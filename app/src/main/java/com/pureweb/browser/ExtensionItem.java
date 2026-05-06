package com.pureweb.browser;

public class ExtensionItem {
    private String name;
    private String id;
    private String summary;
    private double rating;
    private String iconUrl;

    public ExtensionItem(String name, String id, String summary, double rating, String iconUrl) {
        this.name = name;
        this.id = id;
        this.summary = summary;
        this.rating = rating;
        this.iconUrl = iconUrl;
    }

    public String getName() { return name; }
    public String getId() { return id; }
    public String getSummary() { return summary; }
    public double getRating() { return rating; }
    public String getIconUrl() { return iconUrl; }
}