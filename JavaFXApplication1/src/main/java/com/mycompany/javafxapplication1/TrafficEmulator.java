package com.mycompany.javafxapplication1;

import org.eclipse.paho.client.mqttv3.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;


public class TrafficEmulator {
   private final Queue<Task> taskQueue = new ConcurrentLinkedQueue<>();
   private final MqttClient mqttClient;
   private final ExecutorService executor;
   private final Map<String, TaskState> taskStates = new ConcurrentHashMap<>();
   private final Map<String, Integer> retryCount = new ConcurrentHashMap<>();
   private volatile boolean running = true;
   private static final int MAX_RETRIES = 3;
   private static final String BROKER_URL = "tcp://broker.hivemq.com:1883";

   private enum TaskState {
       QUEUED,
       PROCESSING,
       COMPLETED,
       FAILED
   }

   public TrafficEmulator() throws MqttException {
       executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
       mqttClient = new MqttClient(BROKER_URL, "TrafficEmulator-" + UUID.randomUUID());
       connect();
       subscribeToTasks();
       startTaskProcessor();
   }

   private void connect() throws MqttException {
       MqttConnectOptions options = new MqttConnectOptions();
       options.setCleanSession(true);
       options.setAutomaticReconnect(true);
       mqttClient.connect(options);
       System.out.println("[TrafficEmulator] Connected to MQTT broker");
   }

   private void subscribeToTasks() throws MqttException {
       mqttClient.subscribe("task/waiting", (topic, message) -> {
           try {
               JSONObject task = new JSONObject(new String(message.getPayload()));
               handleNewTask(task);
           } catch (Exception e) {
               System.err.println("[ERROR] Failed to process task: " + e.getMessage());
           }
       });
   }

   public void addTask(Task task) {
    taskQueue.offer(task);
}


    private void handleNewTask(JSONObject taskData) {
        String taskId = taskData.getString("taskId");
        System.out.println("[DEBUG] Received Task: " + taskData.toString());

        if (taskStates.containsKey(taskId)) {
            System.out.println("[DEBUG] Task " + taskId + " is already queued.");
            return; // ✅ Prevent duplicate processing
        }

        taskStates.put(taskId, TaskState.QUEUED);
        retryCount.put(taskId, 0);

        Task task = new Task(
            taskId,
            taskData.getString("operation"),
            calculateDelay(taskData.getString("operation"))
        );

        taskQueue.offer(task);
        publishTaskStatus(taskId, "queued");
    }


   private long calculateDelay(String operation) {
       int baseDelay = 30;
       int variableDelay = new Random().nextInt(61);
       return (baseDelay + variableDelay) * 1000L;
   }

    private void startTaskProcessor() {
    executor.submit(() -> {
        while (running) {
            try {
                Task task = taskQueue.poll();
                if (task != null) {
                    processTask(task);
                } else {
                    Thread.sleep(500); //  Increase sleep time to avoid excessive CPU usage
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    });
    }




   private void processTask(Task task) {
    try {
        if (!mqttClient.isConnected()) {
            System.err.println("[ERROR] TrafficEmulator not connected to MQTT! Retrying...");
            connect();
            return;
        }

        taskStates.put(task.getTaskId(), TaskState.PROCESSING);
        publishTaskStatus(task.getTaskId(), "processing");

        Thread.sleep(task.getDelay());

        if (new Random().nextDouble() < 0.9) { // 90% success rate
            completeTask(task);
        } else {
            failTask(task);
        }
    } catch (Exception e) {
        handleTaskError(task, e);
    }
}


   private void completeTask(Task task) {
       taskStates.put(task.getTaskId(), TaskState.COMPLETED);
       publishTaskStatus(task.getTaskId(), "completed");
       cleanup(task.getTaskId());
   }

   private void failTask(Task task) {
       int attempts = retryCount.getOrDefault(task.getTaskId(), 0);
       if (attempts < MAX_RETRIES) {
           retryCount.put(task.getTaskId(), attempts + 1);
           taskQueue.offer(task);
           publishTaskStatus(task.getTaskId(), "retry");
       } else {
           taskStates.put(task.getTaskId(), TaskState.FAILED);
           publishTaskStatus(task.getTaskId(), "failed");
           cleanup(task.getTaskId());
       }
   }

   private void handleTaskError(Task task, Exception error) {
       System.err.println("[ERROR] Task failed: " + task.getTaskId() + " - " + error.getMessage());
       failTask(task);
   }

   private void publishTaskStatus(String taskId, String status) {
       try {
           JSONObject statusUpdate = new JSONObject()
               .put("taskId", taskId)
               .put("status", status)
               .put("timestamp", System.currentTimeMillis());

           mqttClient.publish("task/" + status,
               new MqttMessage(statusUpdate.toString().getBytes()));
       } catch (Exception e) {
           System.err.println("[ERROR] Failed to publish status: " + e.getMessage());
       }
   }

   private void cleanup(String taskId) {
       taskStates.remove(taskId);
       retryCount.remove(taskId);
   }

   public void shutdown() {
       running = false;
       executor.shutdown();
       try {
           if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
               executor.shutdownNow();
           }
           if (mqttClient.isConnected()) {
               mqttClient.disconnect();
           }
       } catch (Exception e) {
           executor.shutdownNow();
           Thread.currentThread().interrupt();
       }
   }

   public static class Task {
       private final String taskId;
       private final String operation;
       private final long delay;

       public Task(String taskId, String operation, long delay) {
           this.taskId = taskId;
           this.operation = operation;
           this.delay = delay;
       }

       public String getTaskId() { return taskId; }
       public String getOperation() { return operation; }
       public long getDelay() { return delay; }
   }
}