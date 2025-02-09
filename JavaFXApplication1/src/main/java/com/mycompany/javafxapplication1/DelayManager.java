package com.mycompany.javafxapplication1;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class DelayManager {
    private static DelayManager instance;
    private final Random random = new Random();
    private final DoubleProperty progressProperty = new SimpleDoubleProperty(0);
    private final TrafficManager trafficManager = TrafficManager.getInstance();
    
    private DelayManager() {
        // Listen for traffic level changes
        trafficManager.trafficLevelProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("Traffic level changed to: " + newVal + " - " + trafficManager.getCurrentStatus());
        });
    }
    
    public static DelayManager getInstance() {
        if (instance == null) {
            instance = new DelayManager();
        }
        return instance;
    }
    
    public DoubleProperty progressProperty() {
        return progressProperty;
    }
    
    public CompletableFuture<Void> simulateDelay(int minSeconds, int maxSeconds) {
        // Apply traffic multiplier to delay
        double multiplier = trafficManager.getTrafficMultiplier();
        int adjustedMinSeconds = (int)(minSeconds * multiplier);
        int adjustedMaxSeconds = (int)(maxSeconds * multiplier);
        
        int delaySeconds = random.nextInt(adjustedMaxSeconds - adjustedMinSeconds + 1) + adjustedMinSeconds;
        long startTime = System.currentTimeMillis();
        long delayMillis = delaySeconds * 1000L;
        
        System.out.println("Current traffic multiplier: " + multiplier + "x");
        System.out.println("Adjusted delay: " + delaySeconds + " seconds");
        
        return CompletableFuture.runAsync(() -> {
            try {
                while (System.currentTimeMillis() - startTime < delayMillis) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    double progress = (double) elapsed / delayMillis;
                    Platform.runLater(() -> progressProperty.set(progress));
                    Thread.sleep(1000);
                }
                Platform.runLater(() -> progressProperty.set(1.0));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public void resetProgress() {
        Platform.runLater(() -> progressProperty.set(0));
    }
}