package com.mycompany.javafxapplication1;

import javafx.application.Application;
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
        initializeSystem(stage);
    }

    private void initializeSystem(Stage stage) {
        LoadBalancerDB loadBalancerDB = null;
        Connection conn = null;
        try {
            // Step 1: Initialize storage system
            File storageDir = new File("storage");
            if (!storageDir.exists() && !storageDir.mkdirs()) {
                throw new IOException("Failed to create storage directory");
            }

            // Create and verify container directories
            for (int i = 1; i <= 4; i++) {
                File containerDir = new File(storageDir, "container" + i);
                if (!containerDir.exists() && !containerDir.mkdirs()) {
                    throw new IOException("Failed to create container directory: " + containerDir);
                }
            }

            // Step 2: Initialize databases
            loadBalancerDB = new LoadBalancerDB();
            FileDB fileDB = new FileDB();
            UserDB userDB = new UserDB();
            SessionDB sessionDB = new SessionDB();

            conn = DBConnection.getMySQLConnection();
            if (conn == null) {
                throw new SQLException("Failed to establish database connection");
            }

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

            // Step 3: Initialize services
            LoadBalancer.getInstance();
            DatabaseSynchroniser.getInstance();

            // Step 4: Initialize UI
            showPrimaryStage(stage);
        } catch (Exception ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("Failed to initialize system", ex);
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
    }

    private void showPrimaryStage(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("primary.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root, 640, 480);
        stage.setScene(scene);
        stage.setTitle("Primary View");
        stage.show();
    }

    @Override
    public void stop() {
        LoadBalancer.getInstance().getHealthMonitor().shutdown();
        DatabaseSynchroniser.getInstance().shutdown();
    }

    public static void main(String[] args) {
        // Force software rendering and disable hardware acceleration
        System.setProperty("prism.order", "sw");
        System.setProperty("javafx.animation.fullspeed", "true");
        System.setProperty("prism.forceGPU", "false");
        System.setProperty("prism.vsync", "false");
        System.setProperty("quantum.multithreaded", "false");
        launch();
    }
}
