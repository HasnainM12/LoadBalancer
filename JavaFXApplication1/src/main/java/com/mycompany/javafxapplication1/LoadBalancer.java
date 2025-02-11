package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

public class LoadBalancer {
    private static LoadBalancer instance;
    private final Queue<FileOperation> requestQueue;
    private final List<String> storageContainers;
    private final Map<String, Integer> containerLoad;
    private final LoadBalancerDB loadBalancerDB;
    private final ExecutorService executorService;
    private SchedulingAlgorithm schedulingAlgorithm = SchedulingAlgorithm.FCFS;
    private final SystemLogger logger = SystemLogger.getInstance();
    
    public enum SchedulingAlgorithm {
        FCFS, SJN, ROUND_ROBIN
    }
    
    private LoadBalancer() {
        requestQueue = new ConcurrentLinkedQueue<>();
        loadBalancerDB = new LoadBalancerDB();
        storageContainers = Collections.synchronizedList(new ArrayList<>());
        containerLoad = new ConcurrentHashMap<>();
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        initializeContainers();
        startRequestProcessor();
    }
    
    public static LoadBalancer getInstance() {
        if (instance == null) {
            synchronized(LoadBalancer.class) {
                if (instance == null) {
                    instance = new LoadBalancer();
                }
            }
        }
        return instance;
    }

    private void initializeContainers() {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            storageContainers.addAll(loadBalancerDB.getStorageContainers(conn));
            for (String container : storageContainers) {
                containerLoad.put(container, 0);
            }
        } catch (SQLException e) {
            logger.log(SystemLogger.LogLevel.ERROR, "Failed to initialize containers: " + e.getMessage());
        }
    }

    public CompletableFuture<String> submitOperation(FileOperation operation) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        logger.log(SystemLogger.LogLevel.INFO, "Operation scheduled: " + operation.getType() + " - " + operation.getFilename());
        executorService.submit(() -> {
            try {
                String container = processOperation(operation);
                future.complete(container);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }

    private String processOperation(FileOperation operation) {
        String container = getNextAvailableContainer();
        logger.log(SystemLogger.LogLevel.INFO, "Container " + container + " selected for operation: " + operation.getType());
        
        try (Connection conn = DBConnection.getMySQLConnection()) {
            Thread.sleep(operation.getEstimatedProcessingTime());
            loadBalancerDB.logOperation(container, operation.getType().name(), conn);
            containerLoad.compute(container, (k, v) -> v + 1);
            return container;
        } catch (Exception e) {
            logger.log(SystemLogger.LogLevel.ERROR, "Operation processing failed: " + e.getMessage());
            throw new RuntimeException("Operation processing failed", e);
        } finally {
            containerLoad.compute(container, (k, v) -> v - 1);
        }
    }

    private String getNextAvailableContainer() {
        if (storageContainers.isEmpty()) {
            throw new IllegalStateException("No storage containers available");
        }

        switch (schedulingAlgorithm) {
            case FCFS:
                return getFirstAvailableContainer();
            case SJN:
                return getLeastLoadedContainer();
            case ROUND_ROBIN:
                return getNextRoundRobinContainer();
            default:
                throw new IllegalStateException("Unknown scheduling algorithm");
        }
    }

    private String getFirstAvailableContainer() {
        return storageContainers.get(0);
    }

    private String getLeastLoadedContainer() {
        return storageContainers.stream()
            .min(Comparator.comparingInt(containerLoad::get))
            .orElse(storageContainers.get(0));
    }

    private int rrIndex = 0;
    
    private synchronized String getNextRoundRobinContainer() {
        String container = storageContainers.get(rrIndex);
        rrIndex = (rrIndex + 1) % storageContainers.size();
        return container;
    }

    public void setSchedulingAlgorithm(SchedulingAlgorithm algorithm) {
        this.schedulingAlgorithm = algorithm;
        logger.log(SystemLogger.LogLevel.INFO, "Switched Load Balancer to: " + algorithm);
    }

    private void startRequestProcessor() {
        Thread processor = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    processQueuedRequests();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        processor.setDaemon(true);
        processor.start();
    }

    private void processQueuedRequests() {
        FileOperation operation;
        while ((operation = requestQueue.poll()) != null) {
            final FileOperation currentOperation = operation;
            executorService.submit(() -> processOperation(currentOperation));
        }
    }

    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        requestQueue.clear();
        storageContainers.clear();
        containerLoad.clear();
        instance = null;
    }
}