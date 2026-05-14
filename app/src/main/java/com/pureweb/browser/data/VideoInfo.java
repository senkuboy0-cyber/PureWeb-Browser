package com.pureweb.browser.data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Video information model - stores detected video data
 * Similar to the VideoInfo.kt in super-video-downloader
 */
public class VideoInfo {
    
    private String id;
    private List<String> downloadUrls;
    private String title;
    private String ext;
    private String thumbnail;
    private long duration;
    private String originalUrl;
    private VideoFormats formats;
    private boolean isRegularDownload;
    private boolean isLive;
    private boolean isDetectedBySuperX;
    private Map<String, String> httpHeaders;
    
    public VideoInfo() {
        this.id = UUID.randomUUID().toString();
        this.downloadUrls = new java.util.ArrayList<>();
        this.formats = new VideoFormats();
        this.httpHeaders = new java.util.HashMap<>();
    }
    
    public VideoInfo(String url) {
        this();
        this.downloadUrls.add(url);
        this.title = "Video";
        this.ext = getExtensionFromUrl(url);
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public List<String> getDownloadUrls() {
        return downloadUrls;
    }
    
    public void setDownloadUrls(List<String> downloadUrls) {
        this.downloadUrls = downloadUrls;
    }
    
    public void addDownloadUrl(String url) {
        if (!this.downloadUrls.contains(url)) {
            this.downloadUrls.add(url);
        }
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getExt() {
        return ext;
    }
    
    public void setExt(String ext) {
        this.ext = ext;
    }
    
    public String getThumbnail() {
        return thumbnail;
    }
    
    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    public String getOriginalUrl() {
        return originalUrl;
    }
    
    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }
    
    public VideoFormats getFormats() {
        return formats;
    }
    
    public void setFormats(VideoFormats formats) {
        this.formats = formats;
    }
    
    public boolean isRegularDownload() {
        return isRegularDownload;
    }
    
    public void setRegularDownload(boolean regularDownload) {
        isRegularDownload = regularDownload;
    }
    
    public boolean isLive() {
        return isLive;
    }
    
    public void setLive(boolean live) {
        isLive = live;
    }
    
    public boolean isDetectedBySuperX() {
        return isDetectedBySuperX;
    }
    
    public void setDetectedBySuperX(boolean detectedBySuperX) {
        isDetectedBySuperX = detectedBySuperX;
    }
    
    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }
    
    public void setHttpHeaders(Map<String, String> httpHeaders) {
        this.httpHeaders = httpHeaders;
    }
    
    // Helper methods
    public String getFirstUrl() {
        return downloadUrls != null && !downloadUrls.isEmpty() ? downloadUrls.get(0) : "";
    }
    
    public String getFileName() {
        return (title != null ? title : "video") + "." + (ext != null ? ext : "mp4");
    }
    
    public boolean isM3u8() {
        if (formats == null || formats.getFormats() == null) return false;
        for (VideoFormat format : formats.getFormats()) {
            if (format.isM3u8()) return true;
        }
        return getFirstUrl().contains(".m3u8");
    }
    
    public boolean isMpd() {
        if (formats == null || formats.getFormats() == null) return false;
        for (VideoFormat format : formats.getFormats()) {
            if (format.isMpd()) return true;
        }
        return getFirstUrl().contains(".mpd");
    }
    
    public boolean isMaster() {
        return isM3u8() && formats != null && formats.getFormats() != null && formats.getFormats().size() > 1;
    }
    
    private String getExtensionFromUrl(String url) {
        if (url == null) return "mp4";
        if (url.contains(".m3u8")) return "m3u8";
        if (url.contains(".mpd")) return "mpd";
        if (url.contains(".webm")) return "webm";
        if (url.contains(".mkv")) return "mkv";
        if (url.contains(".avi")) return "avi";
        if (url.contains(".flv")) return "flv";
        if (url.contains(".mov")) return "mov";
        return "mp4";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoInfo videoInfo = (VideoInfo) o;
        return id != null && id.equals(videoInfo.id);
    }
    
    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}