package com.mycompany.javafxapplication1;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

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
    
    public void initialise(Long fileId, String filename, String owner) {
        this.fileId = fileId;
        this.filename = filename;
        this.owner = owner;
        this.fileDB = new FileDB();
        
        ProgressDialog progressDialog = new ProgressDialog("Loading File");
        progressDialog.bindProgress(DelayManager.getInstance());
        
        fileDB.readFileContent(fileId)
            .thenAccept(content -> {
                Platform.runLater(() -> {
                    progressDialog.close();
                    if (content != null) {
                        try {
                            String decryptedContent = fileDB.decryptContent(content);
                            contentArea.setText(decryptedContent);
                        } catch (Exception e) {
                            contentArea.setText("Error: Unable to decrypt file");
                        }
                    } else {
                        contentArea.setText("Error: Unable to load file content");
                    }
                });
            });
        
        progressDialog.show();
        
        saveButton.setOnAction(event -> handleSave());
        cancelButton.setOnAction(event -> handleCancel());
    }
    
    private void handleSave() {
        try {
            String encryptedContent = fileDB.encryptContent(contentArea.getText());
            
            ProgressDialog progressDialog = new ProgressDialog("Saving File");
            progressDialog.bindProgress(DelayManager.getInstance());
            
            fileDB.updateFile(fileId, encryptedContent)
                .thenAccept(success -> {
                    Platform.runLater(() -> {
                        progressDialog.close();
                        if (success) {
                            closeWindow();
                        } else {
                            showError("Failed to save file");
                        }
                    });
                });
            
            progressDialog.show();
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
        System.err.println(message);
    }
}