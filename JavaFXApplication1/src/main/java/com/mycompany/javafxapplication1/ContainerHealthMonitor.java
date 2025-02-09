package com.mycompany.javafxapplication1;

import java.util.*;
import java.util.concurrent.*;

import com.mycompany.javafxapplication1.SystemLogger.LogLevel;

import java.io.File;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.application.Platform;

public class ContainerHealthMonitor {
    private final Map<String, ContainerStatus> containerHealth = new ConcurrentHashMap<>();
    private final Map<String, ObjectProperty<Double>> containerHealthScores = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final LoadBalancerDB loadBalancerDB;
    private final Map<String, Integer> failureCount = new ConcurrentHashMap<>();
    private static final int MAX_FAILURES = 3;
    private final SystemLogger logger = SystemLogger.getInstance();
    
    public enum ContainerStatus {
        HEALTHY(100.0),
        DEGRADED(50.0),
        UNHEALTHY(25.0),
        OFFLINE(0.0);
        
        final double score;
        ContainerStatus(double score) { this.score = score; }
    }
    
    public ContainerHealthMonitor(LoadBalancerDB loadBalancerDB) {
        this.loadBalancerDB = loadBalancerDB;
        startHealthChecks();
    }
    
    private void startHealthChecks() {
        scheduler.scheduleAtFixedRate(this::checkContainers, 0, 30, TimeUnit.SECONDS);
    }
    
    private void checkContainers() {
        List<String> containers = loadBalancerDB.getStorageContainers();
        for (String container : containers) {
            ContainerStatus status = checkContainerHealth(container);
            updateContainerStatus(container, status);
        }
    }

    private ContainerStatus checkContainerHealth(String container) {
        File containerDir = new File("storage/" + container);
        if (!containerDir.exists()) return ContainerStatus.OFFLINE;

        try {
            // Test write access
            File testFile = new File(containerDir, ".health_check");
            boolean canWrite = testFile.createNewFile();
            if (canWrite) testFile.delete();

            // Check disk space
            long freeSpace = containerDir.getFreeSpace();
            double spaceGB = freeSpace / (1024.0 * 1024.0 * 1024.0);

            // Calculate health based on multiple metrics
            if (!canWrite) return ContainerStatus.UNHEALTHY;
            if (spaceGB < 1.0) return ContainerStatus.DEGRADED;
            if (spaceGB < 0.1) return ContainerStatus.UNHEALTHY;
            
            return ContainerStatus.HEALTHY;
        } catch (Exception e) {
            return ContainerStatus.UNHEALTHY;
        }
    }

    private void updateContainerStatus(String container, ContainerStatus newStatus) {
        ContainerStatus oldStatus = containerHealth.get(container);
        
        if (newStatus == ContainerStatus.DEGRADED) {
            logger.log(LogLevel.WARN, "Container health degraded: " + container);
        } else if (newStatus == ContainerStatus.UNHEALTHY || newStatus == ContainerStatus.OFFLINE) {
            logger.log(LogLevel.ERROR, "Container " + container + " is " + newStatus);
    }

        containerHealth.put(container, newStatus);
        loadBalancerDB.updateContainerStatus(container, newStatus == ContainerStatus.HEALTHY);
        
        // Update health score
        if (!containerHealthScores.containsKey(container)) {
            containerHealthScores.put(container, new SimpleObjectProperty<>(newStatus.score));
            
        }
        final ContainerStatus finalStatus = newStatus;
        Platform.runLater(() -> containerHealthScores.get(container).set(finalStatus.score));

        if (oldStatus != newStatus) {
            System.out.println("Container " + container + " health changed: " + oldStatus + " -> " + newStatus);
        }
    }
    
    public ContainerStatus getContainerStatus(String container) {
        return containerHealth.getOrDefault(container, ContainerStatus.OFFLINE);
    }
    
    public ObjectProperty<Double> containerHealthScoreProperty(String container) {
        return containerHealthScores.computeIfAbsent(container, 
            k -> new SimpleObjectProperty<>(ContainerStatus.OFFLINE.score));
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
}