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
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class App extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        initialiseSystem(stage);
    
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("Uncaught error: " + throwable);
            throwable.printStackTrace();
        });
    }

    private void initialiseSystem(Stage stage) {
        Connection mysqlConn = null;
        Connection sqliteConn = null;
    
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
    
            System.out.println("[INFO] Ensuring databases exist...");
    
            // Ensure SQLite database file exists
            File sqliteFile = new File("databases/comp20081.db");
            if (!sqliteFile.getParentFile().exists()) {
                sqliteFile.getParentFile().mkdirs();  // Ensure parent directory exists
            }

            System.out.println("[INFO] Initialising database connections...");
    
            LoadBalancerDB loadBalancerDB = new LoadBalancerDB();
            FileDB fileDB = new FileDB();
            UserDB userDB = new UserDB();
            SessionDB sessionDB = new SessionDB();
    
            // Ensure MySQL database exists
            mysqlConn = DBConnection.getMySQLConnection();
            if (mysqlConn == null) {
                throw new SQLException("Failed to establish MySQL database connection.");
            }
    
            try (PreparedStatement stmt = mysqlConn.prepareStatement("CREATE DATABASE IF NOT EXISTS comp20081")) {
                stmt.executeUpdate();
                System.out.println("[INFO] Ensured MySQL database exists.");
            }
    
            System.out.println("[INFO] Creating tables...");
    
            // Create Tables for MySQL
           
            loadBalancerDB.createStorageContainersTable(mysqlConn);
            loadBalancerDB.createTaskHistoryTable(mysqlConn);
            userDB.createUserTable(mysqlConn);
            fileDB.createFileTable(mysqlConn);
            fileDB.createFilePermissionsTable(mysqlConn);

    
            // Ensure SQLite tables exist
            System.out.println("[INFO] Ensuring SQLite tables exist...");
            sqliteConn = DBConnection.getSQLiteConnection();
            sessionDB.createSessionTable();  // ✅ Ensures Sessions table exists
            System.out.println("[INFO] SQLite session table checked/created.");
    
            // Initialise storage containers in database
            for (int i = 1; i <= 4; i++) {
                loadBalancerDB.addStorageContainer("container" + i, mysqlConn);
            }
    
            System.out.println("[INFO] Initialising services...");
            LoadBalancer.getInstance();
    
            System.out.println("[INFO] Showing primary stage...");
            showPrimaryStage(stage);
    
        } catch (Exception ex) {
            System.err.println("[ERROR] Failed to initialise system: " + ex.getMessage());
            ex.printStackTrace();
            throw new RuntimeException("Failed to initialise system", ex);
        } finally {
            if (mysqlConn != null) {
                DBConnection.releaseConnection(mysqlConn);
            }
            if (sqliteConn != null) {
                try {
                    sqliteConn.close();
                } catch (SQLException e) {
                    System.err.println("[ERROR] Failed to close SQLite connection: " + e.getMessage());
                }
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
