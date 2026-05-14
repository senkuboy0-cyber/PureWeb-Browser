package com.pureweb.browser.proxy;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.pureweb.browser.R;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

/**
 * Proxy Worker - Background service for running the proxy server
 * Similar to super-video-downloader's ProxyWorker
 */
public class ProxyWorker extends Worker {

    public static final String WORK_NAME = "ProxyWorker";
    public static final int NOTIFICATION_ID = 101;
    public static final String CHANNEL_ID = "ProxyWorkerChannel";

    private static final String KEY_USE_PROXY = "use_proxy";
    private static final String KEY_USE_DNS = "use_dns";

    private NotificationManager notificationManager;
    private PureWebProxyManager proxyManager;
    private SharedPreferences prefs;

    public ProxyWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        proxyManager = PureWebProxyManager.getInstance(context);
        prefs = context.getSharedPreferences("pureweb_proxy", Context.MODE_PRIVATE);
        createNotificationChannel();
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean useProxy = getInputData().getBoolean(KEY_USE_PROXY, true);
        boolean useDns = getInputData().getBoolean(KEY_USE_DNS, false);

        if (!useProxy && !useDns) {
            // Proxy disabled, stop it
            proxyManager.stopProxy();
            return Result.success();
        }

        try {
            // Set as foreground service
            setForegroundAsync(createForegroundInfo());
        } catch (Exception e) {
            // Ignore
        }

        // Start the proxy
        boolean started = proxyManager.startProxy();

        if (started) {
            // Set up traffic listener for video detection
            proxyManager.setTrafficListener(new ProxyTrafficListener());
            
            // Keep running until stopped
            while (proxyManager.isProxyRunning()) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    break;
                }
            }
            
            return Result.success();
        } else {
            return Result.failure();
        }
    }

    @Override
    public void onStopped() {
        super.onStopped();
        proxyManager.stopProxy();
    }

    /**
     * Create notification for foreground service
     */
    private ForegroundInfo createForegroundInfo() {
        Notification notification = createNotification();
        return new ForegroundInfo(NOTIFICATION_ID, notification);
    }

    /**
     * Create notification channel
     */
    private void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Proxy Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Running proxy server for video detection");
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Create notification
     */
    private Notification createNotification() {
        Intent intent = getApplicationContext().getPackageManager()
                .getLaunchIntentForPackage(getApplicationContext().getPackageName());
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(),
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentTitle("PureWeb Proxy")
                .setContentText("Running on port 8888")
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .build();
    }

    /**
     * Traffic listener for video detection
     */
    private class ProxyTrafficListener implements PureWebProxyManager.TrafficListener {
        
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        @Override
        public void onRequest(String method, String url, String headers, byte[] body) {
            // Check for video URLs in requests
            if (isVideoUrl(url)) {
                mainHandler.post(() -> {
                    // Notify video detection manager
                    notifyVideoFound(url, "request");
                });
            }
        }

        @Override
        public void onResponse(String url, int statusCode, String headers, byte[] body) {
            // Check response content for video URLs
            if (body != null && body.length > 0) {
                String contentType = getHeaderValue(headers, "Content-Type");
                
                // Check for video content
                if (contentType != null && (
                        contentType.contains("video") ||
                        contentType.contains("mpeg") ||
                        contentType.contains("dash")
                )) {
                    mainHandler.post(() -> {
                        notifyVideoFound(url, "response");
                    });
                }
                
                // Check body content for M3U8 or MPD URLs
                if (body.length < 1024 * 1024) { // Less than 1MB
                    String bodyStr = new String(body);
                    if (bodyStr.contains(".m3u8") || bodyStr.contains("#EXTM3U")) {
                        mainHandler.post(() -> {
                            extractM3U8Urls(bodyStr, url);
                        });
                    }
                }
            }
        }

        @Override
        public void onError(String error) {
            // Log error
        }

        private boolean isVideoUrl(String url) {
            if (url == null) return false;
            return url.contains(".mp4") || 
                   url.contains(".webm") || 
                   url.contains(".m3u8") ||
                   url.contains(".mpd") ||
                   url.contains(".ts") ||
                   url.contains(".m4s");
        }

        private String getHeaderValue(String headers, String key) {
            if (headers == null || key == null) return null;
            String[] lines = headers.split("\r\n");
            for (String line : lines) {
                if (line.toLowerCase().startsWith(key.toLowerCase() + ":")) {
                    int colonIndex = line.indexOf(":");
                    return line.substring(colonIndex + 1).trim();
                }
            }
            return null;
        }

        private void extractM3U8Urls(String content, String baseUrl) {
            // Extract M3U8 URLs from content
            String[] lines = content.split("\n");
            String base = baseUrl.substring(0, baseUrl.lastIndexOf('/') + 1);
            
            for (String line : lines) {
                line = line.trim();
                if (!line.startsWith("#") && !line.isEmpty()) {
                    String fullUrl;
                    if (line.startsWith("http")) {
                        fullUrl = line;
                    } else {
                        fullUrl = base + line;
                    }
                    notifyVideoFound(fullUrl, "m3u8");
                }
            }
        }

        private void notifyVideoFound(String url, String source) {
            // This will be connected to VideoDetectionManager
            // For now, just log it
            android.util.Log.d("ProxyTraffic", "Video found from " + source + ": " + url);
        }
    }
}