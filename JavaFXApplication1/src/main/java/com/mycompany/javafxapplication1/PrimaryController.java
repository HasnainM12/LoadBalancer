package com.mycompany.javafxapplication1;

import java.io.IOException;
import java.util.Optional;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

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
    private void registerBtnHandler(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("register.fxml"));
            if (loader.getLocation() == null) {
                throw new IOException("FXML file not found: register.fxml");
            }
            Parent root = loader.load();
            Scene scene = new Scene(root, 640, 480);
            Stage secondaryStage = new Stage();
            secondaryStage.setScene(scene);
            secondaryStage.setTitle("Register a new User");
            secondaryStage.show();
            ((Stage) registerBtn.getScene().getWindow()).close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void dialogue(String headerMsg, String contentMsg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Confirmation Dialog");
        alert.setHeaderText(headerMsg);
        alert.setContentText(contentMsg);
        alert.showAndWait();
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
                if (loader.getLocation() == null) {
                    throw new IOException("FXML file not found: secondary.fxml");
                }
                Parent root = loader.load();
                Scene scene = new Scene(root, 640, 480);
                Stage secondaryStage = new Stage();
                secondaryStage.setScene(scene);
                secondaryStage.setTitle("Show Users");
                SecondaryController controller = loader.getController();
                controller.initialise(new String[]{username, ""});
                secondaryStage.show();
                ((Stage) registerBtn.getScene().getWindow()).close();
            } else {
                dialogue("Invalid User Name / Password", "Please try again!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
