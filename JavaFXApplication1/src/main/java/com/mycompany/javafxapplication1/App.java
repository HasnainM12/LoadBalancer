package com.mycompany.javafxapplication1;

import javafx.application.Application;
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

    @Override
    public void start(Stage stage) throws IOException {
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
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }

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
        // Basic software rendering configuration
        System.setProperty("prism.order", "sw");
        launch();
    }
}
