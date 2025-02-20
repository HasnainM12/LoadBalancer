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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.json.JSONObject;
import javafx.concurrent.Task;

import javafx.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;



public class SecondaryController {
    @FXML private TextField userTextField;
    @FXML private TableView<UserFile> fileTableView;
    @FXML private TableView<User> dataTableView;
    @FXML private Button secondaryButton;
    @FXML private Button downloadButton;
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
    private void handleUpload() {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(null);
    
        if (file != null) {
            try {
                String taskId = UUID.randomUUID().toString();
    
                ProgressDialog progressDialog = new ProgressDialog("Uploading File");
                progressDialog.trackProgress(taskId);
    
                // Create FileOperation with the file path
                FileOperation operation = new FileOperation(file.getName(), FileOperation.OperationType.UPLOAD, file.length())
                        .setFilePath(file.getAbsolutePath());
    
                // Prepare JSON with all required fields including filePath
                JSONObject taskData = new JSONObject();
                taskData.put("taskId", taskId);
                taskData.put("operation", "UPLOAD");
                taskData.put("filename", file.getName());
                taskData.put("size", file.length());
                taskData.put("filePath", file.getAbsolutePath());
    
                // Offload the upload process to a background thread using a Task
                Task<Void> uploadTask = new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        LoadBalancer.getInstance().submitTask(taskData);
                        return null;
                    }
                };
    
                // Handle failure if the task encounters an error
                uploadTask.setOnFailed(e -> {
                    Throwable ex = uploadTask.getException();
                    showError("Upload error: " + ex.getMessage());
                });
    
                // Display the progress dialog immediately and then start the task
                progressDialog.show();
                new Thread(uploadTask).start();
            } catch (Exception e) {
                showError("Upload error: " + e.getMessage());
            }
        }
    }
     
        
    @FXML
    private void handleDownload() {
        UserFile selectedFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showError("Please select a file to download");
            return;
        }

        // Check permissions before downloading
        FileDB.FilePermission permissions = fileDB.getFilePermissions(selectedFile.getId(), session.getUsername());
        if (!permissions.canRead()) {
            showError("You don't have permission to download this file.");
            return;
        }

        String taskId = UUID.randomUUID().toString();
        ProgressDialog progressDialog = new ProgressDialog("Downloading File");
        progressDialog.trackProgress(taskId);
        progressDialog.setAutoClose(true);

        // Prepare JSON with required fields for download
        JSONObject taskData = new JSONObject();
        taskData.put("taskId", taskId);
        taskData.put("operation", "DOWNLOAD");
        taskData.put("filename", selectedFile.getFilename());
        taskData.put("fileId", selectedFile.getId());

        // Offload the download process to a background thread using a Task
        Task<Void> downloadTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                LoadBalancer.getInstance().submitTask(taskData);
                return null;
            }
        };

        downloadTask.setOnFailed(e -> {
            Throwable ex = downloadTask.getException();
            showError("Download error: " + ex.getMessage());
        });

        // Show the progress dialog immediately and then start the task
        progressDialog.show();
        new Thread(downloadTask).start();

    }

    @FXML
    private void handleDelete() {
        UserFile selectedFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showError("Please select a file to delete");
            return;
        }

        // Check permissions before deleting
        FileDB.FilePermission permissions = fileDB.getFilePermissions(selectedFile.getId(), session.getUsername());
        if (!permissions.canWrite()) {
            showError("You don't have permission to delete this file.");
            return;
        }

        Optional<ButtonType> result = showConfirmation("Delete File", "Are you sure you want to delete " + selectedFile.getFilename() + "?");
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String taskId = UUID.randomUUID().toString();
            ProgressDialog progressDialog = new ProgressDialog("Deleting File");
            progressDialog.trackProgress(taskId);
            progressDialog.setAutoClose(true);

            JSONObject taskData = new JSONObject();
            taskData.put("taskId", taskId);
            taskData.put("operation", "DELETE");
            taskData.put("filename", selectedFile.getFilename());
            taskData.put("fileId", selectedFile.getId());

            // Offload the delete process to a background thread using a Task
            Task<Void> deleteTask = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    LoadBalancer.getInstance().submitTask(taskData);
                    return null;
                }
            };

            deleteTask.setOnFailed(e -> {
                Throwable ex = deleteTask.getException();
                showError("Delete error: " + ex.getMessage());
            });

            progressDialog.show();
            new Thread(deleteTask).start();

        }
    }

    @FXML
    private void handleEditFile() {
        UserFile selectedFile = fileTableView.getSelectionModel().getSelectedItem();
        if (selectedFile == null) {
            showError("Please select a file to edit.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("file-editor.fxml"));
            Parent root = loader.load();

            FileEditorController controller = loader.getController();
            if (controller == null) {
                throw new RuntimeException("Controller is null! FXML file may be broken.");
            }

            //  Pass file ID, filename, and owner to the editor
            controller.setupEditor(selectedFile.getId(), selectedFile.getFilename(), selectedFile.getOwner());

            Stage editorStage = new Stage();
            editorStage.setTitle("Edit File: " + selectedFile.getFilename());
            editorStage.setScene(new Scene(root));
            editorStage.initModality(Modality.APPLICATION_MODAL);
            editorStage.showAndWait();
        } catch (Exception e) {
            showError("Failed to open File Editor: " + e.getMessage());
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
            if (controller == null) {
                throw new RuntimeException("Controller is null! FXML file may be broken.");
            }

            controller.setupDialog(selectedFile.getId(), selectedFile.getFilename(), selectedFile.getOwner());
            Stage permissionsStage = new Stage();
            permissionsStage.setTitle("Share: " + selectedFile.getFilename());
            permissionsStage.setScene(new Scene(root));
            permissionsStage.initModality(Modality.APPLICATION_MODAL);
            permissionsStage.showAndWait();
        } catch (Exception e) {
            showError("Error opening share dialog: " + e.getMessage());
            e.printStackTrace();
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
    private void promoteToAdminHandler(ActionEvent event) {
        System.out.println("[INFO] Promote to Admin button clicked!");
    
        if (!session.isAdmin()) {
            showError("Admin privileges required");
            return;
        }
    
        User selectedUser = dataTableView.getSelectionModel().getSelectedItem();
        if (selectedUser == null) {
            showError("Please select a user to promote.");
            return;
        }
    
        try {
            if (userDB.promoteToAdmin(selectedUser.getUser())) {
                refreshUserList();
                showSuccess("User " + selectedUser.getUser() + " is now an admin.");
            } else {
                showError("Failed to promote user.");
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
        userTextField.setEditable(false); 
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