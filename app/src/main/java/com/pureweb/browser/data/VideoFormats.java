package com.pureweb.browser.data;

import java.util.List;
import java.util.Map;

/**
 * Container for video formats list
 */
public class VideoFormats {
    
    private List<VideoFormat> formats;
    
    public VideoFormats() {
        this.formats = new java.util.ArrayList<>();
    }
    
    public VideoFormats(List<VideoFormat> formats) {
        this.formats = formats;
    }
    
    public List<VideoFormat> getFormats() {
        return formats;
    }
    
    public void setFormats(List<VideoFormat> formats) {
        this.formats = formats;
    }
    
    public void addFormat(VideoFormat format) {
        if (this.formats == null) {
            this.formats = new java.util.ArrayList<>();
        }
        this.formats.add(format);
    }
    
    public boolean isEmpty() {
        return formats == null || formats.isEmpty();
    }
    
    public int size() {
        return formats != null ? formats.size() : 0;
    }
}