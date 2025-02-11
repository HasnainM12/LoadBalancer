package com.mycompany.javafxapplication1;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

public class App extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        Platform.startup(() -> initialiseSystem(stage));
    
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("Uncaught error: " + throwable);
            throwable.printStackTrace();
        });
    }

    private void initialiseSystem(Stage stage) {
        Connection conn = null;
        try {
            System.out.println("Creating storage directory...");
            File storageDir = new File("storage");
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                throw new IOException("Failed to create storage directory");
            }
    
            System.out.println("Creating container directories...");
            for (int i = 1; i <= 4; i++) {
                File containerDir = new File(storageDir, "container" + i);
                if (!containerDir.exists() && !containerDir.mkdirs()) {
                    throw new IOException("Failed to create container directory: " + containerDir);
                }
            }
    
            System.out.println("Initialising databases...");
            LoadBalancerDB loadBalancerDB = new LoadBalancerDB();
            FileDB fileDB = new FileDB();
            UserDB userDB = new UserDB();
            SessionDB sessionDB = new SessionDB();
    
            conn = DBConnection.getMySQLConnection();
            if (conn == null) {
                throw new SQLException("Failed to establish database connection");
            }
    
            System.out.println("Creating tables...");
            loadBalancerDB.createStorageContainersTable(conn);
            userDB.createUserTable(conn);
            fileDB.createFileTable(conn);
            fileDB.createFilePermissionsTable(conn);
    
            // Initialise storage containers in database
            for (int i = 1; i <= 4; i++) {
                loadBalancerDB.addStorageContainer("container" + i, conn);
            }
    
            System.out.println("Initialising services...");
            LoadBalancer.getInstance();
    
            System.out.println("Showing primary stage...");
            showPrimaryStage(stage);
    
        } catch (Exception ex) {
            System.err.println("Failed to initialise system: " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException("Failed to initialise system", ex);
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
    }
    
    private void showPrimaryStage(Stage stage) throws IOException {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("primary.fxml"));
            Parent root = loader.load();
            
            if (root == null) {
                throw new IOException("Failed to load FXML");
            }
            
            Scene scene = new Scene(root, 640, 480);
            stage.setScene(scene);
            stage.setTitle("Primary View");
            
            Platform.runLater(() -> stage.show());
        } catch (Exception e) {
            System.err.println("Error showing primary stage: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public void stop() {
        LoadBalancer.getInstance().shutdown();
    }

    public static void main(String[] args) {
        System.setProperty("javafx.platform", "gtk");
        System.setProperty("prism.order", "sw");
        launch(args);
    }
}
