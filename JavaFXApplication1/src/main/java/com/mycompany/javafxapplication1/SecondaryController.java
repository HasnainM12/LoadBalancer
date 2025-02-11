package com.mycompany.javafxapplication1;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.File;
import java.util.Optional;
import javafx.event.ActionEvent;
import java.io.IOException;


public class SecondaryController {
    @FXML private TextField userTextField;
    @FXML private TableView<UserFile> fileTableView;
    @FXML private TableView<User> dataTableView;
    @FXML private Button secondaryButton;
    @FXML private Button uploadButton;
    @FXML private Button updateAccountBtn;
    @FXML private Button deleteAccountBtn;
    @FXML private Button deleteButton;
    @FXML private Button promoteToAdminBtn;
    @FXML private Button shareButton;

    private UserDB userDB;
    private FileDB fileDB;
    private Session session;
    private final SystemLogger logger = SystemLogger.getInstance();

    public SecondaryController() {
        userDB = new UserDB();
        fileDB = new FileDB();
        session = Session.getInstance();
    }
    
    @FXML
    private void openTerminal(ActionEvent event) {
        System.out.println("[INFO] Opening Terminal...");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("terminal.fxml"));
            Parent root = loader.load();

            Stage terminalStage = new Stage();
            terminalStage.setTitle("Terminal");
            terminalStage.setScene(new Scene(root, 800, 600));
            terminalStage.show();
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to open Terminal: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void promoteToAdminHandler(ActionEvent event) {
        System.out.println("[INFO] Promote to Admin button clicked!");

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Promote User to Admin");
        dialog.setHeaderText("Enter the username to promote:");
        Optional<String> username = dialog.showAndWait();

        if (username.isPresent() && !username.get().isBlank()) {
            if (userDB.promoteToAdmin(username.get())) {
                showSuccess("User " + username.get() + " is now an admin.");
            } else {
                showError("Failed to promote user.");
            }
        }
    }


    @FXML
    private void handleUpload() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            try {
                logger.log(SystemLogger.LogLevel.AUDIT, "User " + session.getUsername() + " uploading file: " + file.getName());
                
                ProgressDialog progressDialog = new ProgressDialog("Uploading File");
                progressDialog.bindProgress(DelayManager.getInstance());
                
                Long fileId = fileDB.addFile(file.getName(), session.getUsername(), readFileContent(file));
                if (fileId != -1L) {
                    showSuccess("File uploaded successfully");
                    refreshFileList();
                } else {
                    showError("Failed to upload file");
                }
                
                progressDialog.close();
            } catch (Exception e) {
                showError("Upload error: " + e.getMessage());
            }
        }
    }
    


    private String readFileContent(File file) throws Exception {
        return new String(java.nio.file.Files.readAllBytes(file.toPath()));
    }

    @FXML
    private void handleDelete() {
        UserFile selectedFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showError("Please select a file to delete");
            return;
        }

        if (!selectedFile.getOwner().equals(session.getUsername()) && !session.isAdmin()) {
            showError("You can only delete your own files");
            return;
        }

        Optional<ButtonType> result = showConfirmation("Delete File", 
            "Are you sure you want to delete " + selectedFile.getFilename() + "?");
            
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                if (fileDB.deleteFile(selectedFile.getId())) {
                    refreshFileList();
                    showSuccess("File deleted successfully");
                } else {
                    showError("Failed to delete file");
                }
            } catch (Exception e) {
                showError("Delete error: " + e.getMessage());
            }
        }
    }
    
    
    @FXML
    private void handleEditFile(ActionEvent event) {
        System.out.println("[INFO] Edit File button clicked!");

        try {
            // Load the File Editor FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("file-editor.fxml"));
            Parent root = loader.load();

            // Create a new stage for the file editor
            Stage fileEditorStage = new Stage();
            fileEditorStage.setTitle("File Editor");
            fileEditorStage.setScene(new Scene(root, 600, 400));
            fileEditorStage.show();
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to open File Editor: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @FXML
    private void handleShare() {
        UserFile selectedFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showError("Please select a file to share");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("file-permissions.fxml"));
            Parent root = loader.load();

            FilePermissionsController controller = loader.getController();
            controller.setupDialog(selectedFile.getId(), selectedFile.getFilename(), selectedFile.getOwner());

            Stage permissionsStage = new Stage();
            permissionsStage.setTitle("Share: " + selectedFile.getFilename());
            permissionsStage.setScene(new Scene(root));
            permissionsStage.initModality(Modality.APPLICATION_MODAL);
            permissionsStage.showAndWait();
        } catch (Exception e) {
            showError("Error opening share dialog: " + e.getMessage());
        }
    }

    @FXML
    private void refreshBtnHandler(ActionEvent event) {
        System.out.println("[INFO] Refreshing file list...");
        fileTableView.getItems().clear();
        fileTableView.getItems().addAll(new FileDB().getUserFiles(session.getUsername()));
    }


    @FXML
    private void updateAccountHandler() {
        User selectedUser = dataTableView.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showError("Please select a user");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("update.fxml"));
            Parent root = loader.load();
            
            UpdateController controller = loader.getController();
            controller.setUsername(selectedUser.getUser());
            controller.setParentController(this);
            
            Stage updateStage = new Stage();
            updateStage.setTitle("Update Account");
            updateStage.setScene(new Scene(root));
            updateStage.initModality(Modality.APPLICATION_MODAL);
            updateStage.show();
        } catch (Exception e) {
            showError("Error opening update dialog: " + e.getMessage());
        }
    }

    @FXML
    private void deleteAccountHandler(ActionEvent event) {
        System.out.println("[INFO] Delete Account button clicked!");

        User selectedUser = dataTableView.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showError("Please select a user");
            return;
        }

        String targetUser = selectedUser.getUser();
        if (!session.isAdmin() && !targetUser.equals(session.getUsername())) {
            showError("You can only delete your own account");
            return;
        }

        Optional<ButtonType> result = showConfirmation("Delete Account",
            "Are you sure you want to delete this account?");

        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                if (userDB.deleteUser(targetUser)) {
                    if (targetUser.equals(session.getUsername())) {
                        session.clearSession();
                        System.out.println("[INFO] Account deleted. Returning to login.");
                        switchToPrimary();
                    } else {
                        refreshUserList();
                        showSuccess("Account deleted successfully");
                    }
                } else {
                    showError("Failed to delete account");
                }
            } catch (Exception e) {
                showError("Delete error: " + e.getMessage());
            }
        }
    }



    @FXML
    private void promoteToAdminHandler() {
        if (!session.isAdmin()) {
            showError("Admin privileges required");
            return;
        }

        User selectedUser = dataTableView.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showError("Please select a user");
            return;
        }

        try {
            if (userDB.promoteToAdmin(selectedUser.getUser())) {
                refreshUserList();
                showSuccess("User promoted to admin");
            } else {
                showError("Failed to promote user");
            }
        } catch (Exception e) {
            showError("Promotion error: " + e.getMessage());
        }
    }

    @FXML
    private void switchToPrimary() {
        try {
            session.clearSession();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("primary.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 640, 480);
            Stage primaryStage = new Stage();
            primaryStage.setScene(scene);
            primaryStage.setTitle("Login");
            primaryStage.show();
            ((Stage) secondaryButton.getScene().getWindow()).close();
        } catch (Exception e) {
            showError("Navigation error: " + e.getMessage());
        }
    }

    public void refreshUserList() {
        ObservableList<User> users = userDB.getAllUsers();
        dataTableView.setItems(users);
    }

    private void refreshFileList() {
        fileTableView.setItems(fileDB.getUserFiles(session.getUsername()));
    }

    private void setupTables() {
        setupFileTable();
        setupUserTable();
    }

    private void setupFileTable() {
        fileTableView.getColumns().clear();
        
        TableColumn<UserFile, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        
        TableColumn<UserFile, String> nameCol = new TableColumn<>("Filename");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("filename"));
        
        TableColumn<UserFile, String> ownerCol = new TableColumn<>("Owner");
        ownerCol.setCellValueFactory(new PropertyValueFactory<>("owner"));
        
        fileTableView.getColumns().addAll(idCol, nameCol, ownerCol);
        refreshFileList();
    }

    private void setupUserTable() {
        dataTableView.getColumns().clear();
        
        TableColumn<User, String> userCol = new TableColumn<>("Username");
        userCol.setCellValueFactory(new PropertyValueFactory<>("user"));
        
        TableColumn<User, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));
        
        dataTableView.getColumns().addAll(userCol, roleCol);
        refreshUserList();
    }

    public void initialise(String[] credentials) {
        if (!checkSession()) return;
        userTextField.setText(credentials[0]);
        setupTables();
    }

    private boolean checkSession() {
        if (!session.isValid()) {
            switchToPrimary();
            return false;
        }
        session.updateLastActivity();
        return true;
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showSuccess(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private Optional<ButtonType> showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        return alert.showAndWait();
    }
}