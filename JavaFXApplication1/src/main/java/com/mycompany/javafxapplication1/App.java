package com.mycompany.javafxapplication1;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.Connection;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaFX App
 */
public class App extends Application {

    private static final Logger LOGGER = Logger.getLogger(App.class.getName());

    @Override
    public void init() {
        // Ensure clean shutdown
        Platform.setImplicitExit(true);
    }

    @Override
    public void start(Stage stage) throws IOException {
        try {
            initializeDatabases();
            initializeUI(stage);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to initialize application", ex);
            Platform.exit();
        }
    }

    private void initializeDatabases() {
        LoadBalancerDB loadBalancerDB = new LoadBalancerDB();
        FileDB fileDB = new FileDB();
        UserDB userDB = new UserDB();
        SessionDB sessionDB = new SessionDB();

        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            
            // Create tables in correct order (respecting foreign key constraints)
            loadBalancerDB.createStorageContainersTable(conn);
            userDB.createUserTable(conn);
            fileDB.createFileTable(conn);
            fileDB.createFilePermissionsTable(conn);
            fileDB.createChunksTable(conn);
            sessionDB.createSessionTable();  // SessionDB uses SQLite, so no need for MySQL connection

            // Start services
            DatabaseSynchroniser.getInstance();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Database initialization failed", ex);
            throw new RuntimeException("Failed to initialize databases", ex);
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
    }

    private void initializeUI(Stage stage) throws IOException {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("primary.fxml"));
            Parent root = loader.load();
            Scene scene = new Scene(root, 640, 480);
            stage.setScene(scene);
            stage.setTitle("Primary View");
            stage.show();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load UI", ex);
            throw ex;
        }
    }

    @Override
    public void stop() {
        try {
            LoadBalancer.getInstance().getHealthMonitor().shutdown();
            DatabaseSynchroniser.getInstance().shutdown();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Error during shutdown", ex);
        }
    }

    public static void main(String[] args) {
        // Configure graphics properties before launch
        System.setProperty("prism.order", "sw");
        System.setProperty("javafx.animation.fullspeed", "true");
        System.setProperty("prism.verbose", "true");
        System.setProperty("javafx.verbose", "true");
        System.setProperty("glass.platform", "gtk");
        System.setProperty("prism.forceGPU", "false");
        
        // Set headless mode false explicitly
        System.setProperty("java.awt.headless", "false");
        
        try {
            launch(args);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to start application", e);
            System.exit(1);
        }
    }
}
