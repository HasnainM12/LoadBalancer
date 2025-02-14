package com.mycompany.javafxapplication1;

import org.eclipse.paho.client.mqttv3.*;
import java.util.UUID;
import org.json.JSONObject;


public class MQTTClient {
    private static final String BROKER_URL = "tcp://broker.hivemq.com:1883";
    private final MqttClient client;
    private final String clientId;

    public MQTTClient(String prefix) {
        this.clientId = prefix + "-" + UUID.randomUUID().toString();
        try {
            client = new MqttClient(BROKER_URL, clientId);
            connect();
        } catch (MqttException e) {
            System.err.println("[ERROR] MQTT Client initialization failed: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void connect() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        client.connect(options);
        System.out.println("[MQTT] Connected: " + clientId);
    }

    public void publishTask(String status, String taskId, String operation, String filename) {
        try {
            JSONObject payload = new JSONObject()
                .put("taskId", taskId)
                .put("operation", operation)
                .put("filename", filename)
                .put("timestamp", System.currentTimeMillis());

            publish("task/" + status, payload);
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to publish task: " + e.getMessage());
        }
    }

    public void publish(String topic, JSONObject payload) {
        try {
            MqttMessage message = new MqttMessage(payload.toString().getBytes());
            message.setQos(1);
            client.publish(topic, message);
            System.out.println("[MQTT] Published to " + topic + ": " + payload);
        } catch (MqttException e) {
            System.err.println("[ERROR] Publish failed: " + e.getMessage());
        }
    }

    public void subscribe(String topic, IMqttMessageListener listener) {
        try {
            client.subscribe("task/" + topic, (t, message) -> {
                try {
                    listener.messageArrived(t, message);
                } catch (Exception e) {
                    System.err.println("[ERROR] Message handler failed: " + e.getMessage());
                }
            });
            System.out.println("[MQTT] Subscribed to task/" + topic);
        } catch (MqttException e) {
            System.err.println("[ERROR] Subscribe failed: " + e.getMessage());
        }
    }

    public void unsubscribe(String topic) {
        try {
            client.unsubscribe("task/" + topic);
            System.out.println("[MQTT] Unsubscribed from task/" + topic);
        } catch (MqttException e) {
            System.err.println("[ERROR] Unsubscribe failed: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (client.isConnected()) {
                client.disconnect();
                System.out.println("[MQTT] Disconnected: " + clientId);
            }
        } catch (MqttException e) {
            System.err.println("[ERROR] Disconnect failed: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public String getClientId() {
        return clientId;
    }
}
