package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LoadBalancerDB {
    private final SystemLogger logger = SystemLogger.getInstance();

    /**
     * Creates the StorageContainers and OperationsLog tables.
     * The StorageContainers table uses "container_name" as a unique key,
     * and OperationsLog has a foreign key referencing it.
     */
    public void createStorageContainersTable(Connection conn) throws SQLException {
        // Create StorageContainers first
            String storageQuery = "CREATE TABLE IF NOT EXISTS StorageContainers (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "container_name VARCHAR(255) NOT NULL UNIQUE, " +
                    "total_capacity BIGINT NOT NULL, " +
                    "used_capacity BIGINT NOT NULL DEFAULT 0" +
                    ") ENGINE=InnoDB";
    
            // Then create OperationsLog with the foreign key
            String operationsQuery = "CREATE TABLE IF NOT EXISTS OperationsLog (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "container_name VARCHAR(255) NOT NULL, " +
                    "operation_type VARCHAR(255) NOT NULL, " +
                    "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (container_name) REFERENCES StorageContainers(container_name)" +
                    ") ENGINE=InnoDB";
    
            // Execute table creation
            try (PreparedStatement storageStmt = conn.prepareStatement(storageQuery);
                 PreparedStatement operationsStmt = conn.prepareStatement(operationsQuery)) {
                storageStmt.executeUpdate();
                operationsStmt.executeUpdate();
            }
        }
        // Create StorageContainers first
    
    /**
     * Creates the TaskHistory table.
     */
    public void createTaskHistoryTable(Connection conn) throws SQLException {
        String query = "CREATE TABLE IF NOT EXISTS TaskHistory (" +
                       "id INT AUTO_INCREMENT PRIMARY KEY, " +
                       "task_id VARCHAR(255) NOT NULL, " +
                       "status VARCHAR(50) NOT NULL, " +
                       "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                       ") ENGINE=InnoDB";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }
    
    /**
     * Adds a storage container to the StorageContainers table.
     * Note: The totalCapacity must be provided since it is a required field.
     */
    public void addStorageContainer(String containerName, long totalCapacity, Connection conn) throws SQLException {
        if (containerExists(containerName, conn)) {
            return;
        }
     
        String query = "INSERT INTO StorageContainers (container_name, total_capacity, used_capacity) VALUES (?, ?, 0)";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, containerName);
            stmt.setLong(2, totalCapacity);
            stmt.executeUpdate();
        }
    }
     
    private boolean containerExists(String containerName, Connection conn) throws SQLException {
        String query = "SELECT COUNT(*) FROM StorageContainers WHERE container_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, containerName);
            ResultSet rs = stmt.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /**
     * Retrieves a list of active storage container names.
     */
    public List<String> getStorageContainers(Connection conn) throws SQLException {
        List<String> containers = new ArrayList<>();
        String query = "SELECT container_name FROM StorageContainers";
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                containers.add(rs.getString("container_name"));
            }
        }
        return containers;
    }

    /**
     * Logs an operation for a container in the OperationsLog table.
     */
    public void logOperation(String containerName, String operationType, Connection conn) throws SQLException {
        String query = "INSERT INTO OperationsLog (container_name, operation_type) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, containerName);
            stmt.setString(2, operationType);
            stmt.executeUpdate();
        }
    }

    /**
     * Updates the used capacity of a storage container.
     */
    public void updateContainerLoad(String containerName, long loadChange, Connection conn) throws SQLException {
        // Check if new load would exceed capacity
        String checkQuery = "SELECT total_capacity, used_capacity FROM StorageContainers WHERE container_name = ?";
        try (PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
            checkStmt.setString(1, containerName);
            ResultSet rs = checkStmt.executeQuery();
            
            if (rs.next()) {
                long totalCapacity = rs.getLong("total_capacity");
                long currentUsed = rs.getLong("used_capacity");
                if (currentUsed + loadChange > totalCapacity) {
                    throw new SQLException("Load change would exceed container capacity");
                }
            }
        }
     
        String query = "UPDATE StorageContainers SET used_capacity = used_capacity + ? WHERE container_name = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, loadChange);
            stmt.setString(2, containerName);
            stmt.executeUpdate();
        }
     }
}
