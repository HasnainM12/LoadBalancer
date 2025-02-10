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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaFX App
 */
public class App extends Application {


    @Override
    public void start(Stage stage) throws IOException {
        Platform.startup(() -> initializeSystem(stage));
    
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("Uncaught error: " + throwable);
            throwable.printStackTrace();
        });
    }

    private void initializeSystem(Stage stage) {
        LoadBalancerDB loadBalancerDB = null;
        Connection conn = null;
        try {
            System.out.println("Creating storage directory...");
            File storageDir = new File("storage");
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                throw new IOException("Failed to create storage directory");
            }
    
            System.out.println("Creating container directories...");
            // Create and verify container directories
            for (int i = 1; i <= 4; i++) {
                File containerDir = new File(storageDir, "container" + i);
                if (!containerDir.exists() && !containerDir.mkdirs()) {
                    throw new IOException("Failed to create container directory: " + containerDir);
                }
            }
    
            System.out.println("Initializing databases...");
            // Initialize databases
            loadBalancerDB = new LoadBalancerDB();
            FileDB fileDB = new FileDB();
            UserDB userDB = new UserDB();
            SessionDB sessionDB = new SessionDB();
    
            conn = DBConnection.getMySQLConnection();
            if (conn == null) {
                throw new SQLException("Failed to establish database connection");
            }
    
            System.out.println("Creating tables...");
            // Create tables in correct order
            loadBalancerDB.createStorageContainersTable(conn);
            userDB.createUserTable(conn);
            fileDB.createFileTable(conn);
            fileDB.createFilePermissionsTable(conn);
            fileDB.createChunksTable(conn);
            sessionDB.createSessionTable();
    
            // Initialize storage containers in database
            for (int i = 1; i <= 4; i++) {
                loadBalancerDB.addStorageContainer("container" + i);
            }
    
            System.out.println("Initializing services...");
            // Initialize services
            LoadBalancer.getInstance();
            DatabaseSynchroniser.getInstance();
    
            System.out.println("Showing primary stage...");
            // Initialize UI
            showPrimaryStage(stage);
    
        } catch (Exception ex) {
            System.err.println("Failed to initialize system: " + ex.getMessage());
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
            throw new RuntimeException("Failed to initialize system", ex);
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
            
            // Add explicit error checking
            if (root == null) {
                throw new IOException("Failed to load FXML");
            }
            
            Scene scene = new Scene(root, 640, 480);
            stage.setScene(scene);
            stage.setTitle("Primary View");
            
            // Run on JavaFX thread
            Platform.runLater(() -> stage.show());
        } catch (Exception e) {
            System.err.println("Error showing primary stage: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    public void stop() {
        LoadBalancer.getInstance().getHealthMonitor().shutdown();
        DatabaseSynchroniser.getInstance().shutdown();
    }

    public static void main(String[] args) {
        System.setProperty("javafx.platform", "gtk");
        System.setProperty("prism.order", "sw");
        launch(args);
    }
}
