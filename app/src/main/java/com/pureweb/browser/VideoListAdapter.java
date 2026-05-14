package com.pureweb.browser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
        private TextView tvType;
        private Button btnPreview;
        private Button btnDownload;
        private ImageView ivIcon;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvVideoTitle);
            tvType = itemView.findViewById(R.id.tvVideoType);
            btnPreview = itemView.findViewById(R.id.btnPreview);
            btnDownload = itemView.findViewById(R.id.btnDownload);
            ivIcon = itemView.findViewById(R.id.ivVideoType);
        }

        void bind(VideoInfo video) {
            // Title
            String title = video.getTitle();
            tvTitle.setText(title != null && !title.isEmpty() ? title : "Video");
            
            // Format/Type badge
            String typeText;
            int bgColor;
            
            if (video.isM3u8()) {
                typeText = "HLS Stream";
                bgColor = 0xFF4CAF50;
            } else if (video.isMpd()) {
                typeText = "DASH Stream";
                bgColor = 0xFFFF9800;
            } else if (video.isRegularDownload()) {
                typeText = video.getExt() != null ? video.getExt().toUpperCase() : "MP4";
                bgColor = 0xFF2196F3;
            } else {
                typeText = video.getExt() != null ? video.getExt().toUpperCase() : "VIDEO";
                bgColor = 0xFF9C27B0;
            }
            
            tvType.setText(typeText);
            tvType.setBackgroundColor(bgColor);
            
            // Icon based on type
            if (video.isM3u8()) {
                ivIcon.setText("📺");
            } else if (video.isMpd()) {
                ivIcon.setText("🎞️");
            } else {
                ivIcon.setText("📹");
            }
            
            // Download button
            btnDownload.setOnClickListener(v -> {
                if (downloadListener != null) {
                    downloadListener.onClick(video);
                }
            });
            
            // Preview button
            btnPreview.setOnClickListener(v -> {
                if (playListener != null) {
                    playListener.onClick(video);
                }
            });
        }
    }
}