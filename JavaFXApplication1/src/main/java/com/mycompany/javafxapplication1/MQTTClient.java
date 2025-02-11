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
}
