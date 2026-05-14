package com.pureweb.browser.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.pureweb.browser.data.VideoFormat;
import com.pureweb.browser.data.VideoFormats;
import com.pureweb.browser.data.VideoInfo;
import com.pureweb.browser.network.HttpClient;
import com.pureweb.browser.proxy.ProxyController;
import com.pureweb.browser.repository.VideoRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Video Detection Manager - Coordinated video detection from websites
 * Groups HLS segments into single video entries
 */
public class VideoDetectionManager {

    private static final String TAG = "VideoDetection";
    private static VideoDetectionManager instance;
    private Context context;
    private VideoRepository videoRepository;
    private HttpClient httpClient;
    private ProxyController proxyController;
    private ExecutorService executor;
    private Handler mainHandler;
    
    // Track verified URLs to avoid duplicates
    private final Set<String> verifiedUrls = new HashSet<>();
    private final Map<String, VideoInfo> detectedVideos = new ConcurrentHashMap<>();
    
    // HLS Stream Groups - Group segments by their M3U8 manifest
    private final Map<String, HlsStreamGroup> hlsStreamGroups = new ConcurrentHashMap<>();
    
    // Listeners
    private List<VideoDetectionListener> listeners = new ArrayList<>();
    
    // Settings
    private boolean detectByUrl = true;
    private boolean checkOnAudio = true;
    private boolean useProxyDetection = true;
    
    // Minimum video file size (100KB for segments)
    private static final long MIN_SEGMENT_SIZE = 100 * 1024;
    
    // Maximum segments to group (prevent too many)
    private static final int MAX_SEGMENTS_PER_GROUP = 500;
    
    private VideoDetectionManager(Context context) {
        this.context = context.getApplicationContext();
        this.videoRepository = VideoRepository.getInstance(context);
        this.httpClient = HttpClient.getInstance(context);
        this.proxyController = ProxyController.getInstance(context);
        this.executor = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        proxyController.setVideoDetectionListener(new ProxyController.VideoDetectionFromProxyListener() {
            @Override
            public void onVideoDetectedFromProxy(VideoInfo videoInfo) {
                mainHandler.post(() -> {
                    if (!verifiedUrls.contains(videoInfo.getFirstUrl())) {
                        verifiedUrls.add(videoInfo.getFirstUrl());
                        detectedVideos.put(videoInfo.getFirstUrl(), videoInfo);
                        notifyVideoDetected(videoInfo);
                    }
                });
            }
        });
    }
    
    public static synchronized VideoDetectionManager getInstance(Context context) {
        if (instance == null) {
            instance = new VideoDetectionManager(context);
        }
        return instance;
    }
    
    public void addListener(VideoDetectionListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public void removeListener(VideoDetectionListener listener) {
        listeners.remove(listener);
    }
    
    public void clearDetectedVideos() {
        verifiedUrls.clear();
        detectedVideos.clear();
        hlsStreamGroups.clear();
        notifyVideosCleared();
    }
    
    public void startProxyDetection() {
        if (useProxyDetection && !proxyController.isProxyRunning()) {
            proxyController.startLocalProxy();
        }
    }
    
    public void stopProxyDetection() {
        proxyController.stopProxy();
    }
    
    /**
     * Process a detected video URL
     */
    public void processDetectedUrl(String url, String type, String title) {
        if (url == null || url.isEmpty()) return;
        
        // Skip if already verified
        if (verifiedUrls.contains(url)) return;
        
        mainHandler.post(() -> notifyVideoDetecting(url));
        
        executor.execute(() -> {
            // Check if it's a segment file
            if (isSegmentUrl(url)) {
                // Group segments together
                processSegmentUrl(url, type, title);
            } else if (isM3U8Url(url)) {
                // Direct M3U8 URL
                processM3U8Video(url, title);
            } else if (isMpdUrl(url)) {
                processMPDVideo(url, title);
            } else {
                // Regular video
                verifyAndProcessUrl(url, type, title);
            }
        });
    }
    
    /**
     * Check if URL is a segment file (.ts, .m4s, etc.)
     */
    private boolean isSegmentUrl(String url) {
        return url.contains(".ts") || 
               url.contains(".m4s") || 
               url.contains("/seg-") ||
               url.contains("/segment") ||
               url.contains("_seg_") ||
               url.contains("-seg-");
    }
    
    /**
     * Check if URL is M3U8 manifest
     */
    private boolean isM3U8Url(String url) {
        return url.contains(".m3u8") || url.contains("#EXTM3U");
    }
    
    /**
     * Check if URL is MPD manifest
     */
    private boolean isMpdUrl(String url) {
        return url.contains(".mpd");
    }
    
    /**
     * Process segment URL - Group segments by base URL
     */
    private void processSegmentUrl(String url, String type, String title) {
        try {
            // Generate group ID based on URL pattern
            String groupId = generateGroupId(url);
            
            HlsStreamGroup group = hlsStreamGroups.get(groupId);
            if (group == null) {
                group = new HlsStreamGroup(groupId, title != null ? title : "HLS Stream");
                hlsStreamGroups.put(groupId, group);
            }
            
            // Add segment to group
            if (group.addSegment(url)) {
                verifiedUrls.add(url);
                
                // Check if we have enough segments
                if (group.getSegmentCount() >= 3) {
                    // Create video entry from group
                    VideoInfo videoInfo = group.createVideoInfo();
                    if (videoInfo != null && !detectedVideos.containsKey(groupId)) {
                        detectedVideos.put(groupId, videoInfo);
                        mainHandler.post(() -> notifyVideoDetected(videoInfo));
                        
                        // Also try to find M3U8 manifest
                        String m3u8Url = findM3U8FromSegments(url);
                        if (m3u8Url != null && !verifiedUrls.contains(m3u8Url)) {
                            mainHandler.post(() -> notifyM3U8Detected(m3u8Url));
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing segment: " + e.getMessage());
        }
    }
    
    /**
     * Generate group ID from segment URL
     */
    private String generateGroupId(String url) {
        // Try to find common base URL
        try {
            // Remove segment number and get base
            String base = url;
            
            // Pattern: .../segment_0.ts or .../seg-0.ts or .../0.ts
            String[] patterns = {
                "/segment_", "/seg-", "/_seg_", 
                "/dash/", "/hls/", "/v1/", "/v2/"
            };
            
            for (String pattern : patterns) {
                int idx = url.lastIndexOf(pattern);
                if (idx > 0) {
                    base = url.substring(0, idx);
                    break;
                }
            }
            
            // Try to extract stream ID
            if (base.contains("?")) {
                base = base.substring(0, base.indexOf("?"));
            }
            
            return base;
        } catch (Exception e) {
            return url;
        }
    }
    
    /**
     * Try to find M3U8 manifest URL from segment URL
     */
    private String findM3U8FromSegments(String segmentUrl) {
        try {
            // Common patterns for M3U8 URL
            String[] possibleM3U8Patterns = {
                segmentUrl.replaceAll("/[^/]+\\.ts", "/master.m3u8"),
                segmentUrl.replaceAll("/[^/]+\\.ts", "/playlist.m3u8"),
                segmentUrl.replaceAll("/[^/]+\\.ts", "/index.m3u8"),
                segmentUrl.replaceAll("/[^/]+\\.ts", ".m3u8")
            };
            
            for (String m3u8Url : possibleM3U8Patterns) {
                HttpClient.ContentType contentType = httpClient.verifyVideoUrl(m3u8Url);
                if (contentType.isM3U8()) {
                    return m3u8Url;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error finding M3U8: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Process M3U8 manifest
     */
    private void processM3U8Video(String url, String title) {
        if (verifiedUrls.contains(url)) return;
        verifiedUrls.add(url);
        
        mainHandler.post(() -> notifyM3U8Detected(url));
        
        VideoInfo videoInfo = videoRepository.getVideoInfo(url, true, false, checkOnAudio);
        
        if (videoInfo != null && !videoInfo.getDownloadUrls().isEmpty()) {
            detectedVideos.put(url, videoInfo);
            mainHandler.post(() -> notifyVideoDetected(videoInfo));
        }
    }
    
    /**
     * Process MPD manifest
     */
    private void processMPDVideo(String url, String title) {
        if (verifiedUrls.contains(url)) return;
        verifiedUrls.add(url);
        
        mainHandler.post(() -> notifyMPDDetected(url));
        
        VideoInfo videoInfo = videoRepository.getVideoInfo(url, false, true, checkOnAudio);
        
        if (videoInfo != null && !videoInfo.getDownloadUrls().isEmpty()) {
            detectedVideos.put(url, videoInfo);
            mainHandler.post(() -> notifyVideoDetected(videoInfo));
        }
    }
    
    /**
     * Verify and process regular video URL
     */
    private void verifyAndProcessUrl(String url, String type, String title) {
        try {
            HttpClient.ContentType contentType = httpClient.verifyVideoUrl(url);
            
            if (contentType.isVideo() || contentType.isM3U8() || contentType.isMPD()) {
                if (!verifiedUrls.contains(url)) {
                    verifiedUrls.add(url);
                    
                    VideoInfo videoInfo = new VideoInfo();
                    videoInfo.setId(url);
                    videoInfo.setTitle(title != null ? title : extractTitleFromUrl(url));
                    videoInfo.setOriginalUrl(url);
                    videoInfo.setDownloadUrls(new ArrayList<>());
                    videoInfo.getDownloadUrls().add(url);
                    videoInfo.setExt(getExtensionFromUrl(url));
                    videoInfo.setRegularDownload(true);
                    
                    VideoFormats formats = new VideoFormats();
                    VideoFormat format = new VideoFormat(url);
                    format.setFormatId("0");
                    format.setFormat("default");
                    format.setFileSize(contentType.getContentLength());
                    formats.addFormat(format);
                    videoInfo.setFormats(formats);
                    
                    detectedVideos.put(url, videoInfo);
                    mainHandler.post(() -> notifyVideoDetected(videoInfo));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error verifying URL: " + e.getMessage());
        }
    }
    
    private String extractTitleFromUrl(String url) {
        try {
            String path = new java.net.URL(url).getPath();
            if (path != null && !path.isEmpty() && !path.equals("/")) {
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                if (fileName.contains(".")) {
                    return fileName.substring(0, fileName.lastIndexOf('.'));
                }
                return fileName;
            }
        } catch (Exception ignored) {}
        return "Video";
    }
    
    private String getExtensionFromUrl(String url) {
        if (url.contains(".webm")) return "webm";
        if (url.contains(".mkv")) return "mkv";
        if (url.contains(".avi")) return "avi";
        if (url.contains(".flv")) return "flv";
        if (url.contains(".mov")) return "mov";
        if (url.contains(".mp4")) return "mp4";
        return "mp4";
    }
    
    public List<VideoInfo> getDetectedVideos() {
        List<VideoInfo> videos = new ArrayList<>(detectedVideos.values());
        // Sort by type (M3U8/MPD first, then segments)
        videos.sort((a, b) -> {
            if (a.isM3u8() || a.isMpd()) return -1;
            if (b.isM3u8() || b.isMpd()) return 1;
            return 0;
        });
        return videos;
    }
    
    public int getDetectedVideosCount() {
        return detectedVideos.size();
    }
    
    public boolean isProxyActive() {
        return proxyController.isProxyRunning();
    }
    
    private void notifyVideoDetected(VideoInfo videoInfo) {
        for (VideoDetectionListener listener : listeners) {
            listener.onVideoDetected(videoInfo);
        }
    }
    
    private void notifyVideoDetecting(String url) {
        for (VideoDetectionListener listener : listeners) {
            listener.onVideoDetecting(url);
        }
    }
    
    private void notifyVideosCleared() {
        for (VideoDetectionListener listener : listeners) {
            listener.onVideosCleared();
        }
    }
    
    private void notifyM3U8Detected(String url) {
        for (VideoDetectionListener listener : listeners) {
            listener.onM3U8Detected(url);
        }
    }
    
    private void notifyMPDDetected(String url) {
        for (VideoDetectionListener listener : listeners) {
            listener.onMPDDetected(url);
        }
    }
    
    /**
     * HLS Stream Group - Groups segments together
     */
    private class HlsStreamGroup {
        private String groupId;
        private String title;
        private List<String> segments = new ArrayList<>();
        private long creationTime;
        
        HlsStreamGroup(String groupId, String title) {
            this.groupId = groupId;
            this.title = title;
            this.creationTime = System.currentTimeMillis();
        }
        
        boolean addSegment(String segmentUrl) {
            if (!segments.contains(segmentUrl) && segments.size() < MAX_SEGMENTS_PER_GROUP) {
                segments.add(segmentUrl);
                return true;
            }
            return false;
        }
        
        int getSegmentCount() {
            return segments.size();
        }
        
        List<String> getSegments() {
            return new ArrayList<>(segments);
        }
        
        VideoInfo createVideoInfo() {
            if (segments.isEmpty()) return null;
            
            VideoInfo videoInfo = new VideoInfo();
            videoInfo.setId(groupId);
            videoInfo.setTitle(title);
            videoInfo.setOriginalUrl(groupId);
            videoInfo.setDownloadUrls(new ArrayList<>(segments));
            videoInfo.setExt("ts");
            videoInfo.setRegularDownload(false);
            
            VideoFormats formats = new VideoFormats();
            for (int i = 0; i < segments.size(); i++) {
                VideoFormat format = new VideoFormat(segments.get(i));
                format.setFormatId(String.valueOf(i));
                format.setFormat("segment_" + i);
                formats.addFormat(format);
            }
            videoInfo.setFormats(formats);
            
            return videoInfo;
        }
    }
    
    public interface VideoDetectionListener {
        void onVideoDetected(VideoInfo videoInfo);
        void onVideoDetecting(String url);
        void onVideosCleared();
        void onM3U8Detected(String url);
        void onMPDDetected(String url);
    }
}