package com.mycompany.javafxapplication1;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class RegisterController {
    @FXML
    private Label errorLabel;
    @FXML
    private Button registerBtn;
    @FXML
    private Button backLoginBtn;
    @FXML
    private PasswordField passPasswordField;
    @FXML
    private PasswordField rePassPasswordField;
    @FXML
    private TextField userTextField;

    private static final int MIN_PASSWORD_LENGTH = 8;
    private UserDB userDB;

    public RegisterController() {
        userDB = new UserDB();
    }

    private boolean validateRegistrationInput() {
        String username = userTextField.getText();
        String password = passPasswordField.getText();
        String confirmPass = rePassPasswordField.getText();

        if (username.isEmpty() || password.isEmpty() || confirmPass.isEmpty()) {
            errorLabel.setText("All fields must be filled out");
            return false;
        }

        if (username.length() < 3) {
            errorLabel.setText("Username must be at least 3 characters long");
            return false;
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            errorLabel.setText("Password must be at least " + MIN_PASSWORD_LENGTH + " characters long");
            return false;
        }

        if (!password.equals(confirmPass)) {
            errorLabel.setText("Passwords do not match");
            return false;
        }

        if (userDB.usernameExists(username)) {
            errorLabel.setText("Username already taken");
            return false;
        }

        return true;
    }

    @FXML
    private void registerBtnHandler(ActionEvent event) {
        try {
            if (!validateRegistrationInput()) {
                return;
            }

            String username = userTextField.getText();
            String password = passPasswordField.getText();

            userDB.addUser(username, password, "standard");
            showDialog("Registration Successful", null, "Your account has been created!", Alert.AlertType.INFORMATION);
            switchToPrimary();

        } catch (Exception e) {
            showDialog("Registration Error", null, "An error occurred during registration. Please try again.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void backLoginBtnHandler(ActionEvent event) {
        switchToPrimary();
    }

    private void switchToPrimary() {
        try {
            Stage primaryStage = (Stage) backLoginBtn.getScene().getWindow();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("primary.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 640, 480);
            Stage secondaryStage = new Stage();
            secondaryStage.setScene(scene);
            secondaryStage.setTitle("Login");
            secondaryStage.show();
            primaryStage.close();
        } catch (Exception e) {
            showDialog("Navigation Error", null, "Error returning to login screen: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void showDialog(String title, String header, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}