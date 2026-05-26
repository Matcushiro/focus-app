package com.focus.model;

public class Movie {
    private int id;
    private String title;
    private String description;
    private String posterPath;
    private String bannerPath;
    private String videoPath;
    private String trailerPath;
    private double rating;
    private int year;
    private int duration;
    private String director;
    private String country;
    private String category;
    private String genres;
    private boolean nowPlaying;
    private boolean latest;
    private boolean topRated;
    private boolean popular;
    private boolean kids;
    private boolean evening;
    private boolean turkish;
    private boolean top10;
    private boolean featured;

    public Movie() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) {
        this.description = description;
    }

    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) {
        this.posterPath = posterPath;
    }

    public String getBannerPath() { return bannerPath; }
    public void setBannerPath(String bannerPath) {
        this.bannerPath = bannerPath;
    }

    public String getVideoPath() { return videoPath; }
    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public String getTrailerPath() { return trailerPath; }
    public void setTrailerPath(String trailerPath) {
        this.trailerPath = trailerPath;
    }

    public double getRating() { return rating; }
    public void setRating(double rating) {
        this.rating = rating;
    }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) {
        this.duration = duration;
    }

    public String getDirector() { return director; }
    public void setDirector(String director) {
        this.director = director;
    }

    public String getCountry() { return country; }
    public void setCountry(String country) {
        this.country = country;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) {
        this.category = category;
    }

    public String getGenres() { return genres; }
    public void setGenres(String genres) {
        this.genres = genres;
    }

    public boolean isNowPlaying() { return nowPlaying; }
    public void setNowPlaying(boolean nowPlaying) {
        this.nowPlaying = nowPlaying;
    }

    public boolean isLatest() { return latest; }
    public void setLatest(boolean latest) {
        this.latest = latest;
    }

    public boolean isTopRated() { return topRated; }
    public void setTopRated(boolean topRated) {
        this.topRated = topRated;
    }

    public boolean isPopular() { return popular; }
    public void setPopular(boolean popular) {
        this.popular = popular;
    }

    public boolean isKids() { return kids; }
    public void setKids(boolean kids) {
        this.kids = kids;
    }

    public boolean isEvening() { return evening; }
    public void setEvening(boolean evening) {
        this.evening = evening;
    }

    public boolean isTurkish() { return turkish; }
    public void setTurkish(boolean turkish) {
        this.turkish = turkish;
    }

    public boolean isTop10() { return top10; }
    public void setTop10(boolean top10) {
        this.top10 = top10;
    }

    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) {
        this.featured = featured;
    }
}