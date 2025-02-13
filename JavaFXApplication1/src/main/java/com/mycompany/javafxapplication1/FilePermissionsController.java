package com.mycompany.javafxapplication1;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import java.util.stream.Collectors;

public class FilePermissionsController {
    @FXML private ComboBox<String> userComboBox;
    @FXML private CheckBox readPermissionCheckBox;
    @FXML private CheckBox writePermissionCheckBox;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    private Long fileId;
    private String filename;
    private String owner;
    private FileDB fileDB;
    private UserDB userDB;
    
    @FXML
    public void initialise() {
        fileDB = new FileDB();
        userDB = new UserDB();
        saveButton.setOnAction(event -> handleSave());
        cancelButton.setOnAction(event -> closeDialogue());
    }
    
    @FXML
    public void setupDialog(Long fileId, String filename, String owner) {
        this.fileId = fileId;
        this.filename = filename;
        this.owner = owner;

        // ✅ Ensure userDB is initialized before calling loadUsers()
        initialise();

        if (Session.getInstance().isAdmin() || owner.equals(Session.getInstance().getUsername())) {
            loadUsers();
            setupUserSelectionHandler();
        } else {
            showError("Only file owners and administrators can share files");
            closeDialogue();
        }
    }


    private void loadUsers() {
        try {
            var users = userDB.getAllUsers();
            System.out.println("[DEBUG] Users loaded: " + users.size());
            
            userComboBox.setItems(
                users.stream()
                    .map(User::getUser)
                    .filter(user -> !user.equals(owner) && !user.equals(Session.getInstance().getUsername()))
                    .collect(Collectors.toCollection(FXCollections::observableArrayList))
            );
    
            System.out.println("[DEBUG] Filtered users: " + userComboBox.getItems().size());
    
        } catch (Exception e) {
            showError("Failed to load users: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    


    private void setupUserSelectionHandler() {
        userComboBox.setOnAction(event -> {
            String selectedUser = userComboBox.getValue();
            if (selectedUser != null) {
                FileDB.FilePermission permissions = fileDB.getFilePermissions(fileId, selectedUser);
                readPermissionCheckBox.setSelected(permissions.canRead());
                writePermissionCheckBox.setSelected(permissions.canWrite());
            }
        });
    }

    @FXML
private void handleSave() {
    String selectedUser = userComboBox.getValue();
    if (selectedUser == null) {
        showError("Please select a user");
        return;
    }

    System.out.println("[DEBUG] Saving permissions for user: " + selectedUser + ", fileId: " + fileId);
    System.out.println("[DEBUG] Read: " + readPermissionCheckBox.isSelected() + ", Write: " + writePermissionCheckBox.isSelected());

    boolean success = fileDB.setFilePermissions(
        fileId,
        selectedUser,
        readPermissionCheckBox.isSelected(),
        writePermissionCheckBox.isSelected()
    );

    if (success) {
        showSuccess("Permissions updated successfully");
        closeDialogue();
    } else {
        showError("Failed to save permissions");
    }
}

    
    private void closeDialogue() {
        ((Stage) saveButton.getScene().getWindow()).close();
    }
    
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}