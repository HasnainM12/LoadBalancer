package com.mycompany.javafxapplication1;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.scene.control.Alert;

public class FileEditorController {
    @FXML
    private TextArea contentArea;
    
    @FXML
    private Button saveButton;
    
    @FXML
    private Button cancelButton;
    
    private Long fileId;
    private String filename;
    private String owner;
    private FileDB fileDB;
    private final SystemLogger logger = SystemLogger.getInstance();
    
    public void initialise(Long fileId, String filename, String owner) {
        this.fileId = fileId;
        this.filename = filename;
        this.owner = owner;
        this.fileDB = new FileDB();
        
        ProgressDialog progressDialog = new ProgressDialog("Loading File");
        progressDialog.bindProgress(DelayManager.getInstance());
        DelayManager.getInstance().resetProgress();

        String content = fileDB.getFileContent(fileId);
        if (content != null) {
            try {
                String decryptedContent = fileDB.decryptContent(content);
                contentArea.setText(decryptedContent);
            } catch (Exception e) {
                contentArea.setText("Error: Unable to decrypt file");
                logger.logError("Decryption failed", e);
            }
        } else {
            contentArea.setText("Error: Unable to load file content");
        }
        
        saveButton.setOnAction(event -> handleSave());
        cancelButton.setOnAction(event -> handleCancel());
    }
    
    private void handleSave() {
        try {
            ProgressDialog progressDialog = new ProgressDialog("Saving File");
            progressDialog.bindProgress(DelayManager.getInstance());
            DelayManager.getInstance().resetProgress();

            boolean success = fileDB.updateFile(fileId, contentArea.getText());
            if (success) {
                closeWindow();
            } else {
                showError("Failed to save file");
            }
        } catch (Exception e) {
            showError("Error saving file: " + e.getMessage());
        }
    }
    
    private void handleCancel() {
        closeWindow();
    }
    
    private void closeWindow() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }
    
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}