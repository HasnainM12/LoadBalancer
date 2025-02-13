package com.mycompany.javafxapplication1;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.scene.control.Alert;

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
    
    public void setupEditor(Long fileId, String filename, String owner) {
        // 1️⃣ Store file details
        this.fileId = fileId;
        this.filename = filename;
        this.owner = owner;
    
        // 2️⃣ Ensure fileDB is initialized
        if (fileDB == null) {
            fileDB = new FileDB();
        }
    
        // 3️⃣ Retrieve the file path from the database
        this.path = fileDB.getFilePath(fileId);
        System.out.println("[DEBUG] setupEditor() - File path retrieved: " + path);
    
        // 4️⃣ If the path is missing, show an error and stop execution
        if (path == null) {
            showError("Error: Could not retrieve file path.");
            return;
        }
    
        // 5️⃣ Now that all data is available, call initialise()
        initialise();
    }
    @FXML
    public void initialise() {
        // 1️⃣ Ensure fileDB is initialized
        if (fileDB == null) {
            fileDB = new FileDB();
        }
        System.out.println("[DEBUG] fileDB initialised in FileEditorController.");

        // 2️⃣ Get the current logged-in user
        String currentUser = Session.getInstance().getUsername();
        boolean canEdit = owner.equals(currentUser); // 3️⃣ Check if the user is the owner

        // 4️⃣ If the user is NOT the owner, check their file permissions
        if (!canEdit) {
            FileDB.FilePermission permissions = fileDB.getFilePermissions(fileId, currentUser);
            canEdit = permissions.canWrite(); // ✅ Allow editing if the user has write access

            // 5️⃣ If the user has NO read access, show error & close window
            if (!permissions.canRead()) {
                showError("You don't have permission to view this file");
                closeWindow();
                return;
            }
        }

        // 6️⃣ Enable/Disable UI based on permissions
        saveButton.setDisable(!canEdit);
        contentArea.setEditable(canEdit);

        // 7️⃣ Load the file content into the editor
        loadFileContent();
    }

    
    
    private void loadFileContent() {
        ProgressDialog progressDialog = new ProgressDialog("Loading File");
        progressDialog.bindProgress(DelayManager.getInstance());
        DelayManager.getInstance().resetProgress();
    
        try {
            // ✅ First, retrieve the file path from the database
            String filePath = fileDB.getFilePath(fileId);
            if (filePath == null || filePath.isEmpty()) {
                showError("File not found");
                closeWindow();
                return;
            }
    
            // ✅ Now, retrieve the actual file content from storage
            String content = fileDB.getFileContent(filePath);
            if (content != null) {
                contentArea.setText(content);
                logger.logFileOperation("OPEN", filename, "File opened for editing");
            } else {
                showError("Unable to load file content");
            }
        } catch (Exception e) {
            logger.logError("Failed to load file", e);
            showError("Error loading file: " + e.getMessage());
        }
    }
    
    @FXML
    private void handleSave() {
        ProgressDialog progressDialog = new ProgressDialog("Saving File");
        progressDialog.bindProgress(DelayManager.getInstance());
        DelayManager.getInstance().resetProgress();
    
        try {
            // ✅ Ensure user has write permissions before allowing save
            FileDB.FilePermission permissions = fileDB.getFilePermissions(fileId, Session.getInstance().getUsername());
            if (!permissions.canWrite()) {
                showError("You don't have permission to edit this file.");
                return;
            }
    
            String newContent = contentArea.getText();
    
            // ✅ Get the file path before updating it
            String filePath = fileDB.getFilePath(fileId);
            if (filePath == null || filePath.isEmpty()) {
                showError("File not found.");
                return;
            }
    
            // ✅ Now update the file content in storage
            boolean success = fileDB.updateFile(fileId, newContent);
    
            if (success) {
                logger.logFileOperation("SAVE", filename, "File saved successfully");
                showSuccess("File saved successfully.");
                closeWindow();
            } else {
                showError("Failed to save file.");
            }
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