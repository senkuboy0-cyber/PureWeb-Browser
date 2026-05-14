package com.pureweb.browser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.pureweb.browser.data.VideoInfo;

import java.util.List;

/**
 * Adapter for displaying detected videos in a RecyclerView
 * Uses VideoInfo objects from the new detection system
 */
public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.VideoViewHolder> {

    private List<VideoInfo> videos;
    private OnVideoClickListener downloadListener;
    private OnVideoClickListener playListener;

    public interface OnVideoClickListener {
        void onClick(VideoInfo videoInfo);
    }

    public VideoListAdapter(List<VideoInfo> videos, OnVideoClickListener downloadListener, OnVideoClickListener playListener) {
        this.videos = videos;
        this.downloadListener = downloadListener;
        this.playListener = playListener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        VideoInfo video = videos.get(position);
        holder.bind(video);
    }

    @Override
    public int getItemCount() {
        return videos != null ? videos.size() : 0;
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {
        
        private TextView tvTitle;
        private TextView tvFormat;
        private TextView tvUrl;
        private ImageView ivType;
        private ImageButton btnDownload;
        private ImageButton btnPlay;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvVideoTitle);
            tvFormat = itemView.findViewById(R.id.tvVideoFormat);
            tvUrl = itemView.findViewById(R.id.tvVideoUrl);
            ivType = itemView.findViewById(R.id.ivVideoType);
            btnDownload = itemView.findViewById(R.id.btnDownload);
            btnPlay = itemView.findViewById(R.id.btnPlay);
        }

        void bind(VideoInfo video) {
            // Title
            String title = video.getTitle();
            tvTitle.setText(title != null && !title.isEmpty() ? title : "Video");
            
            // Format badge
            String formatText = video.isM3u8() ? "HLS" : (video.isMpd() ? "DASH" : video.getExt().toUpperCase());
            tvFormat.setText(formatText);
            
            // URL (truncated)
            String url = video.getFirstUrl();
            if (url.length() > 50) {
                url = url.substring(0, 47) + "...";
            }
            tvUrl.setText(url);
            
            // Type icon
            if (video.isM3u8()) {
                ivType.setImageResource(android.R.drawable.ic_menu_slideshow);
                ivType.setColorFilter(0xFF4CAF50);
            } else if (video.isMpd()) {
                ivType.setImageResource(android.R.drawable.ic_menu_slideshow);
                ivType.setColorFilter(0xFFFF9800);
            } else {
                ivType.setImageResource(android.R.drawable.ic_media_play);
                ivType.setColorFilter(0xFF2196F3);
            }
            
            // Download button
            btnDownload.setOnClickListener(v -> {
                if (downloadListener != null) {
                    downloadListener.onClick(video);
                }
            });
            
            // Play button
            btnPlay.setOnClickListener(v -> {
                if (playListener != null) {
                    playListener.onClick(video);
                }
            });
            
            // Item click - open download
            itemView.setOnClickListener(v -> {
                if (downloadListener != null) {
                    downloadListener.onClick(video);
                }
            });
        }
    }
}