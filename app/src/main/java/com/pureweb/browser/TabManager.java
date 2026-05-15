package com.pureweb.browser;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;

import java.util.ArrayList;
import java.util.List;

/**
 * TabManager - Manages multiple GeckoSession instances for tabbed browsing
 */
public class TabManager {

    public interface TabListener {
        void onTabChanged(int position);
        void onTabCountChanged(int count);
        void onTabTitleChanged(int position, String title);
    }

    private List<Tab> tabs = new ArrayList<>();
    private int currentIndex = -1;
    private GeckoRuntime runtime;
    private GeckoView geckoView;
    private TabListener listener;
    private Handler handler = new Handler(Looper.getMainLooper());

    public TabManager(GeckoRuntime runtime, GeckoView geckoView) {
        this.runtime = runtime;
        this.geckoView = geckoView;
    }

    public void setListener(TabListener listener) {
        this.listener = listener;
    }

    public int newTab(String url) {
        Tab tab = new Tab();
        tab.session = new GeckoSession();
        tab.session.open(runtime);
        tab.title = "New Tab";
        tab.url = url != null ? url : "about:blank";

        // Set progress delegate for title tracking
        tab.session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String pageUrl) {
                tab.url = pageUrl;
            }
            @Override
            public void onPageStop(GeckoSession session, boolean success) {}
            @Override
            public void onProgressChange(GeckoSession session, int progress) {
                tab.progress = progress;
            }
        });

        // Track page title
        tab.session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession session, String title) {
                tab.title = title != null && !title.isEmpty() ? title : tab.url;
                if (listener != null && tabs.indexOf(tab) == currentIndex) {
                    listener.onTabTitleChanged(currentIndex, tab.title);
                }
            }
            @Override public void onFullScreen(GeckoSession session, boolean fullScreen) {}
            @Override public void onCrash(GeckoSession session) {}
            @Override public void onContextMenu(GeckoSession session, int screenX, int screenY, GeckoSession.ContentDelegate.ContextElement element) {}
        });

        tabs.add(tab);
        switchToTab(tabs.size() - 1);

        if (url != null && !url.equals("about:blank") && !url.isEmpty()) {
            tab.session.loadUri(url);
        }

        if (listener != null) listener.onTabCountChanged(tabs.size());
        return tabs.size() - 1;
    }

    public void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;

        // Detach current session from view
        if (currentIndex >= 0 && currentIndex < tabs.size()) {
            // Session stays open but detached from view
        }

        currentIndex = index;
        Tab tab = tabs.get(index);
        geckoView.setSession(tab.session);

        if (listener != null) {
            listener.onTabChanged(index);
        }
    }

    public void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (tabs.size() <= 1) {
            // Don't close the last tab, just load blank
            tabs.get(0).session.loadUri("about:blank");
            tabs.get(0).title = "New Tab";
            tabs.get(0).url = "about:blank";
            if (listener != null) {
                listener.onTabTitleChanged(0, "New Tab");
                listener.onTabChanged(0);
            }
            return;
        }

        Tab tab = tabs.get(index);
        tab.session.close();
        tabs.remove(index);

        // Adjust current index
        if (currentIndex >= tabs.size()) currentIndex = tabs.size() - 1;
        if (index <= currentIndex && currentIndex > 0) currentIndex--;

        switchToTab(currentIndex);
        if (listener != null) listener.onTabCountChanged(tabs.size());
    }

    public Tab getCurrentTab() {
        if (currentIndex < 0 || currentIndex >= tabs.size()) return null;
        return tabs.get(currentIndex);
    }

    public int getCurrentIndex() { return currentIndex; }
    public List<Tab> getTabs() { return tabs; }
    public int getTabCount() { return tabs.size(); }

    public static class Tab {
        public GeckoSession session;
        public String title = "New Tab";
        public String url = "about:blank";
        public int progress = 0;
        public Bitmap thumbnail;

        public String getDisplayTitle() {
            return title != null && !title.isEmpty() ? title : "New Tab";
        }

        public String getDisplayUrl() {
            if (url != null && url.length() > 50) {
                return url.substring(0, 47) + "...";
            }
            return url != null ? url : "about:blank";
        }

        public String getFavicon() {
            if (url != null) {
                if (url.contains("google.com")) return "🔍";
                if (url.contains("youtube.com") || url.contains("youtu.be")) return "▶️";
                if (url.contains("facebook.com")) return "📘";
                if (url.contains("twitter.com") || url.contains("x.com")) return "🐦";
                if (url.contains("github.com")) return "🐙";
                if (url.contains("reddit.com")) return "🤖";
                if (url.contains("wikipedia.org")) return "📖";
                if (url.contains("amazon")) return "🛒";
                if (url.contains("instagram")) return "📸";
            }
            return "🌐";
        }
    }
}