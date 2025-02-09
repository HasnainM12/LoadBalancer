package com.mycompany.javafxapplication1;

import javafx.scene.control.Dialog;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.scene.control.ButtonType;
import javafx.application.Platform;
import javafx.scene.Node;


// Update ProgressDialog.java
public class ProgressDialog extends Dialog<Void> {
    private final ProgressBar progressBar;
    private final Text statusText;
    private Node closeButton;

    
    public ProgressDialog(String operation) {
        setTitle("Operation in Progress");
        initModality(Modality.APPLICATION_MODAL);
    
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
    
        statusText = new Text("Processing: " + operation);
    
        VBox content = new VBox(10);
        content.getChildren().addAll(statusText, progressBar);
        getDialogPane().setContent(content);
    
        // ✅ Add close button but keep it hidden initially
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        closeButton = getDialogPane().lookupButton(ButtonType.CLOSE);
        closeButton.setVisible(false);
    }
    
    
    public void bindProgress(DelayManager delayManager) {
        progressBar.progressProperty().bind(delayManager.progressProperty());
        
        // ✅ Unhide close button when progress reaches 100%
        delayManager.progressProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() >= 1.0 && closeButton != null) {
                closeButton.setVisible(true);
            }
        });
    }
    

    public void autoCloseOnCompletion() {
    progressBar.progressProperty().addListener((obs, oldVal, newVal) -> {
        if (newVal.doubleValue() >= 1.0) {
            Platform.runLater(() -> this.close());
        }
    });
    }

    
    public void unbindProgress() {
        progressBar.progressProperty().unbind();
    }
 }