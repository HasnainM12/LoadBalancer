package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LoadBalancerDB {

    private void dropTables() {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            // Drop tables in correct order due to foreign key constraints
            try (PreparedStatement dropOps = conn.prepareStatement("DROP TABLE IF EXISTS OperationsLog")) {
                dropOps.executeUpdate();
            }
            try (PreparedStatement dropStorage = conn.prepareStatement("DROP TABLE IF EXISTS StorageContainers")) {
                dropStorage.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error dropping tables: " + e.getMessage());
        }
    }

    public void createStorageContainersTable() {
        // First drop existing tables to ensure clean slate
        dropTables();
        
        String query = "CREATE TABLE IF NOT EXISTS StorageContainers (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "name VARCHAR(255) UNIQUE, " +
                "status VARCHAR(255) DEFAULT 'active', " +
                "current_load INTEGER DEFAULT 0, " +
                "last_updated DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)";
        
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
            
            // ✅ Ensure OperationsLog table remains unchanged
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS OperationsLog (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "container_name VARCHAR(255), " +
                "operation_type VARCHAR(255), " +
                "timestamp DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (container_name) REFERENCES StorageContainers(name))"
            );
        } catch (SQLException e) {
            System.err.println("Error creating storage containers table: " + e.getMessage());
        }
    }
    

    public void addStorageContainer(String containerName) {
        String query = "INSERT INTO StorageContainers (name) VALUES (?)";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, containerName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error adding storage container: " + e.getMessage());
        }
    }

    public List<String> getStorageContainers() {
        List<String> containers = new ArrayList<>();
        String query = "SELECT name FROM StorageContainers WHERE status = 'active'";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                containers.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving storage containers: " + e.getMessage());
        }
        return containers;
    }


    public List<String> getStorageContainersSortedByLoad() {
        List<String> containers = new ArrayList<>();
        String query = "SELECT name FROM StorageContainers WHERE status = 'active' ORDER BY current_load ASC";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                containers.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving sorted storage containers: " + e.getMessage());
        }
        return containers;
    }
    

    public void updateContainerLoad(String containerName, int loadChange) {
        String query = "UPDATE StorageContainers SET current_load = current_load + ?, last_updated = CURRENT_TIMESTAMP WHERE name = ?";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, loadChange);
            stmt.setString(2, containerName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating container load: " + e.getMessage());
        }
    }
    
    public void logOperation(String containerName, String operationType) {
        String query = "INSERT INTO OperationsLog (container_name, operation_type) VALUES (?, ?)";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, containerName);
            stmt.setString(2, operationType);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error logging operation: " + e.getMessage());
        }
    }
    

    public void updateContainerStatus(String container, boolean healthy) {
        String query = "UPDATE StorageContainers SET status = ? WHERE name = ?";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, healthy ? "active" : "unhealthy");
            stmt.setString(2, container);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating container status: " + e.getMessage());
        }
    }

    public void saveChunkMetadata(FileChunk chunk) {
        String query = "INSERT INTO FileChunks (file_id, chunk_number, container_id, encryption_key) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, chunk.getFileId());
            stmt.setInt(2, chunk.getChunkNumber());
            stmt.setString(3, chunk.getContainerId());
            stmt.setString(4, chunk.getEncryptionKey());
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving chunk metadata: " + e.getMessage());
        }
    }

    public void removeStorageContainer(String containerName) {
        String query = "UPDATE StorageContainers SET status = 'inactive' WHERE name = ?";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, containerName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error removing storage container: " + e.getMessage());
        }
    }
}
