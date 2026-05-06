package com.pureweb.browser;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.ViewHolder> {

    public interface OnDownloadClick {
        void onClick(String url, String title);
    }

    public interface OnPreviewClick {
        void onClick(String url);
    }

    private final List<Map<String, String>> videos;
    private final OnDownloadClick downloadClick;
    private final OnPreviewClick previewClick;

    public VideoAdapter(List<Map<String, String>> videos,
                        OnDownloadClick dl, OnPreviewClick pv) {
        this.videos = videos;
        this.downloadClick = dl;
        this.previewClick = pv;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, String> video = videos.get(position);
        String url   = video.get("url");
        String title = video.get("title");
        String type  = video.get("type");

        // Title
        String displayTitle = (title != null && !title.isEmpty())
                ? title : "Video " + (position + 1);
        holder.tvTitle.setText(displayTitle);

        // Type badge (mp4 / m3u8 / xhr / blob ইত্যাদি)
        holder.tvType.setText(type != null ? type.toUpperCase() : "VIDEO");

        // Blob URL → preview নেই
        boolean isBlob = url != null && url.startsWith("blob:");
        holder.btnPreview.setEnabled(!isBlob);
        holder.btnPreview.setAlpha(isBlob ? 0.5f : 1f);

        holder.btnPreview.setOnClickListener(v -> {
            if (url != null) previewClick.onClick(url);
        });
        holder.btnDownload.setOnClickListener(v -> {
            if (url != null) downloadClick.onClick(url, displayTitle);
        });
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvType;
        Button btnPreview, btnDownload;

        ViewHolder(View view) {
            super(view);
            tvTitle     = view.findViewById(R.id.tvVideoTitle);
            tvType      = view.findViewById(R.id.tvVideoType);
            btnPreview  = view.findViewById(R.id.btnPreview);
            btnDownload = view.findViewById(R.id.btnDownload);
        }
    }
}

