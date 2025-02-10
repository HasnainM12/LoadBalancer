package com.mycompany.javafxapplication1;

import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.mycompany.javafxapplication1.SystemLogger.LogLevel;
import java.nio.file.Files;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.Optional;
import java.io.File;
import java.io.IOException;


public class SecondaryController {
    
    @FXML
    private TextField userTextField;
    
    @FXML
    private TableView<UserFile> fileTableView;

    @FXML
    private TableView<User> dataTableView;

    @FXML
    private Button secondaryButton;
    
    @FXML
    private Button refreshBtn;

    @FXML
    private Button uploadButton;
    
    @FXML
    private TextField customTextField;

    @FXML
    private Button updateAccountBtn;

    @FXML
    private Button deleteAccountBtn;

    @FXML
    private Button deleteButton;

    @FXML
    private Button promoteToAdminBtn; 

    @FXML
    private Button shareButton;

    private UserDB userDB;
    private FileDB fileDB;
    private Session session;
    private final SystemLogger logger = SystemLogger.getInstance();

    public SecondaryController() {
        userDB = new UserDB();
        fileDB = new FileDB();
        session = Session.getInstance();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    

    @FXML
    private void refreshBtnHandler(ActionEvent event) {
        if (!checkSession()) return;
        Stage primaryStage = (Stage) customTextField.getScene().getWindow();
        customTextField.setText((String) primaryStage.getUserData());
    }

    @FXML
    private void updateAccountHandler(ActionEvent event) {
        if (!checkSession()) return;

        User selectedUser = dataTableView.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showAlert("Error", "Please select a user", Alert.AlertType.ERROR);
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("update.fxml"));
            Parent root = loader.load();
            
            // Get controller and set username and parent controller
            UpdateController controller = loader.getController();
            controller.setUsername(selectedUser.getUser());
            controller.setParentController(this);
            
            // Create and show stage
            Stage updateStage = new Stage();
            updateStage.setTitle("Update Account");
            updateStage.setScene(new Scene(root));
            updateStage.initModality(Modality.APPLICATION_MODAL);
            updateStage.show();
            
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Could not open update dialog", Alert.AlertType.ERROR);
        }
    }

        // Add method for uploading files
    // Add method for uploading files
    @FXML
    private void handleUpload() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            logger.log(LogLevel.AUDIT, "User " + Session.getInstance().getUsername() + " initiated upload of file: " + file.getName());
            ProgressDialog progressDialog = new ProgressDialog("Uploading File");
            progressDialog.bindProgress(DelayManager.getInstance());
            DelayManager.getInstance().resetProgress(); // Reset progress before starting

            fileDB.addFileRecord(file.getName(), Session.getInstance().getUsername(), file.getPath())
                .thenAccept(fileId -> {
                    if (fileId != -1) {
                        FileChunkManager chunkManager = new FileChunkManager(fileDB);
                        chunkManager.setLoadBalancer(LoadBalancer.getInstance());
                        chunkManager.splitFile(file, fileId)
                            .thenAccept(chunks -> {
                                try {
                                    // Save each chunk
                                    for (FileChunk chunk : chunks) {
                                        chunkManager.saveChunk(chunk);
                                        LoadBalancer.getInstance().saveChunkMetadata(chunk);
                                    }
                                    
                                    Platform.runLater(() -> {
                                        progressDialog.unbindProgress();
                                        progressDialog.close();
                                        showSuccess("File uploaded successfully");
                                        refreshFileList();
                                    });
                                } catch (Exception e) {
                                    Platform.runLater(() -> {
                                        progressDialog.unbindProgress();
                                        progressDialog.close();
                                        showError("Failed to save file chunks: " + e.getMessage());
                                    });
                                }
                            })
                            .exceptionally(e -> {
                                Platform.runLater(() -> {
                                    progressDialog.unbindProgress();
                                    progressDialog.close();
                                    showError("Failed to process file: " + e.getMessage());
                                });
                                return null;
                            });
                    } else {
                        Platform.runLater(() -> {
                            progressDialog.unbindProgress();
                            progressDialog.close();
                            showError("Failed to create file record");
                        });
                    }
                });

            progressDialog.show();
        }
    }

    @FXML
    private void handleDownload() {
        UserFile selectedFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showError("Please select a file to download");
            return;
        }

        logger.log(LogLevel.AUDIT, "User " + Session.getInstance().getUsername() +  " downloading file: " + selectedFile.getFilename());

        ProgressDialog progressDialog = new ProgressDialog("Downloading File");
        progressDialog.bindProgress(DelayManager.getInstance());

        fileDB.readFileContent(selectedFile.getId())
            .thenAccept(content -> {
                Platform.runLater(() -> {
                    progressDialog.unbindProgress();
                    progressDialog.close();
                    if (content != null) {
                        try {
                            File downloadPath = new File("downloads/" + selectedFile.getFilename());
                            Files.write(downloadPath.toPath(), content.getBytes());
                            showSuccess("File downloaded successfully: " + downloadPath.getAbsolutePath());
                        } catch (IOException e) {
                            showError("Error saving downloaded file");
                        }
                    } else {
                        showError("Error retrieving file content");
                    }
                });
            });

        progressDialog.show();
    }



    @FXML
    private void handleDelete() {
        UserFile selectedFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showError("Please select a file to delete");
            return;
        }

        if (!selectedFile.getOwner().equals(Session.getInstance().getUsername())) {
            showError("You can only delete files you own");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Delete");
        confirmation.setHeaderText("Delete File");
        confirmation.setContentText("Are you sure you want to delete " + selectedFile.getFilename() + "?");

        Optional<ButtonType> result = confirmation.showAndWait();
        logger.log(LogLevel.AUDIT, "User " + Session.getInstance().getUsername() + " attempting to delete file: " + selectedFile.getFilename());
        if (result.isPresent() && result.get() == ButtonType.OK) {
            ProgressDialog progressDialog = new ProgressDialog("Deleting File");
            progressDialog.bindProgress(DelayManager.getInstance());

            fileDB.deleteFile(selectedFile.getId())
                .thenAccept(success -> {
                    Platform.runLater(() -> {
                        progressDialog.close();
                        if (success) {
                            File fileToDelete = new File(selectedFile.getPath());
                            if (fileToDelete.exists()) {
                                fileToDelete.delete();
                            }
                            refreshFileList();
                        } else {
                            showError("Failed to delete file");
                        }
                    });
                });

            progressDialog.show();
        }
    }


    @FXML
    private void handleShare() {
        UserFile selectedFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            return;
        }

        logger.log(LogLevel.AUDIT, "User " + Session.getInstance().getUsername() + " sharing file: " + selectedFile.getFilename() + " ID: " + selectedFile.getId());
        

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
            e.printStackTrace();
            showError("Could not open sharing dialog: " + e.getMessage());
        }
    }




    @FXML
    private void deleteAccountHandler(ActionEvent event) {
        String targetUser = ((User) dataTableView.getSelectionModel().getSelectedItem()).getUser();
    
        if (!session.isAdmin() && !targetUser.equals(session.getUsername())) {
            showAlert("Access Denied", "You can only delete your own account", Alert.AlertType.ERROR);
            return;
        }

        Optional<ButtonType> result = showConfirmation("Delete Account", "Are you sure you want to delete this account?");
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (userDB.deleteUser(targetUser)) {
                switchToPrimary();
            } else {
                showAlert("Error", "Could not delete account", Alert.AlertType.ERROR);
            }
        }
    }
        
    @FXML
    private void switchToPrimary() {
        try {
            session.clearSession();

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
            ((Stage) secondaryButton.getScene().getWindow()).close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkSession() {
        if (!session.isValid()) {
            switchToPrimary();
            return false;
        }
        session.updateLastActivity();
        return true;
    }

    @FXML
    private void promoteToAdminHandler(ActionEvent event) {
        User selectedUser = dataTableView.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showAlert("Error", "Please select a user", Alert.AlertType.ERROR);
            return;
        }
    
        if (!session.isAdmin()) {
            showAlert("Access Denied", "Admin privileges required", Alert.AlertType.ERROR);
            return;
        }
    
        if (userDB.promoteToAdmin(selectedUser.getUser())) {
            showAlert("Success", "User promoted to admin", Alert.AlertType.INFORMATION);
            initialise(new String[]{userTextField.getText(), ""});
        }
    }

    private void setupFileTable() {
        // Add ID column
        fileTableView.getColumns().clear();
        TableColumn<UserFile, Long> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("id"));
        
        // Existing filename column
        TableColumn<UserFile, String> nameCol = new TableColumn<>("Filename");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("filename"));
        
        fileTableView.getColumns().setAll(List.of(idCol, nameCol));
        refreshFileList();
    }

    @FXML
    private void handleEditFile() {
        UserFile selectedFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showAlert("Error", "Please select a file to edit", Alert.AlertType.ERROR);
            return;
        }
    
    // Check permissions
    String currentUser = Session.getInstance().getUsername();
    if (!selectedFile.getOwner().equals(currentUser)) {
        FileDB.FilePermission permissions = fileDB.getFilePermissions(selectedFile.getId(), currentUser);
        if (!permissions.canWrite()) {
            showAlert("Error", "You don't have permission to edit this file", Alert.AlertType.ERROR);
            return;
        }
    }

    logger.log(LogLevel.AUDIT, "User " + Session.getInstance().getUsername() + " editing file: " + selectedFile.getFilename());
    
    try {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("file-editor.fxml"));
        Parent root = loader.load();
        
        FileEditorController controller = loader.getController();
        controller.initialise(selectedFile.getId(), selectedFile.getFilename(), selectedFile.getOwner());
        
        Stage editorStage = new Stage();
        editorStage.setTitle("Edit: " + selectedFile.getFilename());
        editorStage.setScene(new Scene(root));
        editorStage.initModality(Modality.APPLICATION_MODAL);
        editorStage.showAndWait();
        
        // Refresh the file list after editing
        refreshFileList();
    } catch (Exception e) {
        e.printStackTrace();
        showAlert("Error", "Could not open editor: " + e.getMessage(), Alert.AlertType.ERROR);
    }
}


    @FXML
    private void openTerminal(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("terminal.fxml"));
            if (loader.getLocation() == null) {
                throw new IOException("FXML file not found: terminal.fxml");
            }
            Parent root = loader.load();
            Scene scene = new Scene(root, 800, 600);
            Stage terminalStage = new Stage();
            terminalStage.setScene(scene);
            terminalStage.setTitle("Terminal");
            terminalStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

        



    private void refreshFileList() {
        String username = session.getUsername();
        fileTableView.setItems(fileDB.getUserFiles(username));
    }
    
    public void refreshUserList() {
        try {
            ObservableList<User> data = userDB.getAllUsers();
            dataTableView.setItems(data);
        } catch (Exception ex) {
            Logger.getLogger(SecondaryController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private Optional<ButtonType> showConfirmation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        return alert.showAndWait();
    }

    public void initialise(String[] credentials) {
        if (!checkSession()) return;
    
        userTextField.setText(credentials[0]);
        setupFileTable();
    
        try {
            ObservableList<User> data = userDB.getAllUsers();
            
            // Clear existing columns first
            dataTableView.getColumns().clear();
            
            TableColumn<User, String> userCol = new TableColumn<>("User");
            userCol.setCellValueFactory(new PropertyValueFactory<>("user"));
            TableColumn<User, String> passCol = new TableColumn<>("Pass");
            passCol.setCellValueFactory(new PropertyValueFactory<>("pass"));
            
            dataTableView.setItems(data);
            // Fix type safety warning by using a specific List instead of varargs
            dataTableView.getColumns().setAll(List.of(userCol, passCol));
        } catch (Exception ex) {
            Logger.getLogger(SecondaryController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
