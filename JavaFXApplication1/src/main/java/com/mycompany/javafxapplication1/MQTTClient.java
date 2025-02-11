package com.mycompany.javafxapplication1;

import org.eclipse.paho.client.mqttv3.*;

public class MQTTClient {
    private static final String BROKER_URL = "tcp://broker.hivemq.com:1883";
    private static final String CLIENT_ID = "LoadBalancerClient";
    private MqttClient client;

    public MQTTClient() {
        try {
            client = new MqttClient(BROKER_URL, CLIENT_ID);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            client.connect(options);
            System.out.println("[MQTT] Connected to broker: " + BROKER_URL);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publishMessage(String topic, String payload) {
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1); // QoS 1: Ensures message is delivered at least once
            client.publish(topic, message);
            System.out.println("[MQTT] Published to " + topic + ": " + payload);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
    public void subscribeToTopic(String topic) {
        try {
            client.subscribe(topic, (receivedTopic, message) -> {
                String payload = new String(message.getPayload());
                System.out.println("[MQTT] Message received on " + receivedTopic + ": " + payload);
                handleMessage(receivedTopic, payload);
            });
    
            System.out.println("[MQTT] Subscribed to: " + topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
    
    private void handleMessage(String topic, String message) {
        if (topic.equals("loadbalancer/processing")) {
            System.out.println("[LoadBalancer] Task is now processing: " + message);
        } else if (topic.equals("loadbalancer/completed")) {
            System.out.println("[LoadBalancer] Task completed: " + message);
        }
    }
    
    
}


