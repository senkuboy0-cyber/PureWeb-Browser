package com.pureweb.browser.download;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.pureweb.browser.MainActivity;
import com.pureweb.browser.R;
import com.pureweb.browser.data.VideoInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Download Worker - Background video download using WorkManager
 * Similar to the download workers in super-video-downloader
 */
public class DownloadWorker extends Worker {

    public static final String WORK_TAG_PREFIX = "download_";
    public static final String KEY_VIDEO_URL = "video_url";
    public static final String KEY_VIDEO_TITLE = "video_title";
    public static final String KEY_VIDEO_ID = "video_id";
    public static final String KEY_IS_M3U8 = "is_m3u8";
    public static final String KEY_IS_MPD = "is_mpd";
    
    private static final int NOTIFICATION_ID = 2001;
    private static final String CHANNEL_ID = "DownloadChannel";
    
    private NotificationManager notificationManager;
    private AtomicBoolean isCancelled = new AtomicBoolean(false);
    private AtomicInteger progress = new AtomicInteger(0);
    
    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }
    
    @NonNull
    @Override
    public Result doWork() {
        String videoUrl = getInputData().getString(KEY_VIDEO_URL);
        String videoTitle = getInputData().getString(KEY_VIDEO_TITLE);
        String videoId = getInputData().getString(KEY_VIDEO_ID);
        boolean isM3u8 = getInputData().getBoolean(KEY_IS_M3U8, false);
        boolean isMpd = getInputData().getBoolean(KEY_IS_MPD, false);
        
        if (videoUrl == null || videoUrl.isEmpty()) {
            return Result.failure();
        }
        
        if (videoTitle == null || videoTitle.isEmpty()) {
            videoTitle = "Video_" + System.currentTimeMillis();
        }
        
        try {
            // Set as foreground service
            setForegroundAsync(createForegroundInfo(videoTitle, 0));
            
            // Start download
            String filePath = downloadFile(videoUrl, videoTitle, isM3u8);
            
            if (filePath != null && !isCancelled.get()) {
                // Show completion notification
                showCompletionNotification(videoTitle, filePath);
                return Result.success();
            } else {
                return Result.failure();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            showErrorNotification(videoTitle, e.getMessage());
            return Result.failure();
        }
    }
    
    /**
     * Download file from URL
     */
    private String downloadFile(String url, String title, boolean isM3u8) {
        File downloadDir = getDownloadDirectory();
        if (downloadDir == null) {
            downloadDir = getApplicationContext().getExternalFilesDir("Downloads");
        }
        if (downloadDir == null) {
            return null;
        }
        
        String extension = getExtension(url, isM3u8);
        String fileName = sanitizeFileName(title) + "." + extension;
        File outputFile = new File(downloadDir, fileName);
        
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        
        try {
            URL downloadUrl = new URL(url);
            connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(30000);
            
            // Add headers
            connection.setRequestProperty("User-Agent", 
                    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36");
            
            // Add cookies if available
            String cookies = getApplicationContext()
                    .getSharedPreferences("pureweb_cookies", Context.MODE_PRIVATE)
                    .getString(url, null);
            if (cookies != null) {
                connection.setRequestProperty("Cookie", cookies);
            }
            
            connection.connect();
            
            long totalSize = connection.getContentLengthLong();
            long downloaded = 0;
            
            inputStream = connection.getInputStream();
            outputStream = new FileOutputStream(outputFile);
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (isCancelled.get()) {
                    outputFile.delete();
                    return null;
                }
                
                outputStream.write(buffer, 0, bytesRead);
                downloaded += bytesRead;
                
                // Update progress
                if (totalSize > 0) {
                    int progressValue = (int) ((downloaded * 100) / totalSize);
                    progress.set(progressValue);
                    updateProgress(title, progressValue);
                }
            }
            
            outputStream.flush();
            return outputFile.getAbsolutePath();
            
        } catch (Exception e) {
            e.printStackTrace();
            outputFile.delete();
            return null;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
                if (connection != null) connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Get download directory
     */
    private File getDownloadDirectory() {
        File dir = new File(getApplicationContext().getExternalFilesDir(null), "Downloads");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
    
    /**
     * Sanitize file name
     */
    private String sanitizeFileName(String name) {
        if (name == null) return "video";
        return name.replaceAll("[^a-zA-Z0-9._-]", "_").substring(0, Math.min(name.length(), 50));
    }
    
    /**
     * Get file extension from URL
     */
    private String getExtension(String url, boolean isM3u8) {
        if (isM3u8 || url.contains(".m3u8")) return "m3u8";
        if (url.contains(".mpd")) return "mpd";
        if (url.contains(".webm")) return "webm";
        if (url.contains(".mkv")) return "mkv";
        if (url.contains(".avi")) return "avi";
        if (url.contains(".flv")) return "flv";
        if (url.contains(".mov")) return "mov";
        if (url.contains(".mp4")) return "mp4";
        return "mp4";
    }
    
    /**
     * Create notification channel for Android O+
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Video Downloads",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Video download progress notifications");
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    /**
     * Create foreground info for download notification
     */
    private ForegroundInfo createForegroundInfo(String title, int progressValue) {
        Notification notification = createNotification(title, progressValue);
        return new ForegroundInfo(NOTIFICATION_ID, notification);
    }
    
    /**
     * Create download progress notification
     */
    private Notification createNotification(String title, int progressValue) {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Downloading: " + title)
                .setContentText(progressValue + "%")
                .setProgress(100, progressValue, false)
                .setOngoing(true)
                .setContentIntent(pendingIntent);
        
        return builder.build();
    }
    
    /**
     * Update download progress notification
     */
    private void updateProgress(String title, int progressValue) {
        Notification notification = createNotification(title, progressValue);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }
    
    /**
     * Show download completion notification
     */
    private void showCompletionNotification(String title, String filePath) {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("downloaded_file", filePath);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                getApplicationContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download Complete")
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
        
        notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
    }
    
    /**
     * Show error notification
     */
    private void showErrorNotification(String title, String error) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Download Failed")
                .setContentText(title + ": " + error)
                .setAutoCancel(true);
        
        notificationManager.notify(NOTIFICATION_ID + 2, builder.build());
    }
    
    @Override
    public void onStopped() {
        super.onStopped();
        isCancelled.set(true);
    }
}