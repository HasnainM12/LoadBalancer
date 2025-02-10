package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LoadBalancerDB {
    private final ConcurrentHashMap<String, Boolean> containerHealth;
    private final ScheduledExecutorService healthChecker;

    public LoadBalancerDB() {
        this.containerHealth = new ConcurrentHashMap<>();
        this.healthChecker = Executors.newSingleThreadScheduledExecutor();
        initializeHealthMonitoring();
    }

    private void initializeHealthMonitoring() {
        healthChecker.scheduleAtFixedRate(() -> {
            for (String containerName : getStorageContainers()) {
                checkContainerHealth(containerName);
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void checkContainerHealth(String containerName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "inspect", "-f", "{{.State.Running}}", containerName);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            if (process.waitFor(5, TimeUnit.SECONDS)) {
                boolean isHealthy = process.exitValue() == 0;
                boolean previousHealth = containerHealth.getOrDefault(containerName, false);
                
                if (previousHealth != isHealthy) {
                    containerHealth.put(containerName, isHealthy);
                    updateContainerStatus(containerName, isHealthy);
                    System.out.println("Container " + containerName + " health changed: " + previousHealth + " -> " + isHealthy);
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking container health: " + e.getMessage());
            containerHealth.put(containerName, false);
        }
    }

    public void createStorageContainersTable(Connection conn) {
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
        } catch (SQLException e) {
            System.err.println("Error creating storage containers table: " + e.getMessage());
        }
    }

    public boolean containerExists(String containerName) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "SELECT COUNT(*) FROM StorageContainers WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                stmt.setString(1, containerName);
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking container existence: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
        return false;
    }

    public void addStorageContainer(String containerName) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            if (containerExists(containerName)) {
                System.out.println("Container " + containerName + " already exists");
                return;
            }

            String query = "INSERT INTO StorageContainers (name, status) VALUES (?, 'active')";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, containerName);
                stmt.executeUpdate();
                containerHealth.put(containerName, true);
            }
        } catch (SQLException e) {
            System.err.println("Error adding storage container: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
    }

    public List<String> getStorageContainers() {
        List<String> containers = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "SELECT name FROM StorageContainers WHERE status = 'active'";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String containerName = rs.getString("name");
                    if (containerHealth.getOrDefault(containerName, false)) {
                        containers.add(containerName);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving storage containers: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
        return containers;
    }

    public List<String> getStorageContainersSortedByLoad() {
        List<String> containers = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "SELECT name FROM StorageContainers WHERE status = 'active' ORDER BY current_load ASC";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String containerName = rs.getString("name");
                    if (containerHealth.getOrDefault(containerName, false)) {
                        containers.add(containerName);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving sorted storage containers: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
        return containers;
    }

    public void updateContainerLoad(String containerName, int loadChange) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "UPDATE StorageContainers SET current_load = current_load + ?, last_updated = CURRENT_TIMESTAMP WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setInt(1, loadChange);
                stmt.setString(2, containerName);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error updating container load: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
    }
    
    public void logOperation(String containerName, String operationType) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "INSERT INTO OperationsLog (container_name, operation_type) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, containerName);
                stmt.setString(2, operationType);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error logging operation: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
    }

    public void updateContainerStatus(String container, boolean healthy) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "UPDATE StorageContainers SET status = ? WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, healthy ? "active" : "unhealthy");
                stmt.setString(2, container);
                stmt.executeUpdate();
                containerHealth.put(container, healthy);
            }
        } catch (SQLException e) {
            System.err.println("Error updating container status: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
    }

    public void saveChunkMetadata(FileChunk chunk) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "INSERT INTO FileChunks (file_id, chunk_number, container_id, encryption_key) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setLong(1, chunk.getFileId());
                stmt.setInt(2, chunk.getChunkNumber());
                stmt.setString(3, chunk.getContainerId());
                stmt.setString(4, chunk.getEncryptionKey());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error saving chunk metadata: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
    }

    public void removeStorageContainer(String containerName) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "UPDATE StorageContainers SET status = 'inactive' WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, containerName);
                stmt.executeUpdate();
                containerHealth.remove(containerName);
            }
        } catch (SQLException e) {
            System.err.println("Error removing storage container: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
    }

    public void shutdown() {
        healthChecker.shutdown();
        try {
            if (!healthChecker.awaitTermination(5, TimeUnit.SECONDS)) {
                healthChecker.shutdownNow();
            }
        } catch (InterruptedException e) {
            healthChecker.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
