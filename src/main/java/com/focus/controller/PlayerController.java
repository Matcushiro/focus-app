package com.focus.controller;

import com.focus.model.Movie;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class PlayerController implements Initializable {

    @FXML private MediaView mediaView;
    @FXML private StackPane playerPane;
    @FXML private Slider progressSlider;
    @FXML private Slider volumeSlider;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    @FXML private Button playPauseBtn;
    @FXML private Label movieTitleLabel;

    private MediaPlayer mediaPlayer;
    private Movie currentMovie;
    private boolean isPlaying = false;
    private boolean sliderDragging = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupVolumeSlider();
        setupProgressSlider();
    }

    public void setMovie(Movie movie) {
        this.currentMovie = movie;
        movieTitleLabel.setText(movie.getTitle());
        loadVideo(movie.getVideoPath());
    }

    private void loadVideo(String videoPath) {
        try {
            File file = new File(videoPath);
            if (!file.exists()) {
                System.out.println(
                        "❌ Файл не найден: " + videoPath
                );
                return;
            }

            String uri = file.toURI().toString();
            Media media = new Media(uri);
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);

            mediaView.fitWidthProperty()
                    .bind(playerPane.widthProperty());
            mediaView.fitHeightProperty()
                    .bind(playerPane.heightProperty());

            mediaPlayer.currentTimeProperty()
                    .addListener((obs, oldTime, newTime) -> {
                        if (!sliderDragging) {
                            Platform.runLater(() ->
                                    updateProgress(newTime)
                            );
                        }
                    });

            mediaPlayer.setOnReady(() -> {
                Duration total = mediaPlayer
                        .getMedia().getDuration();
                totalTimeLabel.setText(
                        formatTime(total)
                );
                progressSlider.setMax(
                        total.toSeconds()
                );
            });

            mediaPlayer.setOnEndOfMedia(() -> {
                playPauseBtn.setText("▶");
                isPlaying = false;
                progressSlider.setValue(0);
            });

            mediaPlayer.play();
            isPlaying = true;
            playPauseBtn.setText("⏸");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        if (isPlaying) {
            mediaPlayer.pause();
            playPauseBtn.setText("▶");
        } else {
            mediaPlayer.play();
            playPauseBtn.setText("⏸");
        }
        isPlaying = !isPlaying;
    }

    @FXML
    private void stopVideo() {
        if (mediaPlayer == null) return;
        mediaPlayer.stop();
        isPlaying = false;
        playPauseBtn.setText("▶");
        progressSlider.setValue(0);
        currentTimeLabel.setText("00:00");
    }

    private void setupVolumeSlider() {
        volumeSlider.valueProperty()
                .addListener((obs, oldVal, newVal) -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.setVolume(
                                newVal.doubleValue()
                        );
                    }
                });
    }

    private void setupProgressSlider() {
        progressSlider.setOnMousePressed(
                e -> sliderDragging = true
        );
        progressSlider.setOnMouseReleased(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(
                        progressSlider.getValue()
                ));
            }
            sliderDragging = false;
        });
    }

    private void updateProgress(Duration current) {
        progressSlider.setValue(current.toSeconds());
        currentTimeLabel.setText(formatTime(current));
    }

    private String formatTime(Duration duration) {
        int seconds = (int) duration.toSeconds();
        int minutes = seconds / 60;
        int hours   = minutes / 60;
        seconds = seconds % 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format(
                    "%02d:%02d:%02d",
                    hours, minutes, seconds
            );
        } else {
            return String.format(
                    "%02d:%02d", minutes, seconds
            );
        }
    }

    @FXML
    private void goBackToDetail() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/focus/fxml/detail.fxml"
                    )
            );
            Node page = loader.load();
            DetailController ctrl =
                    loader.getController();
            ctrl.setMovie(currentMovie);
            BorderPane root =
                    (BorderPane) playerPane
                            .getScene().getRoot();
            root.setCenter(page);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}