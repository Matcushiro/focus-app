package com.focus.model;

public class Movie {

    private int     id;
    private String  title;
    private String  description;
    private String  posterPath;
    private String  bannerPath;
    private String  videoPath;
    private String  trailerPath;
    private double  rating;
    private int     year;
    private int     duration;
    private String  director;
    private String  country;
    private String  category;
    private String  genres;

    // ===== Флаги =====
    private boolean nowPlaying;
    private boolean latest;
    private boolean topRated;
    private boolean popular;
    private boolean kids;
    private boolean evening;
    private boolean turkish;
    private boolean top10;
    private boolean featured;

    // Детские флаги
    private boolean kidsFeatured;
    private boolean kidsPopular;
    private boolean kidsLatest;
    private boolean kidsRecommended; // НОВЫЙ

    // ===== Геттеры / Сеттеры =====
    public int    getId()          { return id; }
    public void   setId(int id)    { this.id = id; }

    public String getTitle()            { return title; }
    public void   setTitle(String v)    { this.title = v; }

    public String getDescription()         { return description; }
    public void   setDescription(String v) { this.description = v; }

    public String getPosterPath()         { return posterPath; }
    public void   setPosterPath(String v) { this.posterPath = v; }

    public String getBannerPath()         { return bannerPath; }
    public void   setBannerPath(String v) { this.bannerPath = v; }

    public String getVideoPath()         { return videoPath; }
    public void   setVideoPath(String v) { this.videoPath = v; }

    public String getTrailerPath()         { return trailerPath; }
    public void   setTrailerPath(String v) { this.trailerPath = v; }

    public double getRating()          { return rating; }
    public void   setRating(double v)  { this.rating = v; }

    public int  getYear()       { return year; }
    public void setYear(int v)  { this.year = v; }

    public int  getDuration()       { return duration; }
    public void setDuration(int v)  { this.duration = v; }

    public String getDirector()         { return director; }
    public void   setDirector(String v) { this.director = v; }

    public String getCountry()         { return country; }
    public void   setCountry(String v) { this.country = v; }

    public String getCategory()         { return category; }
    public void   setCategory(String v) { this.category = v; }

    public String getGenres()         { return genres; }
    public void   setGenres(String v) { this.genres = v; }

    public boolean isNowPlaying()           { return nowPlaying; }
    public void    setNowPlaying(boolean v) { this.nowPlaying = v; }

    public boolean isLatest()          { return latest; }
    public void    setLatest(boolean v){ this.latest = v; }

    public boolean isTopRated()           { return topRated; }
    public void    setTopRated(boolean v) { this.topRated = v; }

    public boolean isPopular()           { return popular; }
    public void    setPopular(boolean v) { this.popular = v; }

    public boolean isKids()           { return kids; }
    public void    setKids(boolean v) { this.kids = v; }

    public boolean isEvening()           { return evening; }
    public void    setEvening(boolean v) { this.evening = v; }

    public boolean isTurkish()           { return turkish; }
    public void    setTurkish(boolean v) { this.turkish = v; }

    public boolean isTop10()           { return top10; }
    public void    setTop10(boolean v) { this.top10 = v; }

    public boolean isFeatured()           { return featured; }
    public void    setFeatured(boolean v) { this.featured = v; }

    public boolean isKidsFeatured()           { return kidsFeatured; }
    public void    setKidsFeatured(boolean v) { this.kidsFeatured = v; }

    public boolean isKidsPopular()           { return kidsPopular; }
    public void    setKidsPopular(boolean v) { this.kidsPopular = v; }

    public boolean isKidsLatest()           { return kidsLatest; }
    public void    setKidsLatest(boolean v) { this.kidsLatest = v; }

    // НОВЫЙ — рекомендации детского раздела
    public boolean isKidsRecommended()           { return kidsRecommended; }
    public void    setKidsRecommended(boolean v) { this.kidsRecommended = v; }

    @Override
    public String toString() {
        return title + " (" + year + ")";
    }
}