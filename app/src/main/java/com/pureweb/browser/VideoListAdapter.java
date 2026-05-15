package com.pureweb.browser;

import android.animation.ObjectAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

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

    public VideoListAdapter(List<VideoInfo> videos,
                            OnVideoClickListener downloadListener,
                            OnVideoClickListener playListener) {
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
        holder.bind(video, position);
    }

    @Override
    public int getItemCount() {
        return videos != null ? videos.size() : 0;
    }

    class VideoViewHolder extends RecyclerView.ViewHolder {

        private TextView tvTitle, tvSource, tvQuality;
        private MaterialButton btnDownload;
        private MaterialCardView card;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.video_title);
            tvSource = itemView.findViewById(R.id.video_source);
            tvQuality = itemView.findViewById(R.id.video_quality);
            btnDownload = itemView.findViewById(R.id.btn_download);
            card = itemView.findViewById(R.id.videoCard);
        }

        void bind(VideoInfo video, int position) {
            // Title
            String title = video.getTitle();
            tvTitle.setText(title != null && !title.isEmpty() ? title : "Unknown Video");

            // Source
            String source = video.getSourceUrl();
            if (source != null && !source.isEmpty()) {
                try {
                    java.net.URL url = new java.net.URL(source);
                    tvSource.setText(url.getHost());
                } catch (Exception e) {
                    tvSource.setText(source);
                }
            } else {
                tvSource.setText("local");
            }

            // Quality / Format info
            String qualityText;
            if (video.isM3u8()) {
                qualityText = "📺 HLS Stream";
            } else if (video.isMpd()) {
                qualityText = "🎞️ DASH Stream";
            } else {
                String ext = video.getExt();
                String size = video.getFileSize();
                if (size != null && !size.isEmpty()) {
                    qualityText = (ext != null ? ext.toUpperCase() : "MP4") + " • " + size;
                } else {
                    qualityText = ext != null ? ext.toUpperCase() : "📹 Video";
                }
            }
            tvQuality.setText(qualityText);

            // Animate entrance
            card.setAlpha(0f);
            card.setTranslationX(-30f);
            card.animate()
                    .alpha(1f).translationX(0f)
                    .setDuration(300)
                    .setStartDelay(position * 60L)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            // Download button
            btnDownload.setOnClickListener(v -> {
                btnDownload.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80)
                        .withEndAction(() -> btnDownload.animate()
                                .scaleX(1f).scaleY(1f).setDuration(80).start())
                        .start();
                if (downloadListener != null) {
                    downloadListener.onClick(video);
                }
            });

            // Click card to preview
            card.setOnClickListener(v -> {
                if (playListener != null && video.getFirstUrl() != null) {
                    playListener.onClick(video);
                }
            });
        }
    }
}
