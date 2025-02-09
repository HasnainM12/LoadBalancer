package com.mycompany.javafxapplication1;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import java.io.IOException;

public class UpdateController {
    @FXML
    private TextField usernameField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private PasswordField confirmPasswordField;
    
    @FXML
    private Button updateBtn;
    
    @FXML
    private Button cancelBtn;

    @FXML
    private ComboBox<String> roleComboBox;
    
    @FXML
    private Label errorLabel;
    
    private String currentUsername;
    private UserDB userDB;
    private SecondaryController parentController;

    public UpdateController() {
        userDB = new UserDB();
    }

    public void setParentController(SecondaryController controller) {
        this.parentController = controller;
    }

    @FXML
    public void setUsername(String username) {
        this.currentUsername = username;
        usernameField.setText(username);
        roleComboBox.setValue(userDB.getUserRole(username));
    }

    @FXML
    private void updateBtnHandler(ActionEvent event) {
        String newUsername = usernameField.getText();
        String newPassword = passwordField.getText();
        String newRole = roleComboBox.getValue();

        if (newUsername.isEmpty() || newPassword.isEmpty()) {
            errorLabel.setText("Username and password required.");
            return;
        }

        if (newPassword.isBlank()) {
            errorLabel.setText("Password cannot be empty.");
            return;
        }

        if (!newPassword.equals(confirmPasswordField.getText())) {
            errorLabel.setText("Passwords do not match.");
            return;
        }

        if (userDB.usernameExists(newUsername) && !newUsername.equals(currentUsername)) {
            errorLabel.setText("Username already taken.");
            return;
        }

        try {
            boolean success = userDB.updateUserWithRole(currentUsername, newUsername, newPassword, newRole);
            if (success) {
                showSuccess("Account updated successfully!");
                returnToSecondary(newUsername);
            } else {
                errorLabel.setText("Update failed.");
            }
        } catch (Exception e) {
            errorLabel.setText("Error: " + e.getMessage());
        }
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void cancelBtnHandler(ActionEvent event) {
        returnToSecondary(currentUsername);
    }

    private void returnToSecondary(String username) {
        // Refresh the user list in the parent window before closing
        if (parentController != null) {
            parentController.refreshUserList();
        }
        // Close the update window
        Stage currentStage = (Stage) updateBtn.getScene().getWindow();
        currentStage.close();
    }
}
