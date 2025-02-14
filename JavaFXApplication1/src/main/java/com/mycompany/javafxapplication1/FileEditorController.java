package com.mycompany.javafxapplication1;

import java.util.UUID;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import org.json.JSONObject;

public class FileEditorController {
    @FXML private TextArea contentArea;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    
    private Long fileId;
    private String filename;
    private String owner;
    private String path;
    private FileDB fileDB;
    private final SystemLogger logger = SystemLogger.getInstance();
    private MQTTClient mqttClient;
    
    public void setupEditor(Long fileId, String filename, String owner) {
        this.fileId = fileId;
        this.filename = filename;
        this.owner = owner;
        
        if (fileDB == null) {
            fileDB = new FileDB();
        }
        
        this.path = fileDB.getFilePath(fileId);
        if (path == null) {
            showError("Error: Could not retrieve file path.");
            return;
        }
        
        mqttClient = new MQTTClient("FileEditor-" + UUID.randomUUID().toString());
        initialise();
    }

    @FXML
    public void initialise() {
        if (fileDB == null) {
            fileDB = new FileDB();
        }

        String currentUser = Session.getInstance().getUsername();
        boolean canEdit = owner.equals(currentUser);

        if (!canEdit) {
            FileDB.FilePermission permissions = fileDB.getFilePermissions(fileId, currentUser);
            canEdit = permissions.canWrite();

            if (!permissions.canRead()) {
                showError("You don't have permission to view this file");
                closeWindow();
                return;
            }
        }

        saveButton.setDisable(!canEdit);
        contentArea.setEditable(canEdit);
        loadFileContent();
    }

    private void loadFileContent() {
        try {
            String taskId = UUID.randomUUID().toString();
            
            ProgressDialog progressDialog = new ProgressDialog("Loading File");
            progressDialog.trackProgress(taskId);
            progressDialog.setAutoClose(true);
            
            JSONObject taskData = new JSONObject()
                .put("taskId", taskId)
                .put("operation", "READ")
                .put("fileId", fileId)
                .put("filename", filename);
            
            LoadBalancer.getInstance().submitTask(taskData);
            
            progressDialog.show();
        } catch (Exception e) {
            logger.logError("Failed to load file", e);
            showError("Error loading file: " + e.getMessage());
        }
    }

    @FXML
    private void handleSave() {
        try {
            FileDB.FilePermission permissions = fileDB.getFilePermissions(fileId, Session.getInstance().getUsername());
            if (!permissions.canWrite()) {
                showError("You don't have permission to edit this file.");
                return;
            }

            String taskId = UUID.randomUUID().toString();
            
            ProgressDialog progressDialog = new ProgressDialog("Saving File");
            progressDialog.trackProgress(taskId);
            progressDialog.setAutoClose(true);
            
            JSONObject taskData = new JSONObject()
                .put("taskId", taskId)
                .put("operation", "WRITE")
                .put("fileId", fileId)
                .put("filename", filename)
                .put("content", contentArea.getText());
            
            LoadBalancer.getInstance().submitTask(taskData);
            
            progressDialog.show();
        } catch (Exception e) {
            logger.logError("Save failed", e);
            showError("Error saving file: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleCancel() {
        logger.logFileOperation("CANCEL_EDIT", filename, "Edit cancelled by user");
        closeWindow();
    }
    
    private void showSuccess(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    
    private void closeWindow() {
        Platform.runLater(() -> {
            Stage stage = (Stage) saveButton.getScene().getWindow();
            stage.close();
        });
    }
}