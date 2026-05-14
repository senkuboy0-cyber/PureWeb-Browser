package com.pureweb.browser.proxy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.pureweb.browser.data.VideoInfo;
import com.pureweb.browser.network.HttpClient;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Proxy Controller - Controls proxy behavior and video detection
 * Similar to super-video-downloader's CustomProxyController
 */
public class ProxyController {

    private static ProxyController instance;
    private Context context;
    private SharedPreferences prefs;
    
    private PureWebProxyManager proxyManager;
    private HttpClient httpClient;
    private ExecutorService executor;
    private Handler mainHandler;
    
    // Current running proxy info
    private String currentProxyHost;
    private int currentProxyPort;
    private String proxyUsername;
    private String proxyPassword;
    
    // Listeners
    private VideoDetectionFromProxyListener listener;
    
    private ProxyController(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences("pureweb_proxy", Context.MODE_PRIVATE);
        this.proxyManager = PureWebProxyManager.getInstance(context);
        this.httpClient = HttpClient.getInstance(context);
        this.executor = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public static synchronized ProxyController getInstance(Context context) {
        if (instance == null) {
            instance = new ProxyController(context);
        }
        return instance;
    }
    
    /**
     * Start proxy with local credentials
     */
    public boolean startLocalProxy() {
        String user = prefs.getString("local_user", "pureweb");
        String pass = prefs.getString("local_pass", "proxy123");
        int port = prefs.getInt("proxy_port", 8888);
        
        return proxyManager.startProxy(port, user, pass);
    }
    
    /**
     * Stop proxy
     */
    public void stopProxy() {
        proxyManager.stopProxy();
    }
    
    /**
     * Check if proxy is running
     */
    public boolean isProxyRunning() {
        return proxyManager.isProxyRunning();
    }
    
    /**
     * Get proxy address
     */
    public String getProxyAddress() {
        return "127.0.0.1:" + proxyManager.getProxyPort();
    }
    
    /**
     * Set video detection listener
     */
    public void setVideoDetectionListener(VideoDetectionFromProxyListener listener) {
        this.listener = listener;
    }
    
    /**
     * Handle detected video URL from proxy
     */
    public void handleVideoUrl(String url, String source) {
        if (url == null || url.isEmpty()) return;
        
        executor.execute(() -> {
            try {
                // Verify the URL
                HttpClient.ContentType contentType = httpClient.verifyVideoUrl(url);
                
                if (contentType.isVideo() || contentType.isM3U8() || contentType.isMPD()) {
                    mainHandler.post(() -> {
                        if (listener != null) {
                            VideoInfo videoInfo = new VideoInfo(url);
                            videoInfo.setTitle(extractTitleFromUrl(url));
                            
                            if (contentType.isM3U8()) {
                                videoInfo.setM3U8(true);
                            } else if (contentType.isMPD()) {
                                videoInfo.setMpd(true);
                            }
                            
                            listener.onVideoDetectedFromProxy(videoInfo);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    
    /**
     * Handle M3U8 manifest from proxy
     */
    public void handleM3U8Manifest(String manifestUrl) {
        executor.execute(() -> {
            try {
                String content = httpClient.fetchContent(manifestUrl);
                if (content != null && content.contains("#EXTM3U")) {
                    mainHandler.post(() -> {
                        if (listener != null) {
                            VideoInfo videoInfo = new VideoInfo(manifestUrl);
                            videoInfo.setTitle("HLS Stream");
                            videoInfo.setM3U8(true);
                            videoInfo.setExt("m3u8");
                            listener.onVideoDetectedFromProxy(videoInfo);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
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
     * Save proxy credentials
     */
    public void saveCredentials(String user, String pass) {
        prefs.edit()
                .putString("local_user", user)
                .putString("local_pass", pass)
                .apply();
    }
    
    /**
     * Get proxy credentials
     */
    public String[] getCredentials() {
        return proxyManager.getCredentials();
    }
    
    /**
     * Check if proxy is enabled
     */
    public boolean isProxyEnabled() {
        return prefs.getBoolean("proxy_enabled", false);
    }
    
    /**
     * Set proxy enabled state
     */
    public void setProxyEnabled(boolean enabled) {
        prefs.edit().putBoolean("proxy_enabled", enabled).apply();
    }
    
    /**
     * Listener interface for video detection from proxy
     */
    public interface VideoDetectionFromProxyListener {
        void onVideoDetectedFromProxy(VideoInfo videoInfo);
    }
}