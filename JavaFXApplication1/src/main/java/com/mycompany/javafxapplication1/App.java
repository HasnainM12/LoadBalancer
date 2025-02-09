package com.mycompany.javafxapplication1;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaFX App
 */
public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        LoadBalancerDB loadBalancerDB = new LoadBalancerDB();
        loadBalancerDB.createStorageContainersTable(); // ✅ Ensure table is created

        SessionDB sessionDB = new SessionDB();
        sessionDB.createSessionTable();

        FileDB fileDB = new FileDB();
        UserDB userDB = new UserDB();

        try {
            userDB.createUserTable();  
            fileDB.createFilesTable();
            fileDB.createFilePermissionsTable();
            fileDB.createChunksTable();
            sessionDB.createSessionTable();
            DatabaseSynchroniser.getInstance(); // Start sync service
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(App.class.getName()).log(Level.SEVERE, null, ex);
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
        launch();
    }
}
