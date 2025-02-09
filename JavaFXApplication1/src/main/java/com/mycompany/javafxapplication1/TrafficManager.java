package com.mycompany.javafxapplication1;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class TrafficManager {
    private static TrafficManager instance;
    private final Random random = new Random();
    private final ObjectProperty<TrafficLevel> currentLevel = new SimpleObjectProperty<>(TrafficLevel.MEDIUM);
    private LocalTime lastUpdate = LocalTime.now();
    private final ObjectProperty<Double> currentMultiplier = new SimpleObjectProperty<>(1.0);
    
    public enum TrafficLevel {
        LOW(0.5, 1.0, "Low traffic - Fast response times"),
        MEDIUM(1.0, 2.0, "Medium traffic - Normal response times"),
        HIGH(2.0, 3.0, "High traffic - Slower response times"),
        PEAK(3.0, 4.0, "Peak traffic - Expect delays");
        
        final double minMultiplier;
        final double maxMultiplier;
        final String description;
        
        TrafficLevel(double min, double max, String description) {
            this.minMultiplier = min;
            this.maxMultiplier = max;
            this.description = description;
        }
    }
    
    private TrafficManager() {
        startPeriodicUpdate();
    }
    
    public static TrafficManager getInstance() {
        if (instance == null) {
            instance = new TrafficManager();
        }
        return instance;
    }
    
    private void startPeriodicUpdate() {
        Thread updateThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    updateTrafficLevel();
                    Thread.sleep(30000); // Update every 30 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        updateThread.setDaemon(true);
        updateThread.start();
    }
    
    private void updateTrafficLevel() {
        if (ChronoUnit.SECONDS.between(lastUpdate, LocalTime.now()) >= 30) {
            int hour = LocalTime.now().getHour();
            int chance = random.nextInt(100);
            
            // Simulate peak hours (9-11 AM and 2-5 PM)
            if ((hour >= 9 && hour <= 11) || (hour >= 14 && hour <= 17)) {
                chance += 30;
            }
            
            // Night hours (11 PM - 5 AM) tend to have lower traffic
            if (hour >= 23 || hour <= 5) {
                chance -= 30;
            }
            
            chance = Math.min(Math.max(chance, 0), 100);
            
            TrafficLevel newLevel = chance < 20 ? TrafficLevel.LOW :
                                  chance < 60 ? TrafficLevel.MEDIUM :
                                  chance < 90 ? TrafficLevel.HIGH :
                                  TrafficLevel.PEAK;
            
            currentLevel.set(newLevel);
            updateMultiplier();
            lastUpdate = LocalTime.now();
        }
    }
    
    private void updateMultiplier() {
        TrafficLevel level = currentLevel.get();
        double multiplier = level.minMultiplier + 
            random.nextDouble() * (level.maxMultiplier - level.minMultiplier);
        currentMultiplier.set(multiplier);
    }
    
    public double getTrafficMultiplier() {
        return currentMultiplier.get();
    }
    
    public ObjectProperty<TrafficLevel> trafficLevelProperty() {
        return currentLevel;
    }
    
    public ObjectProperty<Double> multiplierProperty() {
        return currentMultiplier;
    }
    
    public String getCurrentStatus() {
        return currentLevel.get().description;
    }
}