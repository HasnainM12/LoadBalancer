package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LoadBalancerDB {
    private final SystemLogger logger = SystemLogger.getInstance();

    public void createStorageContainersTable(Connection conn) throws SQLException {
        String storageQuery = "CREATE TABLE IF NOT EXISTS StorageContainers (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "name VARCHAR(255) UNIQUE, " +
                "status VARCHAR(255) DEFAULT 'active', " +
                "current_load INTEGER DEFAULT 0, " +
                "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)";
        
        String operationsQuery = "CREATE TABLE IF NOT EXISTS OperationsLog (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "container_name VARCHAR(255), " +
                "operation_type VARCHAR(255), " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (container_name) REFERENCES StorageContainers(name))";
        
        try (PreparedStatement storageStmt = conn.prepareStatement(storageQuery);
             PreparedStatement operationsStmt = conn.prepareStatement(operationsQuery)) {
            storageStmt.executeUpdate();
            operationsStmt.executeUpdate();
        }
    }

    public void createTaskHistoryTable(Connection conn) throws SQLException {
        String query = "CREATE TABLE IF NOT EXISTS TaskHistory (" +
                       "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                       "task_id VARCHAR(255) UNIQUE, " +
                       "worker VARCHAR(255), " +
                       "status VARCHAR(50), " +
                       "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP)";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }
    
    public void addStorageContainer(String containerName, Connection conn) throws SQLException {
        if (containerExists(containerName, conn)) {
            return;
        }
     
        String query = "INSERT INTO StorageContainers (name, status) VALUES (?, 'active')";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, containerName);
            stmt.executeUpdate();
        }
    }
     
    private boolean containerExists(String containerName, Connection conn) throws SQLException {
        String query = "SELECT COUNT(*) FROM StorageContainers WHERE name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, containerName);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    public List<String> getStorageContainers(Connection conn) throws SQLException {
        List<String> containers = new ArrayList<>();
        String query = "SELECT name FROM StorageContainers WHERE status = 'active'";
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                containers.add(rs.getString("name"));
            }
        }
        return containers;
    }

    public void logOperation(String containerName, String operationType, Connection conn) throws SQLException {
        String query = "INSERT INTO OperationsLog (container_name, operation_type) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, containerName);
            stmt.setString(2, operationType);
            stmt.executeUpdate();
        }
    }

    public void updateContainerLoad(String containerName, int loadChange, Connection conn) throws SQLException {
        String query = "UPDATE StorageContainers SET current_load = current_load + ?, last_updated = CURRENT_TIMESTAMP WHERE name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, loadChange);
            stmt.setString(2, containerName);
            stmt.executeUpdate();
        }
    }
}