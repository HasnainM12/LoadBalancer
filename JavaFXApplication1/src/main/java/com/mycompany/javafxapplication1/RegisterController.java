package com.mycompany.javafxapplication1;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.text.Text;


/**
 * FXML Controller class for user registration
 */
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
    
    @FXML
    private Text fileText;
    
    @FXML
    private Button selectBtn;

    private static final int MIN_PASSWORD_LENGTH = 8;
    private UserDB userDB;

    public RegisterController() {
        userDB = new UserDB();
    }

    @FXML
    private void selectBtnHandler(ActionEvent event) throws IOException {
        Stage primaryStage = (Stage) selectBtn.getScene().getWindow();
        primaryStage.setTitle("Select a File");

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        
        if (selectedFile != null) {
            fileText.setText(selectedFile.getCanonicalPath());
        }
    }

    private void showDialog(String title, String header, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
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

        try {
            if (userDB.usernameExists(username)) {
                errorLabel.setText("Username already taken");
                return false;
            }
        } catch (Exception e) {
            errorLabel.setText("Database error occurred");
            e.printStackTrace();
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
            e.printStackTrace();
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
            if (loader.getLocation() == null) {
                throw new IOException("FXML file not found: primary.fxml");
            }
            Parent root = loader.load();
            Scene scene = new Scene(root, 640, 480);
            Stage secondaryStage = new Stage();
            secondaryStage.setScene(scene);
            secondaryStage.setTitle("Login");
            secondaryStage.show();
            primaryStage.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
