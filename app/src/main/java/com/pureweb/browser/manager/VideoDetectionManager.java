package com.pureweb.browser.manager;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Video Detection Manager - Coordinates video detection from websites
 * Combines JS injection + Proxy traffic + URL verification + content parsing
 * 
 * Similar to super-video-downloader's GlobalVideoDetectionModel
 */
public class VideoDetectionManager {

    private static VideoDetectionManager instance;
    private Context context;
    private VideoRepository videoRepository;
    private HttpClient httpClient;
    private ProxyController proxyController;
    private ExecutorService executor;
    private Handler mainHandler;
    
    // Track verified URLs to avoid duplicates
    private final Set<String> verifiedUrls = new HashSet<>();
    private final Map<String, VideoInfo> detectedVideos = new HashMap<>();
    
    // Listeners
    private List<VideoDetectionListener> listeners = new ArrayList<>();
    
    // Settings
    private boolean detectByUrl = true;
    private boolean checkOnAudio = true;
    private boolean useProxyDetection = true;
    
    // Minimum video file size (5MB)
    private static final long MIN_VIDEO_SIZE = 5 * 1024 * 1024;
    
    // Filter regex for non-video files
    private static final String FILTER_REGEX = 
            ".*\\.(apk|html|xml|ico|css|js|png|gif|json|jpg|jpeg|svg|woff|woff2|ttf|otf|cur|webp|bmp|tif|tiff|psd|ai|eps|pdf|doc|docx|xls|xlsx|ppt|pptx|csv|md|rtf|vtt|srt|swf|jar|log|txt|m4s)$";
    
    private VideoDetectionManager(Context context) {
        this.context = context.getApplicationContext();
        this.videoRepository = VideoRepository.getInstance(context);
        this.httpClient = HttpClient.getInstance(context);
        this.proxyController = ProxyController.getInstance(context);
        this.executor = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Connect to proxy for traffic-based detection
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
    
    /**
     * Add detection listener
     */
    public void addListener(VideoDetectionListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    /**
     * Remove detection listener
     */
    public void removeListener(VideoDetectionListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Clear all detected videos
     */
    public void clearDetectedVideos() {
        verifiedUrls.clear();
        detectedVideos.clear();
        notifyVideosCleared();
    }
    
    /**
     * Start proxy-based detection
     */
    public void startProxyDetection() {
        if (useProxyDetection && !proxyController.isProxyRunning()) {
            proxyController.startLocalProxy();
        }
    }
    
    /**
     * Stop proxy-based detection
     */
    public void stopProxyDetection() {
        proxyController.stopProxy();
    }
    
    /**
     * Set detection settings
     */
    public void setDetectByUrl(boolean detect) {
        this.detectByUrl = detect;
    }
    
    public void setCheckOnAudio(boolean check) {
        this.checkOnAudio = check;
    }
    
    public void setUseProxyDetection(boolean use) {
        this.useProxyDetection = use;
    }
    
    /**
     * Process a detected video URL from JS injection
     */
    public void processDetectedUrl(String url, String type, String title) {
        if (url == null || url.isEmpty()) return;
        
        // Skip if already verified
        if (verifiedUrls.contains(url)) return;
        
        // Skip filtered files
        if (url.matches(FILTER_REGEX)) return;
        
        // Skip non-HTTP URLs (except blob)
        if (!url.startsWith("http") && !url.startsWith("blob")) return;
        
        mainHandler.post(() -> notifyVideoDetecting(url));
        
        executor.execute(() -> verifyAndProcessUrl(url, type, title));
    }
    
    /**
     * Process URL detected from proxy traffic
     */
    public void processProxyUrl(String url) {
        if (url == null || url.isEmpty()) return;
        if (verifiedUrls.contains(url)) return;
        if (url.matches(FILTER_REGEX)) return;
        
        mainHandler.post(() -> notifyVideoDetecting(url));
        
        executor.execute(() -> {
            if (detectByUrl) {
                verifyAndProcessUrl(url, null, null);
            }
        });
    }
    
    /**
     * Verify URL and process it
     */
    private void verifyAndProcessUrl(String url, String type, String title) {
        try {
            // Check if M3U8 or MPD
            if (url.contains(".m3u8") || (type != null && type.contains("m3u8"))) {
                processM3U8Video(url, title);
                return;
            }
            
            if (url.contains(".mpd")) {
                processMPDVideo(url, title);
                return;
            }
            
            // Verify with HTTP request
            HttpClient.ContentType contentType = httpClient.verifyVideoUrl(url);
            
            switch (contentType.getType()) {
                case M3U8:
                    processM3U8Video(url, title);
                    break;
                case MPD:
                    processMPDVideo(url, title);
                    break;
                case VIDEO:
                case AUDIO:
                    if (type == null || type.equals("audio")) {
                        if (checkOnAudio) {
                            processRegularVideo(url, contentType, title);
                        }
                    } else {
                        processRegularVideo(url, contentType, title);
                    }
                    break;
                case OCTET_STREAM:
                    // Might be video - check content
                    checkOctetStreamContent(url, contentType, title);
                    break;
                default:
                    // Try as regular video if URL suggests it
                    if (isLikelyVideoUrl(url)) {
                        processRegularVideo(url, contentType, title);
                    }
                    break;
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Check if octet-stream is actually a video
     */
    private void checkOctetStreamContent(String url, HttpClient.ContentType contentType, String title) {
        try {
            byte[] firstBytes = httpClient.fetchBytes(url);
            if (firstBytes != null && firstBytes.length > 0) {
                String content = new String(firstBytes, 0, Math.min(firstBytes.length, 20));
                
                if (content.startsWith("#EXTM3U")) {
                    processM3U8Video(url, title);
                    return;
                }
                if (content.contains("<MPD")) {
                    processMPDVideo(url, title);
                    return;
                }
            }
            
            // Check by content length
            if (contentType.getContentLength() > MIN_VIDEO_SIZE) {
                processRegularVideo(url, contentType, title);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Process regular video URL
     */
    private void processRegularVideo(String url, HttpClient.ContentType contentType, String title) {
        if (verifiedUrls.contains(url)) return;
        
        // Check minimum size
        long size = contentType.getContentLength();
        if (size > 0 && size < MIN_VIDEO_SIZE) {
            return;
        }
        
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
        format.setExt(videoInfo.getExt());
        format.setFileSize(size);
        formats.addFormat(format);
        videoInfo.setFormats(formats);
        
        detectedVideos.put(url, videoInfo);
        
        mainHandler.post(() -> notifyVideoDetected(videoInfo));
    }
    
    /**
     * Process M3U8 (HLS) video
     */
    private void processM3U8Video(String url, String title) {
        if (verifiedUrls.contains(url)) return;
        
        verifiedUrls.add(url);
        
        mainHandler.post(() -> notifyM3U8Detected(url));
        
        // Get full video info from repository
        VideoInfo videoInfo = videoRepository.getVideoInfo(url, true, false, checkOnAudio);
        
        if (videoInfo != null && !videoInfo.getDownloadUrls().isEmpty()) {
            detectedVideos.put(url, videoInfo);
            mainHandler.post(() -> notifyVideoDetected(videoInfo));
        }
    }
    
    /**
     * Process MPD (DASH) video
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
     * Check if URL is likely a video
     */
    private boolean isLikelyVideoUrl(String url) {
        return url.contains(".mp4") || url.contains(".webm") || 
               url.contains(".mkv") || url.contains(".m4v") ||
               url.contains(".mov") || url.contains(".avi") ||
               url.contains(".flv");
    }
    
    /**
     * Extract title from URL
     */
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
        } catch (Exception e) {
            // Ignore
        }
        return "Video";
    }
    
    /**
     * Get extension from URL
     */
    private String getExtensionFromUrl(String url) {
        if (url.contains(".m3u8")) return "m3u8";
        if (url.contains(".mpd")) return "mpd";
        if (url.contains(".webm")) return "webm";
        if (url.contains(".mkv")) return "mkv";
        if (url.contains(".m4v")) return "m4v";
        if (url.contains(".avi")) return "avi";
        if (url.contains(".flv")) return "flv";
        if (url.contains(".mov")) return "mov";
        if (url.contains(".mp4")) return "mp4";
        return "mp4";
    }
    
    /**
     * Get all detected videos
     */
    public List<VideoInfo> getDetectedVideos() {
        return new ArrayList<>(detectedVideos.values());
    }
    
    /**
     * Get detected videos count
     */
    public int getDetectedVideosCount() {
        return detectedVideos.size();
    }
    
    /**
     * Check if proxy is active
     */
    public boolean isProxyActive() {
        return proxyController.isProxyRunning();
    }
    
    /**
     * Notification methods
     */
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
     * Listener interface for video detection events
     */
    public interface VideoDetectionListener {
        void onVideoDetected(VideoInfo videoInfo);
        void onVideoDetecting(String url);
        void onVideosCleared();
        void onM3U8Detected(String url);
        void onMPDDetected(String url);
    }
}