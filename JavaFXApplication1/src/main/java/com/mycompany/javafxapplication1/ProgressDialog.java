package com.mycompany.javafxapplication1;

import javafx.scene.control.Dialog;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.scene.control.ButtonType;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import javafx.application.Platform;
import javafx.scene.Node;
import org.json.JSONObject;
import javafx.animation.PauseTransition;
import javafx.util.Duration;


public class ProgressDialog extends Dialog<Void> {
    private final ProgressBar progressBar;
    private final Text statusText;
    private Node closeButton;
    private MQTTClient mqttClient;
    private String taskId;
    private boolean autoClose = false;

    public ProgressDialog(String operation) {
        setTitle("Operation in Progress");
        initModality(Modality.APPLICATION_MODAL);
        
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        
        statusText = new Text("Initializing " + operation);
        
        VBox content = new VBox(10);
        content.getChildren().addAll(statusText, progressBar);
        getDialogPane().setContent(content);
        
        getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        closeButton = getDialogPane().lookupButton(ButtonType.CANCEL);
        closeButton.setDisable(true);

        mqttClient = new MQTTClient("ProgressDialog-" + UUID.randomUUID().toString());
    }

    public void trackProgress(String taskId) {
        this.taskId = taskId;
        progressBar.setProgress(0);
        
        mqttClient.subscribe("waiting", (topic, msg) -> handleTaskUpdate(msg, 0.25, "Waiting..."));
        mqttClient.subscribe("processing", (topic, msg) -> handleTaskUpdate(msg, 0.5, "Processing..."));
        mqttClient.subscribe("retry", (topic, msg) -> handleTaskUpdate(msg, 0.25, "Retrying..."));
        mqttClient.subscribe("completed", (topic, msg) -> handleTaskUpdate(msg, 1.0, "Completed"));
        mqttClient.subscribe("failed", (topic, msg) -> handleTaskError(msg));
    }

    private void handleTaskUpdate(MqttMessage message, double progress, String status) {
        try {
            JSONObject taskUpdate = new JSONObject(new String(message.getPayload()));
            if (taskUpdate.getString("taskId").equals(taskId)) {
                Platform.runLater(() -> {
                    progressBar.setProgress(progress);
                    statusText.setText(status);
                    
                    if (progress >= 1.0) {
                        handleCompletion();
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to process update: " + e.getMessage());
        }
    }

    private void handleTaskError(MqttMessage message) {
        try {
            JSONObject taskUpdate = new JSONObject(new String(message.getPayload()));
            if (taskUpdate.getString("taskId").equals(taskId)) {
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    statusText.setText("Failed: " + taskUpdate.optString("error", "Unknown error"));
                    closeButton.setDisable(false);
                });
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to process error: " + e.getMessage());
        }
    }

    private void handleCompletion() {
        closeButton.setDisable(false);
    
        if (autoClose) {
            PauseTransition pause = new PauseTransition(Duration.seconds(1));
            pause.setOnFinished(event -> close());
            pause.play();
        }
    }
    
    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }

    public void shutdown() {
        if (mqttClient != null) {
            mqttClient.unsubscribe("task/waiting");
            mqttClient.unsubscribe("task/processing");
            mqttClient.unsubscribe("task/retry");
            mqttClient.unsubscribe("task/completed");
            mqttClient.unsubscribe("task/failed");
            mqttClient.disconnect();
        }
        super.setResult(null); // Close the dialog safely
    }
    
}
