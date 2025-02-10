package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.mycompany.javafxapplication1.SystemLogger.LogLevel;

import java.util.logging.Level;

public class LoadBalancer {
    private static LoadBalancer instance;
    private final Queue<FileOperation> requestQueue;
    private final List<String> storageContainers;
    private final Map<String, Integer> containerLoad;
    private final LoadBalancerDB loadBalancerDB;
    private final ExecutorService executorService;
    private final ContainerHealthMonitor healthMonitor;
    private static final Logger LOGGER = Logger.getLogger(LoadBalancer.class.getName());
    private SchedulingAlgorithm schedulingAlgorithm = SchedulingAlgorithm.FCFS; // Default to FCFS
    private final SystemLogger logger = SystemLogger.getInstance();
    
    private LoadBalancer() {
        requestQueue = new ConcurrentLinkedQueue<>();
        loadBalancerDB = new LoadBalancerDB();
        storageContainers = Collections.synchronizedList(new ArrayList<>());
        containerLoad = new ConcurrentHashMap<>();
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.healthMonitor = new ContainerHealthMonitor(loadBalancerDB);
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

    public enum SchedulingAlgorithm {
        FCFS, SJN, ROUND_ROBIN
    }
    
    private void initializeContainers() {
        storageContainers.addAll(loadBalancerDB.getStorageContainers());
        for (String container : storageContainers) {
            containerLoad.put(container, 0);
        }
    }

    public CompletableFuture<String> submitOperation(FileOperation operation) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        logger.log(LogLevel.INFO, "Operation scheduled: " + operation.getType() + " - " + operation.getFilename());
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
        logger.log(LogLevel.INFO, "Container " + container + " selected for operation: " + operation.getType());
    
        try { 
            Thread.sleep(operation.getEstimatedProcessingTime());
    
            // ✅ FIX: Pass the operation type correctly
            loadBalancerDB.logOperation(container, operation.getType().name());
    
            containerLoad.compute(container, (k, v) -> v + 1);
    
            LOGGER.log(Level.INFO, "Operation {0} processed on container {1}",
                    new Object[]{operation.getType(), container});
    
            return container;
    
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Operation processing interrupted", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operation processing interrupted", e);
        } finally {
            containerLoad.compute(container, (k, v) -> v - 1);
        }
    }
    

    private String getNextAvailableContainer() {
        if (storageContainers.isEmpty()) {
            throw new IllegalStateException("No storage containers available");
        }
    
        List<String> healthyContainers = storageContainers.stream()
            .filter(container -> healthMonitor.getContainerStatus(container) == ContainerHealthMonitor.ContainerStatus.HEALTHY)
            .collect(Collectors.toList());
    
        if (healthyContainers.isEmpty()) {
            throw new IllegalStateException("No healthy containers available");
        }
    
        switch (schedulingAlgorithm) {
            case FCFS:
                return getFirstAvailableContainer(healthyContainers);  // FIFO order
    
            case SJN:
                return healthyContainers.stream()
                    .min(Comparator.comparingInt(containerLoad::get))
                    .orElse(healthyContainers.get(0));  // Least-loaded container
    
            case ROUND_ROBIN:
                return getNextRoundRobinContainer(healthyContainers);  // Rotate evenly
    
            default:
                throw new IllegalStateException("Unknown scheduling algorithm");
        }
    }

    private String getFirstAvailableContainer(List<String> containers) {
        return containers.get(0); // First available container in order
    }

    private int rrIndex = 0; // Round Robin index

    private synchronized String getNextRoundRobinContainer(List<String> containers) {
        if (containers.isEmpty()) throw new IllegalStateException("No containers available");

        String container = containers.get(rrIndex);
        rrIndex = (rrIndex + 1) % containers.size(); // Cycle to the next container
        return container;
    }

    public void setSchedulingAlgorithm(SchedulingAlgorithm algorithm) {
        this.schedulingAlgorithm = algorithm;
        LOGGER.log(Level.INFO, "Switched Load Balancer to: " + algorithm);
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

    public ContainerHealthMonitor getHealthMonitor() {
        return healthMonitor;
    }

    private void processQueuedRequests() {
        FileOperation operation;
        while ((operation = requestQueue.poll()) != null) {
            final FileOperation currentOperation = operation;
            executorService.submit(() -> processOperation(currentOperation));
        }
    }

    public void addStorageContainer(String containerName) {
        synchronized(storageContainers) {
            if (!storageContainers.contains(containerName)) {
                storageContainers.add(containerName);
                containerLoad.put(containerName, 0);
                loadBalancerDB.addStorageContainer(containerName);
            }
        }
    }

    public void removeStorageContainer(String containerName) {
        synchronized(storageContainers) {
            storageContainers.remove(containerName);
            containerLoad.remove(containerName);
            loadBalancerDB.removeStorageContainer(containerName);
        }
    }

    public List<String> getAvailableContainers() {
        synchronized(storageContainers) {
            return new ArrayList<>(storageContainers);
        }
    }

    public Map<String, Integer> getContainerLoads() {
        return new HashMap<>(containerLoad);
    }


    public void createChunksTable() {
    String query = "CREATE TABLE IF NOT EXISTS FileChunks (" +
                   "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                   "file_id INTEGER, " +
                   "chunk_number INTEGER, " +
                   "container_id VARCHAR(255), " +
                   "encryption_key TEXT, " +
                   "FOREIGN KEY(file_id) REFERENCES Files(id))";
    try (Connection conn = DBConnection.getMySQLConnection();
         PreparedStatement stmt = conn.prepareStatement(query)) {
        stmt.executeUpdate();
    } catch (SQLException e) {
        System.err.println("Error creating chunks table: " + e.getMessage());
        }
    }

    public void saveChunkMetadata(FileChunk chunk) {
        String query = "INSERT INTO FileChunks (file_id, chunk_number, container_id, encryption_key) " +
                    "VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getMySQLConnection();
            PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, chunk.getFileId());
            stmt.setInt(2, chunk.getChunkNumber());
            stmt.setString(3, chunk.getContainerId());
            stmt.setString(4, chunk.getEncryptionKey());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving chunk metadata: " + e.getMessage());
        }
    }

    public void shutdown() {
        // Shutdown the executor service
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Shutdown the health monitor
        if (healthMonitor != null) {
            healthMonitor.shutdown();
        }

        // Shutdown the LoadBalancerDB
        if (loadBalancerDB != null) {
            loadBalancerDB.shutdown();
        }

        // Clear collections
        requestQueue.clear();
        storageContainers.clear();
        containerLoad.clear();

        // Reset instance
        instance = null;
    }
}
