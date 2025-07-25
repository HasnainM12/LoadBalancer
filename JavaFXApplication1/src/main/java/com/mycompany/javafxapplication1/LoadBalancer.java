package com.mycompany.javafxapplication1;

import com.mycompany.javafxapplication1.Session;
import java.io.IOException;
import java.nio.file.Files;
import java.io.File;
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
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.mycompany.javafxapplication1.FileOperation;




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
    private static MQTTClient mqttClient;
    
    // Task tracking
    private final Map<String, TaskState> taskStates = new ConcurrentHashMap<>();
    private final Map<String, Long> taskTimestamps = new ConcurrentHashMap<>();
    private final Map<String, Long> taskProcessingTimestamps = new ConcurrentHashMap<>();
    private final Map<String, String> taskToContainerMap = new ConcurrentHashMap<>();

    
    // Container management  
    private final Map<String, Integer> workerLoad = new ConcurrentHashMap<>();
    private final Map<String, Boolean> containerHealth = new ConcurrentHashMap<>();
    private int roundRobinIndex = 0;

   private static final int MAX_CONTAINER_LOAD = 100;
   private static final int TASK_TIMEOUT_SECONDS = 60;
   private static final int HEALTH_CHECK_INTERVAL = 30; // Check every 30 seconds
   private static final int MONITORING_INTERVAL = 10;


 
    public enum TaskState {
        WAITING,
        PROCESSING, 
        COMPLETED,
        ERROR
    }
    
    public enum SchedulingAlgorithm {
        FCFS, SJN, ROUND_ROBIN
    }
    
    private LoadBalancer() {
        requestQueue = new ConcurrentLinkedQueue<>();
        loadBalancerDB = new LoadBalancerDB();
        storageContainers = Collections.synchronizedList(new ArrayList<>());
        containerLoad = new ConcurrentHashMap<>();
        executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        
        // Initialize MQTT
        if (mqttClient == null) {
            mqttClient = new MQTTClient("LoadBalancer");
            mqttClient.subscribe("task/+", this::handleTaskMessage);
        }
        
        initializeContainers();
        startRequestProcessor();
        startTaskMonitor();
        startHealthChecks();
        startStuckTaskMonitor();
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
        scheduler.scheduleAtFixedRate(this::checkTaskTimeouts, 
            MONITORING_INTERVAL, MONITORING_INTERVAL, TimeUnit.SECONDS);
    }
     
    
    public void submitTask(JSONObject taskData) {
        String taskId = taskData.getString("taskId");
        String operation = taskData.getString("operation");
        String filename = taskData.getString("filename");
    
        FileOperation.OperationType opType;
        try {
            opType = FileOperation.OperationType.valueOf(operation);
        } catch (IllegalArgumentException e) {
            System.err.println("[ERROR] Invalid operation type: " + operation);
            return;
        }
    
        // Extract file size if available
        long fileSize = taskData.has("size") ? taskData.getLong("size") : 0;
    
        FileOperation fileOp = new FileOperation(filename, opType, fileSize);
        if (taskData.has("filePath")) {
            fileOp.setFilePath(taskData.getString("filePath"));
        }
        if (taskData.has("content")) {
            fileOp.setContent(taskData.getString("content"));
        }
    
        taskStates.put(taskId, TaskState.WAITING);
        taskTimestamps.put(taskId, System.currentTimeMillis());
    
        try {
            mqttClient.publishTask("waiting", taskId, operation, filename);
            logger.log(SystemLogger.LogLevel.INFO, "Task submitted: " + taskId);
    
            switch (opType) {
                case UPLOAD:
                    processFileUpload(fileOp, taskId);
                    break;
                case DOWNLOAD:
                    processFileDownload(fileOp, taskId);
                    break;
                case DELETE:
                    processFileDelete(fileOp, taskId);
                    break;
                case READ:
                    processFileRead(fileOp, taskId);
                    break;
                case WRITE:
                    processFileWrite(fileOp, taskId);
                    break;
                default:
                    logger.log(SystemLogger.LogLevel.ERROR, "Unknown operation: " + operation);
            }
    
        } catch (Exception e) {
            handleTaskError(taskId, e);
        }
    }
    
    

     private void handleTaskMessage(String topic, MqttMessage message) {
        try {
            JSONObject taskUpdate = new JSONObject(new String(message.getPayload()));
            String taskId = taskUpdate.getString("taskId");
            String status = topic.split("/")[1];
            
            switch(status) {
                case "processing":
                    taskStates.put(taskId, TaskState.PROCESSING);
                    break;
                case "completed":
                    handleTaskCompletion(taskId);
                    break;
                case "error":
                    handleTaskError(taskId, new Exception(taskUpdate.getString("error")));
                    break;
            }
        } catch (Exception e) {
            logger.log(SystemLogger.LogLevel.ERROR, "Failed to process message: " + e.getMessage());
        }
    }
     
    public void handleTaskCompletion(String taskId) {
        // Remove task tracking info
        taskStates.remove(taskId);
        taskTimestamps.remove(taskId);
        
        // Get the container that was processing this task
        String container = taskToContainerMap.remove(taskId);
        
        if (container != null) {
            // Properly decrement the load counter for this container
            workerLoad.computeIfPresent(container, (k, v) -> Math.max(0, v - 1));
            logger.log(SystemLogger.LogLevel.INFO, "Task completed: " + taskId + " on container: " + container);
        } else {
            logger.log(SystemLogger.LogLevel.INFO, "Task completed: " + taskId);
        }
        
        // Clean up any timestamp information
        taskProcessingTimestamps.remove(taskId);
    }
     
    public void handleTaskError(String taskId, Exception error) {
        taskStates.put(taskId, TaskState.ERROR);
        logger.log(SystemLogger.LogLevel.ERROR, "Task failed: " + taskId + " - " + error.getMessage());
        
        // Get and remove container for this task
        String container = taskToContainerMap.remove(taskId);
        if (container != null) {
            // Decrement the worker load
            workerLoad.computeIfPresent(container, (k, v) -> Math.max(0, v - 1));
        }
        
        try {
            JSONObject errorMsg = new JSONObject()
                .put("taskId", taskId)
                .put("error", error.getMessage());
            mqttClient.publish("task/error", errorMsg);
        } catch (Exception e) {
            logger.log(SystemLogger.LogLevel.ERROR, "Failed to publish error: " + e.getMessage());
        }
    }

     private void checkTaskTimeouts() {
        long currentTime = System.currentTimeMillis();
        long timeout = TASK_TIMEOUT_SECONDS * 1000;
 
        taskTimestamps.forEach((taskId, startTime) -> {
            if (currentTime - startTime > timeout) {
                TaskState state = taskStates.get(taskId);
                if (state == TaskState.PROCESSING || state == TaskState.WAITING) {
                    logger.log(SystemLogger.LogLevel.WARN, "Task " + taskId + " timed out");
                    retryTask(taskId);
                }
            }
        });
    }

     private void retryTask(String taskId) {
        String newWorker = getNextWorker();
        if (newWorker == null) {
            handleTaskError(taskId, new Exception("No available workers"));
            return;
        }
     
        try {
            JSONObject retryMsg = new JSONObject()
                .put("taskId", taskId)
                .put("worker", newWorker)
                .put("status", "RETRY");
                
            mqttClient.publish("task/waiting", retryMsg); //  Pass JSONObject directly
            taskTimestamps.put(taskId, System.currentTimeMillis());
            workerLoad.put(newWorker, workerLoad.getOrDefault(newWorker, 0) + 1);
            
        } catch (Exception e) {
            handleTaskError(taskId, e);
        }
    }

    private long calculateDelay(FileOperation.OperationType operationType) {
        // Base delay between 30-90 seconds
        int baseDelay = 30;
        int variableDelay = new Random().nextInt(61); // 0-60 additional seconds
        return (baseDelay + variableDelay) * 1000L; // Convert to milliseconds
    }

    private void processFileDelete(FileOperation operation, String taskId) {
        FileDB fileDB = new FileDB();
        try {
            Long fileId = fileDB.getFileIdByFilename(operation.getFilename());
            if (fileId == null) {
                throw new Exception("File ID not found for filename: " + operation.getFilename());
            }
    
            // Mark task as "PROCESSING"
            taskStates.put(taskId, TaskState.PROCESSING);
            mqttClient.publishTask("processing", taskId, "DELETE", operation.getFilename());
    
            if (fileDB.deleteFile(fileId)) {
                taskStates.put(taskId, TaskState.COMPLETED);
                mqttClient.publishTask("completed", taskId, "DELETE", operation.getFilename());
            } else {
                throw new Exception("Delete failed");
            }
        } catch (Exception e) {
            System.err.println("[ERROR] File deletion failed: " + e.getMessage());
            taskStates.put(taskId, TaskState.ERROR);
            mqttClient.publishTask("failed", taskId, "DELETE", operation.getFilename());
            handleTaskError(taskId, e);
        }
    }
    
    private void processFileDownload(FileOperation operation, String taskId) {
        FileDB fileDB = new FileDB();
        try {
            Long fileId = fileDB.getFileIdByFilename(operation.getFilename());
            if (fileId == null) {
                throw new Exception("File ID not found for filename: " + operation.getFilename());
            }
    
            // Mark task as "PROCESSING"
            taskStates.put(taskId, TaskState.PROCESSING);
            mqttClient.publishTask("processing", taskId, "DOWNLOAD", operation.getFilename());
    
            // Create downloads directory if it doesn't exist
            File downloadsDir = new File("downloads");
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }
    
            String localPath = "downloads/" + operation.getFilename();
            if (fileDB.downloadFile(fileId, localPath)) {
                taskStates.put(taskId, TaskState.COMPLETED);
                mqttClient.publishTask("completed", taskId, "DOWNLOAD", operation.getFilename());
            } else {
                throw new Exception("Download failed");
            }
        } catch (Exception e) {
            System.err.println("[ERROR] File download failed: " + e.getMessage());
            taskStates.put(taskId, TaskState.ERROR);
            mqttClient.publishTask("failed", taskId, "DOWNLOAD", operation.getFilename());
            handleTaskError(taskId, e);
        }
    }
    
    private void processFileUpload(FileOperation operation, String taskId) {
        String container = getNextWorker(); // Get next storage container
    
        if (container == null) {
            System.err.println("[ERROR] No available storage containers!");
            mqttClient.publishTask("failed", taskId, "UPLOAD", operation.getFilename());
            return;
        }
        taskToContainerMap.put(taskId, container);
        workerLoad.put(container, workerLoad.getOrDefault(container, 0) + 1);
        long delay = calculateDelay(operation.getType());
        taskProcessingTimestamps.put(taskId, System.currentTimeMillis() + delay);
    
        // Map container names to their hostnames in the Docker network
        Map<String, String> containerHosts = Map.of(
            "comp20081-files1", "comp20081-files1",
            "comp20081-files2", "comp20081-files2",
            "comp20081-files3", "comp20081-files3",
            "comp20081-files4", "comp20081-files4"
        );
    
        System.out.println("[DEBUG] Selected container: " + container);
        System.out.println("[DEBUG] Filename: " + operation.getFilename());
        System.out.println("[DEBUG] File path: " + operation.getFilePath());
    
        // Mark task as "PROCESSING"
        taskStates.put(taskId, TaskState.PROCESSING);
        mqttClient.publishTask("processing", taskId, "UPLOAD", operation.getFilename());
    
        try {
            Thread.sleep(delay);
    
            // Establish SFTP connection
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
    
            // Upload the file
            String remoteFilePath = "/files/" + operation.getFilename();
            sftpChannel.put(operation.getFilePath(), remoteFilePath);
    
            // Store metadata in DB
            FileDB fileDB = new FileDB();
            Long fileId = fileDB.addFileMetadata(
                operation.getFilename(),
                Session.getInstance().getUsername(),
                container + ":" + remoteFilePath
            );
    
            System.out.println("[INFO] File uploaded to container " + container + " with ID: " + fileId);
    
            // Mark task as "COMPLETED"
            taskStates.put(taskId, TaskState.COMPLETED);
            mqttClient.publishTask("completed", taskId, "UPLOAD", operation.getFilename());
            logger.log(SystemLogger.LogLevel.INFO, "File uploaded successfully for task " + taskId);
    
            // Cleanup
            sftpChannel.exit();
            sshSession.disconnect();
    
        } catch (Exception e) {
            // Handle failures and retries
            System.err.println("[ERROR] File upload failed: " + e.getMessage());
            e.printStackTrace();
    
            // Mark task as "FAILED" and notify MQTT
            taskStates.put(taskId, TaskState.ERROR);
            mqttClient.publishTask("failed", taskId, "UPLOAD", operation.getFilename());
            handleTaskError(taskId, e);
        }
    }

    private void processFileRead(FileOperation operation, String taskId) {
        FileDB fileDB = new FileDB();
        try {
            Long fileId = fileDB.getFileIdByFilename(operation.getFilename());
            if (fileId == null) {
                throw new Exception("File ID not found for filename: " + operation.getFilename());
            }
    
            String containerPath = fileDB.getFilePath(fileId);
            if (containerPath == null) {
                throw new Exception("File path not found: " + operation.getFilename());
            }
    
            // Mark task as processing
            taskStates.put(taskId, TaskState.PROCESSING);
            mqttClient.publishTask("processing", taskId, "READ", operation.getFilename());
    
            // Get file content
            String content = fileDB.getFileContent(containerPath);
            if (content != null) {
                taskStates.put(taskId, TaskState.COMPLETED);
                mqttClient.publishTask("completed", taskId, "READ", operation.getFilename());
            } else {
                throw new Exception("Failed to read file content");
            }
    
        } catch (Exception e) {
            System.err.println("[ERROR] File read failed: " + e.getMessage());
            taskStates.put(taskId, TaskState.ERROR);
            mqttClient.publishTask("failed", taskId, "READ", operation.getFilename());
            handleTaskError(taskId, e);
        }
    }

    private void processFileWrite(FileOperation operation, String taskId) {
        FileDB fileDB = new FileDB();
        com.mycompany.javafxapplication1.Session userSession = com.mycompany.javafxapplication1.Session.getInstance();
        
        try {
            // Update session activity timestamp to prevent expiration
            userSession.updateLastActivity();
            // validate session
            if (!userSession.isValid()) {
                throw new Exception("Invalid session or session expired");
            }
    
            // Get file ID and check existence
            Long fileId = fileDB.getFileIdByFilename(operation.getFilename());
            if (fileId == null) {
                throw new Exception("File ID not found for filename: " + operation.getFilename());
            }
    
            // Check write permissions for current user
            FileDB.FilePermission permissions = fileDB.getFilePermissions(fileId, userSession.getUsername());
            if (!permissions.canWrite()) {
                throw new Exception("User does not have write permission for this file");
            }
    
            String containerPath = fileDB.getFilePath(fileId);
            if (containerPath == null) {
                throw new Exception("File path not found: " + operation.getFilename());
            }
    
            // Mark task as "PROCESSING"
            taskStates.put(taskId, TaskState.PROCESSING);
            mqttClient.publishTask("processing", taskId, "WRITE", operation.getFilename());
    
            String[] pathParts = containerPath.split(":");
            if (pathParts.length != 2) {
                throw new Exception("Invalid file path format");
            }
    
            String container = pathParts[0];
            String remotePath = pathParts[1];
    
            // Create a temporary file with the new content
            File tempFile = File.createTempFile("write", ".tmp");
            Files.write(tempFile.toPath(), operation.getContent().getBytes());
    
            // Upload the temporary file via SFTP
            JSch jsch = new JSch();
            com.jcraft.jsch.Session sshSession = jsch.getSession("ntu-user", container, 22);
            sshSession.setPassword("ntu-user");
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.connect(5000);
    
            Channel channel = sshSession.openChannel("sftp");
            channel.connect(5000);
            ChannelSftp sftpChannel = (ChannelSftp) channel;
    
            try {
                // Upload the new content
                sftpChannel.put(tempFile.getAbsolutePath(), remotePath);
                
                // Update session activity timestamp
                userSession.updateLastActivity();
                
                taskStates.put(taskId, TaskState.COMPLETED);
                mqttClient.publishTask("completed", taskId, "WRITE", operation.getFilename());
                logger.log(SystemLogger.LogLevel.INFO, 
                          "File successfully written: " + operation.getFilename() + 
                          " by user: " + userSession.getUsername());
            } finally {
                sftpChannel.exit();
                sshSession.disconnect();
                tempFile.delete();  // Clean up temporary file
            }
    
        } catch (Exception e) {
            System.err.println("[ERROR] File write failed: " + e.getMessage());
            taskStates.put(taskId, TaskState.ERROR);
            mqttClient.publishTask("failed", taskId, "WRITE", operation.getFilename());
            handleTaskError(taskId, e);
        }
    }

    private String getNextWorker() {
        List<String> healthyContainers = storageContainers.stream()
            .filter(container -> containerHealth.getOrDefault(container, false))
            .collect(Collectors.toList());
    
        if (healthyContainers.isEmpty()) {
            logger.log(SystemLogger.LogLevel.ERROR, "No healthy containers available");
            return null;
        }
    
        switch (schedulingAlgorithm) {
            case FCFS:
                return healthyContainers.get(0); // First-come, first-served
    
            case SJN:
                return getLeastLoadedWorker(healthyContainers); // Uses least loaded worker
    
            case ROUND_ROBIN:
                return getNextRoundRobinContainer(); // Uses round-robin
    
            default:
                logger.log(SystemLogger.LogLevel.WARN, "Unknown scheduling algorithm, defaulting to FCFS");
                return healthyContainers.get(0);
        }
    }
    
    

    private int getQueuedTaskCount(String container) {
        return (int) taskStates.entrySet().stream()
            .filter(entry -> entry.getValue().equals("queued") && 
                    entry.getKey().startsWith(container))
            .count();
    }

    public Map<String, TaskState> getActiveTasks() {
        return taskStates.entrySet().stream()
            .filter(e -> isTaskActive(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public boolean isTaskActive(String taskId) {
        TaskState state = taskStates.get(taskId);
        return state == TaskState.WAITING || state == TaskState.PROCESSING;
    }
    
    private String getLeastLoadedWorker(List<String> healthyContainers) {
        return healthyContainers.stream()
            .min(Comparator.comparingInt(container -> 
                workerLoad.getOrDefault(container, 0))) // Use workerLoad, not just containerLoad
            .orElse(null);
    }
    

    
    public void handleMessage(String topic, String message) {
        switch (topic) {
            case "task/processing":
                markTaskAsProcessing(message);
                break;
            case "task/completed":
                markTaskAsCompleted(message);
                break;
            case "task/complete": 
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
            taskStates.put(taskId, TaskState.PROCESSING);
            taskProcessingTimestamps.put(taskId, System.currentTimeMillis());
    
            //Log task start in the database
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
    
                //  Reassign the task
                reassignTask(taskId);
            }
        }
    }
    

    private void startStuckTaskMonitor() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(this::checkForStuckTasks, 30, 30, TimeUnit.SECONDS);
    }
    

    private void reassignTask(String taskId) {
        String newWorker = getNextWorker();
        if (newWorker == null) {
            System.out.println("[ERROR] No available workers to reassign task: " + taskId);
            return;
        }
    
        // Create JSON object correctly
        JSONObject reassignedTask = new JSONObject()
            .put("taskId", taskId)
            .put("worker", newWorker)
            .put("status", "RETRY");
    
        System.out.println("[LoadBalancer] Reassigning task " + taskId + " to " + newWorker);
    
        // Publish to the correct MQTT topic
        mqttClient.publish("task/retry", reassignedTask);
    
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
    
            //  Log task completion in the database
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

    //HEALTH CONTAINER

    private void startHealthChecks() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this::checkContainerHealth, 0, HEALTH_CHECK_INTERVAL, TimeUnit.SECONDS);
    }
    
    private void checkContainerHealth() {
        for (String container : storageContainers) {
            boolean wasHealthy = containerHealth.getOrDefault(container, true);
            boolean isHealthy = isContainerHealthy(container);
            containerHealth.put(container, isHealthy);
            
            if (wasHealthy && !isHealthy) {
                System.err.println("[WARNING] Container " + container + " went offline!");
            } else if (!wasHealthy && isHealthy) {
                System.out.println("[INFO] Container " + container + " is back online!");
            }
        }
    }
    
    private boolean isContainerHealthy(String container) {
        try {
            JSch jsch = new JSch();
            com.jcraft.jsch.Session sshSession = jsch.getSession("ntu-user", container, 22);
            sshSession.setPassword("ntu-user");
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.connect(5000);
            
            Channel channel = sshSession.openChannel("exec");
            ((ChannelExec) channel).setCommand("ls /files");
            channel.connect();
            
            boolean success = channel.isConnected();
            channel.disconnect();
            sshSession.disconnect();
            
            return success;
        } catch (Exception e) {
            return false;
        }
    }

    private String processOperation(FileOperation operation) {
        String container = getNextAvailableContainer();
        logger.log(SystemLogger.LogLevel.INFO, "Container " + container + " selected for operation: " + operation.getType());
        
        try (Connection conn = DBConnection.getMySQLConnection()) {
            // Increment the load for this container
            workerLoad.put(container, workerLoad.getOrDefault(container, 0) + 1);
            
            Thread.sleep(operation.getEstimatedProcessingTime());
            loadBalancerDB.logOperation(container, operation.getType().name(), conn);
            return container;
        } catch (Exception e) {
            logger.log(SystemLogger.LogLevel.ERROR, "Operation processing failed: " + e.getMessage());
            throw new RuntimeException("Operation processing failed", e);
        } finally {
            // Decrement the load when operation completes or fails
            workerLoad.computeIfPresent(container, (k, v) -> Math.max(0, v - 1));
        }
    }

    private String getNextAvailableContainer() {
        if (storageContainers.isEmpty()) {
            throw new IllegalStateException("No storage containers available");
        }
    
        List<String> healthyContainers = storageContainers.stream()
            .filter(container -> containerHealth.getOrDefault(container, false))
            .collect(Collectors.toList());
            
        if (healthyContainers.isEmpty()) {
            logger.log(SystemLogger.LogLevel.WARN, "No healthy containers available, using first container");
            return storageContainers.get(0);
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
            .filter(container -> containerHealth.getOrDefault(container, false))
            .min(Comparator.comparingInt(container -> 
                workerLoad.getOrDefault(container, 0)))
            .orElse(storageContainers.get(0));
    }


    private synchronized String getNextRoundRobinContainer() {
        List<String> healthyContainers = storageContainers.stream()
            .filter(container -> containerHealth.getOrDefault(container, false))
            .collect(Collectors.toList());
            
        if (healthyContainers.isEmpty()) {
            logger.log(SystemLogger.LogLevel.WARN, "No healthy containers available, using first container");
            return storageContainers.get(0);
        }
        
        // Reset index if it's out of bounds
        if (roundRobinIndex >= healthyContainers.size()) {
            roundRobinIndex = 0;
        }
        
        String container = healthyContainers.get(roundRobinIndex);
        roundRobinIndex = (roundRobinIndex + 1) % healthyContainers.size();
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

        mqttClient.disconnect();
        requestQueue.clear();
        taskStates.clear();
        taskTimestamps.clear();
        instance = null;
    }

}