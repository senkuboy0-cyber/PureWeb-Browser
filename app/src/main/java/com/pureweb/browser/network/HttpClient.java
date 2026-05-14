package com.pureweb.browser.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.CookieManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Headers;

/**
 * HTTP Client for video detection and downloading
 * Uses OkHttp 5.0.0 for network operations
 */
public class HttpClient {

    private static HttpClient instance;
    private OkHttpClient client;
    private Context context;
    
    // Shared preferences for storing cookies/headers
    private SharedPreferences prefs;
    
    private HttpClient(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("pureweb_http", Context.MODE_PRIVATE);
        
        // Build OkHttp client with timeouts
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build();
    }
    
    public static synchronized HttpClient getInstance(Context context) {
        if (instance == null) {
            instance = new HttpClient(context);
        }
        return instance;
    }
    
    /**
     * Verify if URL is a video by checking Content-Type header
     * @param url URL to verify
     * @return ContentType result
     */
    public ContentType verifyVideoUrl(String url) {
        return verifyVideoUrl(url, null);
    }
    
    /**
     * Verify if URL is a video by checking Content-Type header with custom headers
     */
    public ContentType verifyVideoUrl(String url, Map<String, String> customHeaders) {
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .get();
            
            // Add custom headers
            if (customHeaders != null) {
                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    requestBuilder.addHeader(entry.getKey(), entry.getValue());
                }
            } else {
                // Add default headers
                addDefaultHeaders(requestBuilder, url);
            }
            
            Request request = requestBuilder.build();
            
            try (Response response = client.newCall(request).execute()) {
                String contentType = response.header("Content-Type");
                long contentLength = response.body() != null ? response.body().contentLength() : -1;
                
                return analyzeContentType(contentType, contentLength, url, response);
            }
            
        } catch (IOException e) {
            e.printStackTrace();
            return new ContentType(ContentType.Type.OTHER, -1);
        }
    }
    
    /**
     * Analyze content type and determine if it's a video
     */
    private ContentType analyzeContentType(String contentType, long contentLength, String url, Response response) {
        // Check for video content types
        if (contentType != null) {
            if (contentType.contains("mpegurl") || contentType.contains("x-mpegurl")) {
                return new ContentType(ContentType.Type.M3U8, contentLength);
            }
            if (contentType.contains("dash") || contentType.contains("xml")) {
                return new ContentType(ContentType.Type.MPD, contentLength);
            }
            if (contentType.contains("video")) {
                return new ContentType(ContentType.Type.VIDEO, contentLength);
            }
            if (contentType.contains("audio")) {
                return new ContentType(ContentType.Type.AUDIO, contentLength);
            }
            if (contentType.contains("application/octet-stream")) {
                // Need to check content for M3U8
                return new ContentType(ContentType.Type.OCTET_STREAM, contentLength);
            }
        }
        
        // Check by URL extension
        if (url.contains(".m3u8")) {
            return new ContentType(ContentType.Type.M3U8, contentLength);
        }
        if (url.contains(".mpd")) {
            return new ContentType(ContentType.Type.MPD, contentLength);
        }
        
        return new ContentType(ContentType.Type.OTHER, -1);
    }
    
    /**
     * Fetch content from URL
     */
    public String fetchContent(String url) {
        return fetchContent(url, null);
    }
    
    /**
     * Fetch content from URL with custom headers
     */
    public String fetchContent(String url, Map<String, String> customHeaders) {
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .get();
            
            if (customHeaders != null) {
                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    requestBuilder.addHeader(entry.getKey(), entry.getValue());
                }
            } else {
                addDefaultHeaders(requestBuilder, url);
            }
            
            Request request = requestBuilder.build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.body() != null) {
                    return response.body().string();
                }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Fetch content as bytes
     */
    public byte[] fetchBytes(String url) {
        return fetchBytes(url, null);
    }
    
    /**
     * Fetch content as bytes with custom headers
     */
    public byte[] fetchBytes(String url, Map<String, String> customHeaders) {
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .get();
            
            if (customHeaders != null) {
                for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
                    requestBuilder.addHeader(entry.getKey(), entry.getValue());
                }
            } else {
                addDefaultHeaders(requestBuilder, url);
            }
            
            Request request = requestBuilder.build();
            
            try (Response response = client.newCall(request).execute()) {
                if (response.body() != null) {
                    return response.body().bytes();
                }
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Get headers from response
     */
    public Map<String, String> getResponseHeaders(String url) {
        Map<String, String> headers = new HashMap<>();
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();
            
            try (Response response = client.newCall(request).execute()) {
                Headers responseHeaders = response.headers();
                for (String name : responseHeaders.names()) {
                    headers.put(name, responseHeaders.get(name));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return headers;
    }
    
    /**
     * Add default headers for video requests
     */
    private void addDefaultHeaders(Request.Builder builder, String url) {
        builder.addHeader("User-Agent", 
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36");
        
        // Add cookies from CookieManager
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) {
                builder.addHeader("Cookie", cookies);
            }
        } catch (Exception e) {
            // Ignore cookie errors
        }
        
        // Add Referer for protected content
        try {
            java.net.URL parsedUrl = new java.net.URL(url);
            builder.addHeader("Referer", "https://" + parsedUrl.getHost() + "/");
        } catch (Exception e) {
            // Ignore URL parse errors
        }
    }
    
    /**
     * Get cookies for a URL
     */
    public String getCookies(String url) {
        try {
            String cookies = CookieManager.getInstance().getCookie(url);
            return cookies != null ? cookies : "";
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Content type result holder
     */
    public static class ContentType {
        public enum Type {
            VIDEO,
            AUDIO,
            M3U8,
            MPD,
            OCTET_STREAM,
            OTHER
        }
        
        private Type type;
        private long contentLength;
        
        public ContentType(Type type, long contentLength) {
            this.type = type;
            this.contentLength = contentLength;
        }
        
        public Type getType() {
            return type;
        }
        
        public long getContentLength() {
            return contentLength;
        }
        
        public boolean isVideo() {
            return type == Type.VIDEO;
        }
        
        public boolean isAudio() {
            return type == Type.AUDIO;
        }
        
        public boolean isM3U8() {
            return type == Type.M3U8;
        }
        
        public boolean isMPD() {
            return type == Type.MPD;
        }
        
        public boolean isOctetStream() {
            return type == Type.OCTET_STREAM;
        }
        
        public boolean isOther() {
            return type == Type.OTHER;
        }
    }
}