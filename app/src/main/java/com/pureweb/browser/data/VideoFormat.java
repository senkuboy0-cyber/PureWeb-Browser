package com.pureweb.browser.data;

import java.util.Map;

/**
 * Individual video format information
 * Similar to VideoFormatEntity in super-video-downloader
 */
public class VideoFormat {
    
    private String formatId;
    private String format;
    private String ext;
    private String url;
    private Map<String, String> httpHeaders;
    private long fileSize;
    private int height;
    private int width;
    private long bitrate;
    private String codec;
    private float fps;
    private int audioChannels;
    private int audioBitrate;
    
    public VideoFormat() {
    }
    
    public VideoFormat(String url) {
        this.url = url;
        this.ext = getExtensionFromUrl(url);
        this.formatId = "0";
        this.format = "Unknown";
    }
    
    public VideoFormat(String url, String ext) {
        this.url = url;
        this.ext = ext;
        this.formatId = "0";
        this.format = "Unknown";
    }
    
    // Getters and Setters
    public String getFormatId() {
        return formatId;
    }
    
    public void setFormatId(String formatId) {
        this.formatId = formatId;
    }
    
    public String getFormat() {
        return format;
    }
    
    public void setFormat(String format) {
        this.format = format;
    }
    
    public String getExt() {
        return ext;
    }
    
    public void setExt(String ext) {
        this.ext = ext;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public Map<String, String> getHttpHeaders() {
        return httpHeaders;
    }
    
    public void setHttpHeaders(Map<String, String> httpHeaders) {
        this.httpHeaders = httpHeaders;
    }
    
    public long getFileSize() {
        return fileSize;
    }
    
    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }
    
    public int getHeight() {
        return height;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }
    
    public int getWidth() {
        return width;
    }
    
    public void setWidth(int width) {
        this.width = width;
    }
    
    public long getBitrate() {
        return bitrate;
    }
    
    public void setBitrate(long bitrate) {
        this.bitrate = bitrate;
    }
    
    public String getCodec() {
        return codec;
    }
    
    public void setCodec(String codec) {
        this.codec = codec;
    }
    
    public float getFps() {
        return fps;
    }
    
    public void setFps(float fps) {
        this.fps = fps;
    }
    
    public int getAudioChannels() {
        return audioChannels;
    }
    
    public void setAudioChannels(int audioChannels) {
        this.audioChannels = audioChannels;
    }
    
    public int getAudioBitrate() {
        return audioBitrate;
    }
    
    public void setAudioBitrate(int audioBitrate) {
        this.audioBitrate = audioBitrate;
    }
    
    // Helper methods
    public boolean isM3u8() {
        return url != null && (url.contains(".m3u8") || url.startsWith("#EXTM3U"));
    }
    
    public boolean isMpd() {
        return url != null && url.contains(".mpd");
    }
    
    public String getResolution() {
        if (height > 0) {
            if (height >= 2160) return "4K";
            if (height >= 1440) return "1440p";
            if (height >= 1080) return "1080p";
            if (height >= 720) return "720p";
            if (height >= 480) return "480p";
            if (height >= 360) return "360p";
            if (height >= 240) return "240p";
            return height + "p";
        }
        return format;
    }
    
    public String getQualityLabel() {
        String quality = getResolution();
        if (bitrate > 0) {
            return quality + " (" + (bitrate / 1000) + " kbps)";
        }
        return quality;
    }
    
    public String getFileSizeFormatted() {
        if (fileSize <= 0) return "Unknown";
        
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        if (fileSize < 1024 * 1024 * 1024) return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        return String.format("%.2f GB", fileSize / (1024.0 * 1024 * 1024));
    }
    
    private String getExtensionFromUrl(String url) {
        if (url == null) return "mp4";
        if (url.contains(".m3u8")) return "m3u8";
        if (url.contains(".mpd")) return "mpd";
        if (url.contains(".webm")) return "webm";
        if (url.contains(".mkv")) return "mkv";
        if (url.contains(".ts")) return "ts";
        if (url.contains(".mp4")) return "mp4";
        return "mp4";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoFormat that = (VideoFormat) o;
        return url != null && url.equals(that.url);
    }
    
    @Override
    public int hashCode() {
        return url != null ? url.hashCode() : 0;
    }
}