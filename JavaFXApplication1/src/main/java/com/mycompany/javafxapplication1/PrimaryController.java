package com.mycompany.javafxapplication1;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class PrimaryController {
    @FXML
    private Button registerBtn;
    @FXML
    private TextField userTextField;
    @FXML
    private PasswordField passPasswordField;

    private UserDB userDB;
    private Session session;

    public PrimaryController() {
        userDB = new UserDB();
        session = Session.getInstance();
    }

    @FXML
    private void registerBtnHandler() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("register.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 640, 480);
            Stage secondaryStage = new Stage();
            secondaryStage.setScene(scene);
            secondaryStage.setTitle("Register a new User");
            secondaryStage.show();
            ((Stage) registerBtn.getScene().getWindow()).close();
        } catch (Exception e) {
            showError("Error loading registration form", e.getMessage());
        }
    }

    @FXML
    private void switchToSecondary() {
        try {
            String username = userTextField.getText();
            String password = passPasswordField.getText();
            
            if (userDB.validateUser(username, password)) {
                String role = userDB.getUserRole(username);
                session.setUser(username, role);

                FXMLLoader loader = new FXMLLoader(getClass().getResource("secondary.fxml"));
                Parent root = loader.load();
                Scene scene = new Scene(root, 640, 480);
                Stage secondaryStage = new Stage();
                secondaryStage.setScene(scene);
                secondaryStage.setTitle("File Management");
                
                SecondaryController controller = loader.getController();
                controller.initialise(new String[]{username, ""});
                
                secondaryStage.show();
                ((Stage) registerBtn.getScene().getWindow()).close();
            } else {
                showError("Login Failed", "Invalid username or password");
            }
        } catch (Exception e) {
            showError("System Error", e.getMessage());
        }
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
