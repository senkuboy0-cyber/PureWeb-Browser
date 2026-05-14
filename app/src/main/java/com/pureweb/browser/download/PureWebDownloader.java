package com.pureweb.browser.download;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.pureweb.browser.data.VideoInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * PureWeb Downloader - Main video downloader for PureWeb Browser
 * Handles regular video, HLS streams, and DASH streams
 * 
 * Similar to super-video-downloader's custom downloader
 */
public class PureWebDownloader {

    private static PureWebDownloader instance;
    private Context context;
    private Handler mainHandler;
    
    private PureWebDownloader(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }
    
    public static synchronized PureWebDownloader getInstance(Context context) {
        if (instance == null) {
            instance = new PureWebDownloader(context);
        }
        return instance;
    }
    
    /**
     * Start download for a video
     */
    public void download(VideoInfo videoInfo) {
        if (videoInfo == null || videoInfo.getFirstUrl() == null) {
            return;
        }
        
        String workId = videoInfo.getId();
        
        Data inputData = new Data.Builder()
                .putString(DownloadKeys.KEY_VIDEO_ID, workId)
                .putString(DownloadKeys.KEY_VIDEO_URL, videoInfo.getFirstUrl())
                .putString(DownloadKeys.KEY_VIDEO_TITLE, videoInfo.getTitle() != null ? 
                        videoInfo.getTitle() : "Video")
                .putBoolean(DownloadKeys.KEY_IS_M3U8, videoInfo.isM3u8())
                .putBoolean(DownloadKeys.KEY_IS_MPD, videoInfo.isMpd())
                .putBoolean(DownloadKeys.KEY_IS_REGULAR, videoInfo.isRegularDownload())
                .build();
        
        OneTimeWorkRequest downloadRequest = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(inputData)
                .addTag(workId)
                .build();
        
        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        "download_" + workId,
                        ExistingWorkPolicy.REPLACE,
                        downloadRequest
                );
        
        mainHandler.post(() -> 
            Toast.makeText(context, "Download started!", Toast.LENGTH_SHORT).show()
        );
    }
    
    /**
     * Start download from URL
     */
    public void download(String url, String title) {
        VideoInfo videoInfo = new VideoInfo(url);
        videoInfo.setTitle(title != null ? title : "Video");
        download(videoInfo);
    }
    
    /**
     * Cancel download
     */
    public void cancelDownload(String videoId) {
        WorkManager.getInstance(context)
                .cancelUniqueWork("download_" + videoId);
    }
    
    /**
     * Check if download is in progress
     */
    public boolean isDownloading(String videoId) {
        try {
            var workInfos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork("download_" + videoId)
                    .get();
            
            if (workInfos.isEmpty()) return false;
            
            for (var workInfo : workInfos) {
                if (!workInfo.getState().isFinished()) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Download keys for worker
     */
    public static class DownloadKeys {
        public static final String KEY_VIDEO_ID = "video_id";
        public static final String KEY_VIDEO_URL = "video_url";
        public static final String KEY_VIDEO_TITLE = "video_title";
        public static final String KEY_IS_M3U8 = "is_m3u8";
        public static final String KEY_IS_MPD = "is_mpd";
        public static final String KEY_IS_REGULAR = "is_regular";
    }
    
    /**
     * Download Worker - Handles background downloads
     */
    public static class DownloadWorker extends Worker {
        
        private static final int NOTIFICATION_ID = 2001;
        private static final String CHANNEL_ID = "PureWebDownload";
        
        private AtomicBoolean isCancelled = new AtomicBoolean(false);
        
        public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }
        
        @NonNull
        @Override
        public Result doWork() {
            String videoUrl = getInputData().getString(DownloadKeys.KEY_VIDEO_URL);
            String videoTitle = getInputData().getString(DownloadKeys.KEY_VIDEO_TITLE);
            boolean isM3u8 = getInputData().getBoolean(DownloadKeys.KEY_IS_M3U8, false);
            boolean isMpd = getInputData().getBoolean(DownloadKeys.KEY_IS_MPD, false);
            
            if (videoUrl == null || videoUrl.isEmpty()) {
                return Result.failure();
            }
            
            try {
                setForegroundAsync(createForegroundInfo(videoTitle, 0));
                
                String filePath;
                
                if (isM3u8) {
                    filePath = downloadHLS(videoUrl, videoTitle);
                } else if (isMpd) {
                    filePath = downloadMPD(videoUrl, videoTitle);
                } else {
                    filePath = downloadRegular(videoUrl, videoTitle);
                }
                
                if (filePath != null && !isCancelled.get()) {
                    showCompletionNotification(videoTitle, filePath);
                    return Result.success();
                } else {
                    return Result.failure();
                }
                
            } catch (Exception e) {
                showErrorNotification(videoTitle, e.getMessage());
                return Result.failure();
            }
        }
        
        /**
         * Download regular video file
         */
        private String downloadRegular(String url, String title) throws IOException {
            File downloadDir = getDownloadDirectory();
            String extension = getExtension(url);
            String fileName = sanitizeFileName(title) + "." + extension;
            File outputFile = new File(downloadDir, fileName);
            
            HttpURLConnection connection = null;
            InputStream inputStream = null;
            FileOutputStream outputStream = null;
            
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);
                addDefaultHeaders(connection, url);
                
                long totalSize = connection.getContentLengthLong();
                long downloaded = 0;
                
                inputStream = connection.getInputStream();
                outputStream = new FileOutputStream(outputFile);
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    if (isCancelled.get()) {
                        closeQuietly(inputStream);
                        closeQuietly(outputStream);
                        outputFile.delete();
                        return null;
                    }
                    
                    outputStream.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;
                    
                    if (totalSize > 0) {
                        int progress = (int) ((downloaded * 100) / totalSize);
                        updateProgress(title, progress);
                    }
                }
                
                return outputFile.getAbsolutePath();
                
            } finally {
                closeQuietly(inputStream);
                closeQuietly(outputStream);
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        
        /**
         * Download HLS stream (M3U8)
         */
        private String downloadHLS(String m3u8Url, String title) throws IOException {
            File downloadDir = getDownloadDirectory();
            String fileName = sanitizeFileName(title) + ".ts";
            File outputFile = new File(downloadDir, fileName);
            
            // Parse M3U8
            List<String> segments = parseM3U8(m3u8Url);
            
            if (segments.isEmpty()) {
                // Try as single file
                return downloadRegular(m3u8Url, title);
            }
            
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(outputFile);
                int total = segments.size();
                AtomicInteger downloaded = new AtomicInteger(0);
                
                for (int i = 0; i < total; i++) {
                    if (isCancelled.get()) {
                        outputFile.delete();
                        return null;
                    }
                    
                    byte[] data = downloadSegment(segments.get(i));
                    if (data != null) {
                        fos.write(data);
                    }
                    
                    int progress = (int) ((downloaded.incrementAndGet() * 100.0) / total);
                    updateProgress(title, progress);
                }
                
                return outputFile.getAbsolutePath();
                
            } finally {
                closeQuietly(fos);
            }
        }
        
        /**
         * Download MPD (DASH) stream
         */
        private String downloadMPD(String mpdUrl, String title) throws IOException {
            File downloadDir = getDownloadDirectory();
            String fileName = sanitizeFileName(title) + ".mp4";
            File outputFile = new File(downloadDir, fileName);
            
            // For simplicity, just download the MPD manifest
            // Full implementation would parse MPD and download segments
            return downloadRegular(mpdUrl, title + ".mpd");
        }
        
        /**
         * Parse M3U8 manifest
         */
        private List<String> parseM3U8(String url) {
            List<String> segments = new ArrayList<>();
            BufferedReader reader = null;
            
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                addCookies(connection, url);
                
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                
                String baseUrl = url.substring(0, url.lastIndexOf('/') + 1);
                String line;
                boolean isMasterPlaylist = false;
                
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    
                    // Master playlist
                    if (line.startsWith("#EXT-X-STREAM-INF")) {
                        isMasterPlaylist = true;
                        continue;
                    }
                    
                    // Get variant stream from master playlist
                    if (isMasterPlaylist && !line.startsWith("#") && !line.isEmpty()) {
                        String variantUrl = line.startsWith("http") ? 
                                line : baseUrl + line;
                        // Recursive call in try block
                        try {
                            closeQuietly(reader);
                            return parseM3U8(variantUrl);
                        } catch (IOException e) {
                            // Fall through to return current segments
                        }
                    }
                    
                    // Regular segment
                    if (!line.startsWith("#") && !line.isEmpty()) {
                        String segmentUrl = line.startsWith("http") ? 
                                line : baseUrl + line;
                        if (!segments.contains(segmentUrl)) {
                            segments.add(segmentUrl);
                        }
                    }
                }
                
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                closeQuietly(reader);
            }
            
            return segments;
        }
        
        /**
         * Download a single segment
         */
        private byte[] downloadSegment(String url) {
            HttpURLConnection connection = null;
            InputStream is = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                addCookies(connection, url);
                
                is = connection.getInputStream();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                
                while ((len = is.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }
                
                return baos.toByteArray();
                
            } catch (Exception e) {
                return null;
            } finally {
                closeQuietly(is);
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        
        /**
         * Close stream quietly (no exceptions)
         */
        private void closeQuietly(java.io.Closeable closeable) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        
        /**
         * Add default headers
         */
        private void addDefaultHeaders(HttpURLConnection connection, String url) {
            connection.setRequestProperty("User-Agent", 
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36");
            addCookies(connection, url);
        }
        
        /**
         * Add cookies
         */
        private void addCookies(HttpURLConnection connection, String url) {
            try {
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null && !cookies.isEmpty()) {
                    connection.setRequestProperty("Cookie", cookies);
                }
            } catch (Exception e) {
                // Ignore
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
         * Get extension from URL
         */
        private String getExtension(String url) {
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
        
        /**
         * Create foreground info
         */
        private androidx.work.ForegroundInfo createForegroundInfo(String title, int progress) {
            android.app.Notification notification = createNotification(title, progress);
            return new androidx.work.ForegroundInfo(NOTIFICATION_ID, notification);
        }
        
        /**
         * Create notification
         */
        private android.app.Notification createNotification(String title, int progress) {
            createNotificationChannel();
            
            Intent intent = getApplicationContext().getPackageManager()
                    .getLaunchIntentForPackage(getApplicationContext().getPackageName());
            
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                    getApplicationContext(), 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );
            
            return new androidx.core.app.NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle("Downloading: " + title)
                    .setContentText(progress + "%")
                    .setProgress(100, progress, false)
                    .setOngoing(true)
                    .setContentIntent(pendingIntent)
                    .build();
        }
        
        /**
         * Create notification channel
         */
        private void createNotificationChannel() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        CHANNEL_ID, "Downloads", android.app.NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Download progress notifications");
                
                android.app.NotificationManager manager = 
                        (android.app.NotificationManager) getApplicationContext()
                                .getSystemService(android.content.Context.NOTIFICATION_SERVICE);
                manager.createNotificationChannel(channel);
            }
        }
        
        /**
         * Update progress notification
         */
        private void updateProgress(String title, int progress) {
            android.app.NotificationManager manager = 
                    (android.app.NotificationManager) getApplicationContext()
                            .getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID, createNotification(title, progress));
        }
        
        /**
         * Show completion notification
         */
        private void showCompletionNotification(String title, String filePath) {
            android.app.NotificationManager manager = 
                    (android.app.NotificationManager) getApplicationContext()
                            .getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.fromFile(new File(filePath)), "video/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                    getApplicationContext(), 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );
            
            android.app.Notification notification = new androidx.core.app.NotificationCompat.Builder(
                    getApplicationContext(), CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Download Complete")
                    .setContentText(title)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build();
            
            manager.notify(NOTIFICATION_ID + 1, notification);
        }
        
        /**
         * Show error notification
         */
        private void showErrorNotification(String title, String error) {
            android.app.NotificationManager manager = 
                    (android.app.NotificationManager) getApplicationContext()
                            .getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            
            android.app.Notification notification = new androidx.core.app.NotificationCompat.Builder(
                    getApplicationContext(), CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("Download Failed")
                    .setContentText(title + ": " + error)
                    .setAutoCancel(true)
                    .build();
            
            manager.notify(NOTIFICATION_ID + 2, notification);
        }
        
        @Override
        public void onStopped() {
            super.onStopped();
            isCancelled.set(true);
        }
    }
}