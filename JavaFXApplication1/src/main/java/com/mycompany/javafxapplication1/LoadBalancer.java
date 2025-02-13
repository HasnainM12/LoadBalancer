package com.mycompany.javafxapplication1;

import com.mycompany.javafxapplication1.Session;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import org.eclipse.paho.client.mqttv3.MqttException;
import com.jcraft.jsch.Channel;


// Assuming these are


public class LoadBalancer {
    private static LoadBalancer instance;
    private final Queue<FileOperation> requestQueue;
    private final List<String> storageContainers;
    private final Map<String, Integer> containerLoad;
    private final LoadBalancerDB loadBalancerDB;
    private final ExecutorService executorService;
    private SchedulingAlgorithm schedulingAlgorithm = SchedulingAlgorithm.FCFS;
    private final SystemLogger logger = SystemLogger.getInstance();
    private final MQTTClient mqttClient;
    private final Map<String, String> taskStates = new ConcurrentHashMap<>();
    private final Map<String, Integer> workerLoad = new ConcurrentHashMap<>();
    private int roundRobinIndex = 0;
    private final Map<String, Long> taskProcessingTimestamps = new ConcurrentHashMap<>();
    
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
        mqttClient = new MQTTClient();
        mqttClient.subscribeToTopic("loadbalancer/processing", this);
        mqttClient.subscribeToTopic("loadbalancer/completed", this);
        mqttClient.subscribeToTopic("loadbalancer/task/complete", this);
        startTaskMonitor(); 
        initializeContainers();
        startRequestProcessor();
        startTaskMonitor();
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

    private void startTaskMonitor() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::checkForStuckTasks, 10, 10, TimeUnit.SECONDS);
    }
    

    public void submitTask(FileOperation operation) {
        executorService.submit(() -> {
            switch (operation.getType()) {
                case UPLOAD:
                    processFileUpload(operation);
                    break;
                case DOWNLOAD:
                    processFileDownload(operation);
                    break;
                case DELETE:
                    processFileDelete(operation);
                    break;
            }
        });
    }


    private void processFileDelete(FileOperation operation) {
        try {
            FileDB fileDB = new FileDB();
    
            // ✅ Retrieve the file ID first
            Long fileId = fileDB.getFileIdByFilename(operation.getFilename());
            if (fileId == null) {
                System.err.println("[ERROR] File ID not found for filename: " + operation.getFilename());
                return;
            }
    
            // ✅ Now, get the actual file path
            String filePath = fileDB.getFilePath(fileId);
            if (filePath == null || !Files.exists(Paths.get(filePath))) {
                System.err.println("[ERROR] File not found: " + operation.getFilename());
                return;
            }
    
            // ✅ Delete file from storage
            Files.delete(Paths.get(filePath));
    
            // ✅ Remove metadata from MySQL
            fileDB.deleteFileMetadata(fileId);
    
            System.out.println("[INFO] File deleted successfully: " + operation.getFilename());
        } catch (Exception e) {
            System.err.println("[ERROR] File deletion failed: " + e.getMessage());
        }
    }

    private void processFileDownload(FileOperation operation) {
        try {
            FileDB fileDB = new FileDB();
    
            // ✅ Retrieve the file ID first
            Long fileId = fileDB.getFileIdByFilename(operation.getFilename());
            if (fileId == null) {
                System.err.println("[ERROR] File ID not found for filename: " + operation.getFilename());
                return;
            }
    
            // ✅ Now, get the actual file path
            String filePath = fileDB.getFilePath(fileId);
            if (filePath == null || !Files.exists(Paths.get(filePath))) {
                System.err.println("[ERROR] File not found: " + operation.getFilename());
                return;
            }
    
            System.out.println("[INFO] File ready for download: " + filePath);
        } catch (Exception e) {
            System.err.println("[ERROR] File retrieval failed: " + e.getMessage());
        }
    }
    

    private void processFileUpload(FileOperation operation) {
        String container = getNextWorker();
        if (container == null) {
            System.err.println("[ERROR] No available storage containers!");
            return;
        }
    
        // Map container names to their hostnames in Docker network
        Map<String, String> containerHosts = Map.of(
            "comp20081-files1", "comp20081-files1",
            "comp20081-files2", "comp20081-files2",
            "comp20081-files3", "comp20081-files3",
            "comp20081-files4", "comp20081-files4"
        );
                
        System.out.println("[DEBUG] Selected container: " + container);
        System.out.println("[DEBUG] Filename: " + operation.getFilename());
        System.out.println("[DEBUG] File path: " + operation.getFilePath());
    
        try {
            JSch jsch = new JSch();
            com.jcraft.jsch.Session sshSession = jsch.getSession("ntu-user", containerHosts.get(container), 22);
            sshSession.setPassword("ntu-user");
            
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            sshSession.setConfig(config);
            
            sshSession.connect(10000);
    
            Channel channel = sshSession.openChannel("sftp");
            channel.connect(5000);
            ChannelSftp sftpChannel = (ChannelSftp) channel;
    
            String remoteFilePath = "/storage/" + operation.getFilename();
            sftpChannel.put(operation.getFilePath(), remoteFilePath);
    
            FileDB fileDB = new FileDB();
            Long fileId = fileDB.addFileMetadata(
                operation.getFilename(),
                Session.getInstance().getUsername(),
                container + ":" + remoteFilePath
            );
    
            System.out.println("[INFO] File uploaded to container " + container + " with ID: " + fileId);
    
            sftpChannel.exit();
            sshSession.disconnect();
    
        } catch (Exception e) {
            System.err.println("[ERROR] File upload failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

        private String getFirstAvailableWorker() {
        return storageContainers.get(0);
    }

    private String getLeastLoadedWorker() {
        return storageContainers.stream()
            .min(Comparator.comparingInt(workerLoad::get))
            .orElse(storageContainers.get(0));
    }
    
    private synchronized String getNextRoundRobinWorker() {
        String worker = storageContainers.get(roundRobinIndex);
        roundRobinIndex = (roundRobinIndex + 1) % storageContainers.size();
        return worker;
    }
    
    

    public String getNextWorker() {
        if (storageContainers.isEmpty()) return null;
    
        switch (schedulingAlgorithm) {
            case FCFS:
                return getFirstAvailableWorker();
            case SJN:
                return getLeastLoadedWorker();
            case ROUND_ROBIN:
                return getNextRoundRobinWorker();
            default:
                return getFirstAvailableWorker();
        }
    }
        
    public void handleMessage(String topic, String message) {
        switch (topic) {
            case "loadbalancer/processing":
                markTaskAsProcessing(message);
                break;
            case "loadbalancer/completed":
                markTaskAsCompleted(message);
                break;
            case "loadbalancer/task/complete":
                System.out.println("[LoadBalancer] Task completion notification received: " + message);
                break;
            default:
                System.out.println("[LoadBalancer] Received unknown topic: " + topic);
                break;
        }
    }
    

    private String extractTaskId(String message) {
        try {
            return message.split("\"task_id\":\\s*\"")[1].split("\"")[0];
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to extract task ID: " + message);
            return null;
        }
    }

    private String extractWorkerId(String message) {
        try {
            return message.split("\"worker\":\\s*\"")[1].split("\"")[0];
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to extract worker ID: " + message);
            return null;
        }
    }

    private void markTaskAsProcessing(String message) {
        String taskId = extractTaskId(message);
        String worker = extractWorkerId(message);
        if (taskId != null && taskStates.containsKey(taskId)) {
            taskStates.put(taskId, "processing");
            taskProcessingTimestamps.put(taskId, System.currentTimeMillis());
    
            // ✅ Log task start in the database
            logTaskHistory(taskId, worker, "PROCESSING");
    
            System.out.println("[LoadBalancer] Task is now processing: " + taskId);
        }
    }
    

    private void checkForStuckTasks() {
        long currentTime = System.currentTimeMillis();
        long timeout = 60_000; // 60 seconds timeout for stuck tasks
    
        for (String taskId : new HashSet<>(taskProcessingTimestamps.keySet())) {
            long startTime = taskProcessingTimestamps.get(taskId);
            if (currentTime - startTime > timeout) {
                System.out.println("[WARNING] Task " + taskId + " is stuck. Reassigning...");
    
                // Reassign the task
                reassignTask(taskId);
            }
        }
    }

    private void reassignTask(String taskId) {
        String newWorker = getNextWorker();
        if (newWorker == null) {
            System.out.println("[ERROR] No available workers to reassign task: " + taskId);
            return;
        }
    
        // Construct a new task message
        String reassignedTaskJson = String.format("{\"task_id\": \"%s\", \"worker\": \"%s\", \"status\": \"RETRY\"}",
                taskId, newWorker);
    
        System.out.println("[LoadBalancer] Reassigning task " + taskId + " to " + newWorker);
        mqttClient.publishMessage("loadbalancer/waiting", reassignedTaskJson);
    
        // Update worker load tracking
        workerLoad.put(newWorker, workerLoad.getOrDefault(newWorker, 0) + 1);
        taskProcessingTimestamps.put(taskId, System.currentTimeMillis()); // Reset timer
    }
    
    private void markTaskAsCompleted(String message) {
        String taskId = extractTaskId(message);
        String worker = extractWorkerId(message);
        if (taskId != null && taskStates.containsKey(taskId)) {
            taskStates.remove(taskId);
            taskProcessingTimestamps.remove(taskId);
    
            // ✅ Log task completion in the database
            logTaskHistory(taskId, worker, "COMPLETED");
    
            System.out.println("[LoadBalancer] Task completed and removed: " + taskId);
        }
    }
    
    private void logTaskHistory(String taskId, String worker, String status) {
        try (Connection conn = DBConnection.getMySQLConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO TaskHistory (task_id, worker, status) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE status = ?, timestamp = CURRENT_TIMESTAMP")) {
            stmt.setString(1, taskId);
            stmt.setString(2, worker);
            stmt.setString(3, status);
            stmt.setString(4, status);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to log task history: " + e.getMessage());
        }
    }

    private void initializeContainers() {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            List<String> containers = Arrays.asList(
                "comp20081-files1",
                "comp20081-files2", 
                "comp20081-files3",
                "comp20081-files4"
            );
            
            storageContainers.addAll(containers);
            containers.forEach(container -> {
                try {
                    loadBalancerDB.addStorageContainer(container, 1000, conn);
                    containerLoad.put(container, 0);
                } catch (SQLException e) {
                    logger.log(SystemLogger.LogLevel.ERROR, "Failed to initialize container " + container);
                }
            });
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