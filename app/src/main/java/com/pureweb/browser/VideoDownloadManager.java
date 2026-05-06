package com.pureweb.browser;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class VideoDownloadManager {
    private Context context;

    public VideoDownloadManager(Context context) {
        this.context = context;
    }

    public void download(String url, String title) {
        if (url == null || url.isEmpty()) return;
        String cleanTitle = (title == null || title.isEmpty())
                ? "video_" + System.currentTimeMillis()
                : title.replaceAll("[^a-zA-Z0-9_\\-]", "_").substring(0, Math.min(title.length(), 50));

        if (url.contains(".m3u8") || url.contains("m3u8")) {
            downloadHLS(url, cleanTitle);
        } else {
            downloadDirect(url, cleanTitle);
        }
    }

    // Direct mp4/webm download
    private void downloadDirect(String url, String title) {
        try {
            DownloadManager dm = (DownloadManager)
                    context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request req =
                    new DownloadManager.Request(Uri.parse(url));
            req.setTitle(title);
            req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, title + ".mp4");
            req.addRequestHeader("User-Agent",
                    "Mozilla/5.0 (Android) AppleWebKit/537.36");
            dm.enqueue(req);
            Toast.makeText(context, "⬇ Download started!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Download failed: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // HLS m3u8 stream download — segments merge করে
    private void downloadHLS(String m3u8Url, String title) {
        Toast.makeText(context, "🎬 HLS download starting...",
                Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                List<String> segments = parseM3U8(m3u8Url);
                if (segments.isEmpty()) {
                    showToast("No segments found in stream");
                    return;
                }

                File outputDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS);
                File output = new File(outputDir, title + ".ts");

                FileOutputStream fos = new FileOutputStream(output);
                int total = segments.size();
                for (int i = 0; i < total; i++) {
                    byte[] data = downloadBytes(segments.get(i));
                    if (data != null) fos.write(data);
                    // Progress toast (every 10%)
                    if (i % Math.max(1, total / 10) == 0) {
                        int progress = (int) ((i * 100.0) / total);
                        showToast("Downloading... " + progress + "%");
                    }
                }
                fos.close();
                showToast("✅ Download complete: " + title + ".ts");
            } catch (Exception e) {
                showToast("HLS download failed: " + e.getMessage());
            }
        }).start();
    }

    // m3u8 playlist parse করো
    private List<String> parseM3U8(String url) throws Exception {
        List<String> segments = new ArrayList<>();
        String base = url.substring(0, url.lastIndexOf('/') + 1);

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Android) AppleWebKit/537.36");
        conn.setConnectTimeout(10000);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
        String line;
        boolean isMaster = false;

        while ((line = reader.readLine()) != null) {
            line = line.trim();
            // Master playlist → variant stream খোঁজো
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                isMaster = true;
                String variantUrl = reader.readLine();
                if (variantUrl != null) {
                    variantUrl = variantUrl.trim();
                    if (!variantUrl.startsWith("http")) variantUrl = base + variantUrl;
                    return parseM3U8(variantUrl); // recursive for best quality
                }
            }
            // Media segment
            if (!line.startsWith("#") && !line.isEmpty()) {
                if (!line.startsWith("http")) line = base + line;
                segments.add(line);
            }
        }
        reader.close();
        return segments;
    }

    private byte[] downloadBytes(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Android) AppleWebKit/537.36");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream is = conn.getInputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) > 0) baos.write(buffer, 0, len);
            is.close();
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private void showToast(String msg) {
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).runOnUiThread(() ->
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show());
        }
    }
}

