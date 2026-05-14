package com.pureweb.browser.download;

import android.content.Context;

import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.pureweb.browser.data.VideoInfo;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Generic Downloader - Base class for video downloads
 * Similar to GenericDownloader.kt in super-video-downloader
 */
public class GenericDownloader {

    private static GenericDownloader instance;
    private Context context;
    
    private GenericDownloader(Context context) {
        this.context = context.getApplicationContext();
    }
    
    public static synchronized GenericDownloader getInstance(Context context) {
        if (instance == null) {
            instance = new GenericDownloader(context);
        }
        return instance;
    }
    
    /**
     * Start download for a video
     * @param videoInfo Video information
     */
    public void startDownload(VideoInfo videoInfo) {
        if (videoInfo == null || videoInfo.getFirstUrl() == null) {
            return;
        }
        
        String workId = videoInfo.getId() != null ? videoInfo.getId() : UUID.randomUUID().toString();
        
        Data inputData = new Data.Builder()
                .putString(DownloadWorker.KEY_VIDEO_ID, workId)
                .putString(DownloadWorker.KEY_VIDEO_URL, videoInfo.getFirstUrl())
                .putString(DownloadWorker.KEY_VIDEO_TITLE, videoInfo.getTitle() != null ? 
                        videoInfo.getTitle() : "Video")
                .putBoolean(DownloadWorker.KEY_IS_M3U8, videoInfo.isM3u8())
                .putBoolean(DownloadWorker.KEY_IS_MPD, videoInfo.isMpd())
                .build();
        
        OneTimeWorkRequest downloadRequest = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(inputData)
                .addTag(workId)
                .build();
        
        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        DownloadWorker.WORK_TAG_PREFIX + workId,
                        ExistingWorkPolicy.REPLACE,
                        downloadRequest
                );
    }
    
    /**
     * Start download from URL only
     * @param url Video URL
     * @param title Video title
     */
    public void startDownload(String url, String title) {
        VideoInfo videoInfo = new VideoInfo(url);
        videoInfo.setTitle(title != null ? title : "Video");
        startDownload(videoInfo);
    }
    
    /**
     * Start download from URL with M3U8 flag
     */
    public void startDownload(String url, String title, boolean isM3u8) {
        VideoInfo videoInfo = new VideoInfo(url);
        videoInfo.setTitle(title != null ? title : "Video");
        videoInfo.setM3U8(isM3u8);
        startDownload(videoInfo);
    }
    
    /**
     * Cancel a download
     * @param videoId Video/Work ID
     */
    public void cancelDownload(String videoId) {
        WorkManager.getInstance(context)
                .cancelUniqueWork(DownloadWorker.WORK_TAG_PREFIX + videoId);
    }
    
    /**
     * Check if a download is scheduled or running
     * @param videoId Video/Work ID
     * @return true if download is scheduled/running
     */
    public boolean isDownloadScheduled(String videoId) {
        try {
            var workInfos = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(DownloadWorker.WORK_TAG_PREFIX + videoId)
                    .get();
            
            if (workInfos.isEmpty()) {
                return false;
            }
            
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
     * Cancel all downloads
     */
    public void cancelAllDownloads() {
        WorkManager.getInstance(context).cancelAllWork();
    }
}