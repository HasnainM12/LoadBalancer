package com.mycompany.javafxapplication1;

import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;

public class TrafficEmulationSubscriber {
   private static final String BROKER = "tcp://mqtt-broker:1883";
   private static final String CLIENT_ID = "TrafficEmulatorSubscriber";
   private final LoadBalancer loadBalancer;
   private final TrafficEmulator trafficEmulator;
   private final MqttClient mqttClient;

   public TrafficEmulationSubscriber(LoadBalancer loadBalancer, TrafficEmulator trafficEmulator) {
       this.loadBalancer = loadBalancer;
       this.trafficEmulator = trafficEmulator;
       this.mqttClient = initializeMQTT();
   }

   private MqttClient initializeMQTT() {
       try {
           MqttClient client = new MqttClient(BROKER, CLIENT_ID + "-" + System.currentTimeMillis());
           MqttConnectOptions options = new MqttConnectOptions();
           options.setCleanSession(true);
           options.setAutomaticReconnect(true);
           
           client.setCallback(createCallback());
           client.connect(options);
           client.subscribe("task/#", 1);
           
           return client;
       } catch (MqttException e) {
           throw new RuntimeException("Failed to initialize MQTT", e);
       }
   }

   private MqttCallback createCallback() {
       return new MqttCallback() {
           @Override
           public void connectionLost(Throwable cause) {
               System.err.println("[MQTT] Connection lost: " + cause.getMessage());
               attemptReconnect();
           }

           @Override
           public void messageArrived(String topic, MqttMessage message) {
               handleMessage(topic, message);
           }

           @Override
           public void deliveryComplete(IMqttDeliveryToken token) {
               // Not needed for subscriber
           }
       };
   }

   private void handleMessage(String topic, MqttMessage message) {
       try {
           JSONObject payload = new JSONObject(new String(message.getPayload()));
           String taskId = payload.getString("taskId");
           
           switch (topic) {
               case "task/waiting":
                   trafficEmulator.addTask(new TrafficEmulator.Task(
                       taskId,
                       payload.getString("operation"),
                       payload.optLong("delay", 30000)
                   ));
                   break;
                   
               case "task/completed":
                   loadBalancer.handleTaskCompletion(taskId);
                   break;
                   
               case "task/failed":
                   loadBalancer.handleTaskError(taskId, 
                       new Exception(payload.optString("error", "Unknown error")));
                   break;
           }
       } catch (Exception e) {
           System.err.println("[ERROR] Failed to process message: " + e.getMessage());
       }
   }

   private void attemptReconnect() {
       try {
           if (!mqttClient.isConnected()) {
               mqttClient.reconnect();
               mqttClient.subscribe("task/#", 1);
           }
       } catch (MqttException e) {
           System.err.println("[ERROR] Reconnection failed: " + e.getMessage());
       }
   }

   public void shutdown() {
       try {
           if (mqttClient != null && mqttClient.isConnected()) {
               mqttClient.disconnect();
           }
       } catch (MqttException e) {
           System.err.println("[ERROR] Shutdown failed: " + e.getMessage());
       }
   }
}