package com.pureweb.browser.repository;

import android.content.Context;

import com.pureweb.browser.data.VideoFormat;
import com.pureweb.browser.data.VideoFormats;
import com.pureweb.browser.data.VideoInfo;
import com.pureweb.browser.network.HttpClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Video Repository - Fetches video information from URLs
 * Similar to VideoRepository.kt in super-video-downloader
 */
public class VideoRepository {

    private static VideoRepository instance;
    private HttpClient httpClient;
    private Context context;
    
    // Minimum file size threshold (5MB)
    private static final long MIN_VIDEO_SIZE = 5 * 1024 * 1024;
    
    private VideoRepository(Context context) {
        this.context = context.getApplicationContext();
        this.httpClient = HttpClient.getInstance(context);
    }
    
    public static synchronized VideoRepository getInstance(Context context) {
        if (instance == null) {
            instance = new VideoRepository(context);
        }
        return instance;
    }
    
    /**
     * Get video info from a URL
     * @param url Video URL
     * @param isM3u8 Whether URL is M3U8 manifest
     * @param isMpd Whether URL is MPD manifest
     * @param checkAudio Whether to check for audio content
     * @return VideoInfo or null
     */
    public VideoInfo getVideoInfo(String url, boolean isM3u8, boolean isMpd, boolean checkAudio) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        try {
            // Verify the URL first
            HttpClient.ContentType contentType = httpClient.verifyVideoUrl(url);
            
            if (isM3u8 || contentType.isM3U8()) {
                return parseM3U8Manifest(url);
            }
            
            if (isMpd || contentType.isMPD()) {
                return parseMPDManifest(url);
            }
            
            if (contentType.isVideo() || contentType.isAudio()) {
                return createRegularVideoInfo(url, contentType);
            }
            
            if (contentType.isOctetStream()) {
                // Try to check first bytes
                String content = httpClient.fetchContent(url);
                if (content != null && content.startsWith("#EXTM3U")) {
                    return parseM3U8Manifest(url);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Get video info by URL only (auto-detect type)
     */
    public VideoInfo getVideoInfo(String url, boolean checkAudio) {
        return getVideoInfo(url, false, false, checkAudio);
    }
    
    /**
     * Parse M3U8 manifest and extract video URLs
     */
    private VideoInfo parseM3U8Manifest(String url) {
        try {
            String manifest = httpClient.fetchContent(url);
            if (manifest == null || manifest.isEmpty()) {
                return null;
            }
            
            VideoInfo videoInfo = new VideoInfo();
            videoInfo.setOriginalUrl(url);
            videoInfo.setTitle(extractTitleFromUrl(url));
            videoInfo.setExt("m3u8");
            videoInfo.setM3U8(true);
            
            VideoFormats formats = new VideoFormats();
            List<String> urls = new ArrayList<>();
            
            String baseUrl = getBaseUrl(url);
            String[] lines = manifest.split("\n");
            
            boolean isMasterPlaylist = false;
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                
                // Check if master playlist
                if (line.contains("#EXT-X-STREAM-INF")) {
                    isMasterPlaylist = true;
                }
                
                // Skip comments and directives
                if (line.startsWith("#")) {
                    continue;
                }
                
                if (!line.isEmpty()) {
                    String segmentUrl;
                    
                    if (line.startsWith("http")) {
                        segmentUrl = line;
                    } else {
                        // Resolve relative URL
                        segmentUrl = resolveUrl(baseUrl, line);
                    }
                    
                    if (!urls.contains(segmentUrl)) {
                        urls.add(segmentUrl);
                        
                        VideoFormat format = new VideoFormat(segmentUrl);
                        format.setExt("ts");
                        format.setFormatId(String.valueOf(formats.size()));
                        format.setFormat(isMasterPlaylist ? "720p" : "segment");
                        
                        formats.addFormat(format);
                    }
                }
            }
            
            if (urls.isEmpty()) {
                // If no segments found, it might be a variant playlist
                // Try to find variant streams
                for (int i = 0; i < lines.length - 1; i++) {
                    String line = lines[i].trim();
                    if (line.startsWith("#EXT-X-STREAM-INF")) {
                        String nextLine = lines[i + 1].trim();
                        if (!nextLine.startsWith("#") && !nextLine.isEmpty()) {
                            String variantUrl = nextLine.startsWith("http") ? 
                                    nextLine : resolveUrl(baseUrl, nextLine);
                            
                            urls.add(variantUrl);
                            
                            VideoFormat format = new VideoFormat(variantUrl);
                            format.setExt("m3u8");
                            format.setFormatId(String.valueOf(formats.size()));
                            
                            // Try to extract resolution from stream info
                            String resolution = extractResolution(line);
                            format.setFormat(resolution);
                            
                            formats.addFormat(format);
                        }
                    }
                }
            }
            
            videoInfo.setDownloadUrls(urls);
            videoInfo.setFormats(formats);
            videoInfo.setRegularDownload(urls.size() == 1);
            
            return videoInfo;
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Parse MPD (DASH) manifest
     */
    private VideoInfo parseMPDManifest(String url) {
        try {
            String manifest = httpClient.fetchContent(url);
            if (manifest == null || manifest.isEmpty()) {
                return null;
            }
            
            VideoInfo videoInfo = new VideoInfo();
            videoInfo.setOriginalUrl(url);
            videoInfo.setTitle(extractTitleFromUrl(url));
            videoInfo.setExt("mpd");
            videoInfo.setMpd(true);
            
            VideoFormats formats = new VideoFormats();
            List<String> urls = new ArrayList<>();
            
            // Simple regex to find segment URLs in MPD
            // In production, use proper XML parsing
            Pattern segmentPattern = Pattern.compile("BaseURL>([^<]+)<");
            Matcher matcher = segmentPattern.matcher(manifest);
            
            while (matcher.find()) {
                String segmentUrl = matcher.group(1);
                if (!urls.contains(segmentUrl)) {
                    urls.add(segmentUrl);
                    
                    VideoFormat format = new VideoFormat(segmentUrl);
                    format.setExt("m4s");
                    format.setFormatId(String.valueOf(formats.size()));
                    formats.addFormat(format);
                }
            }
            
            videoInfo.setDownloadUrls(urls);
            videoInfo.setFormats(formats);
            videoInfo.setRegularDownload(false);
            
            return videoInfo;
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Create VideoInfo for regular video URL
     */
    private VideoInfo createRegularVideoInfo(String url, HttpClient.ContentType contentType) {
        VideoInfo videoInfo = new VideoInfo();
        videoInfo.setOriginalUrl(url);
        videoInfo.setTitle(extractTitleFromUrl(url));
        videoInfo.setDownloadUrls(new ArrayList<>());
        videoInfo.getDownloadUrls().add(url);
        videoInfo.setRegularDownload(true);
        
        // Set extension based on URL
        String ext = getExtensionFromUrl(url);
        videoInfo.setExt(ext);
        
        VideoFormats formats = new VideoFormats();
        VideoFormat format = new VideoFormat(url);
        format.setFormatId("0");
        format.setFormat("default");
        format.setExt(ext);
        format.setFileSize(contentType.getContentLength());
        formats.addFormat(format);
        
        videoInfo.setFormats(formats);
        
        return videoInfo;
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
     * Get base URL for resolving relative paths
     */
    private String getBaseUrl(String url) {
        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            return parsedUrl.getProtocol() + "://" + parsedUrl.getHost() + 
                   (parsedUrl.getPort() != -1 ? ":" + parsedUrl.getPort() : "") +
                   parsedUrl.getPath().substring(0, parsedUrl.getPath().lastIndexOf('/') + 1);
        } catch (Exception e) {
            return url;
        }
    }
    
    /**
     * Resolve relative URL to absolute
     */
    private String resolveUrl(String baseUrl, String relativeUrl) {
        try {
            if (relativeUrl.startsWith("/")) {
                // Absolute path
                java.net.URL parsed = new java.net.URL(baseUrl);
                return parsed.getProtocol() + "://" + parsed.getHost() + relativeUrl;
            } else {
                // Relative path
                return new java.net.URL(new java.net.URL(baseUrl), relativeUrl).toString();
            }
        } catch (Exception e) {
            return relativeUrl;
        }
    }
    
    /**
     * Extract resolution from M3U8 stream info
     */
    private String extractResolution(String streamInfo) {
        try {
            Pattern pattern = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)");
            Matcher matcher = pattern.matcher(streamInfo);
            if (matcher.find()) {
                int height = Integer.parseInt(matcher.group(2));
                if (height >= 2160) return "4K";
                if (height >= 1440) return "1440p";
                if (height >= 1080) return "1080p";
                if (height >= 720) return "720p";
                if (height >= 480) return "480p";
                if (height >= 360) return "360p";
                return height + "p";
            }
        } catch (Exception e) {
            // Ignore
        }
        return "Unknown";
    }
    
    /**
     * Get extension from URL
     */
    private String getExtensionFromUrl(String url) {
        if (url.contains(".m3u8")) return "m3u8";
        if (url.contains(".mpd")) return "mpd";
        if (url.contains(".webm")) return "webm";
        if (url.contains(".mkv")) return "mkv";
        if (url.contains(".avi")) return "avi";
        if (url.contains(".flv")) return "flv";
        if (url.contains(".mov")) return "mov";
        if (url.contains(".mp4")) return "mp4";
        return "mp4";
    }
    
    // Helper methods for M3U8/MPD
    public boolean isM3U8(String url) {
        return url != null && (url.contains(".m3u8") || url.contains("#EXTM3U"));
    }
    
    public boolean isMPD(String url) {
        return url != null && url.contains(".mpd");
    }
}