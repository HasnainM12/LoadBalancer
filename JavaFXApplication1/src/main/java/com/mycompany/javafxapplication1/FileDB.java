package com.mycompany.javafxapplication1;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.Base64;

public class FileDB {
    private static final String ENCRYPTION_KEY = "MySecretKey12345";
    private SystemLogger logger = SystemLogger.getInstance();

    public void createFileTable(Connection conn) throws SQLException {
        String query = "CREATE TABLE IF NOT EXISTS Files (" +
                       "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                       "filename VARCHAR(255), owner VARCHAR(255), path VARCHAR(255), " +
                       "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                       "FOREIGN KEY(owner) REFERENCES Users(name))";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }

    public void createFilePermissionsTable(Connection conn) throws SQLException {
        String query = "CREATE TABLE IF NOT EXISTS FilePermissions (" +
                       "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                       "file_id INTEGER, " +
                       "user_id VARCHAR(255), " +
                       "can_read BOOLEAN DEFAULT false, " +
                       "can_write BOOLEAN DEFAULT false, " +
                       "FOREIGN KEY(file_id) REFERENCES Files(id), " +
                       "FOREIGN KEY(user_id) REFERENCES Users(name))";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }

    public boolean setFilePermissions(long fileId, String userId, boolean canRead, boolean canWrite) {
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO FilePermissions (file_id, user_id, can_read, can_write) " +
                "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE can_read = VALUES(can_read), can_write = VALUES(can_write)")) {
            stmt.setLong(1, fileId);
            stmt.setString(2, userId);
            stmt.setBoolean(3, canRead);
            stmt.setBoolean(4, canWrite);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.log(SystemLogger.LogLevel.ERROR, "Error setting file permissions: " + e.getMessage());
            return false;
        }
    }

    public FilePermission getFilePermissions(Long fileId, String userId) {
        String query = "SELECT can_read, can_write FROM FilePermissions WHERE file_id = ? AND user_id = ?";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, fileId);
            stmt.setString(2, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new FilePermission(rs.getBoolean("can_read"), rs.getBoolean("can_write"));
            }
        } catch (SQLException e) {
            logger.log(SystemLogger.LogLevel.ERROR, "Error retrieving file permissions: " + e.getMessage());
        }
        return new FilePermission(false, false);
    }

    public ObservableList<UserFile> getUserFiles(String username) {
        ObservableList<UserFile> files = FXCollections.observableArrayList();
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "SELECT id, filename, owner FROM Files WHERE owner = ?")) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                files.add(new UserFile(
                    rs.getLong("id"),
                    rs.getString("filename"),
                    rs.getString("owner"),
                    null
                ));
            }
        } catch (SQLException e) {
            logger.log(SystemLogger.LogLevel.ERROR, "Error retrieving user files: " + e.getMessage());
        }
        return files;
    }



    public Long addFileMetadata(String filename, String owner, String filePath) {
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "INSERT INTO Files (filename, owner, path) VALUES (?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, filename);
            stmt.setString(2, owner);
            stmt.setString(3, filePath);
            stmt.executeUpdate();
    
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to store file metadata: " + e.getMessage());
        }
        return -1L;
    }

    public boolean updateFile(Long fileId, String newContent) {
        String filePath = getFilePath(fileId);
        if (filePath == null || filePath.isEmpty()) {
            System.err.println("[ERROR] Cannot update file: File path not found for ID " + fileId);
            return false;
        }

        try {
            Files.write(Paths.get(filePath), newContent.getBytes());
            return true;
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to update file: " + e.getMessage());
            return false;
        }
    }


    public boolean deleteFileMetadata(Long fileId) {
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "DELETE FROM Files WHERE id = ?")) {
            stmt.setLong(1, fileId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to delete file metadata: " + e.getMessage());
            return false;
        }
    }

    public Long getFileIdByFilename(String filename) {
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT id FROM Files WHERE filename = ?")) {
            stmt.setString(1, filename);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to retrieve file ID: " + e.getMessage());
        }
        return null;
    }
    

    public String getFileContent(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                logger.logError("File not found: " + filePath, null);
                return null;
            }
    
            // ✅ Directly read the file (no decryption)
            return new String(Files.readAllBytes(path));
        } catch (Exception e) {
            logger.logError("Error retrieving file: " + filePath, e);
            return null;
        }
    }
    
    

    public String getFilePath(Long fileId) {
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT path FROM Files WHERE id = ?")) {
            stmt.setLong(1, fileId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("path");
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Failed to retrieve file path: " + e.getMessage());
        }
        return null;
    }
    
    

    public boolean deleteFile(Long fileId) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            String query = "DELETE FROM Files WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setLong(1, fileId);
                int result = stmt.executeUpdate();
                if (result > 0) {
                    logger.logFileOperation("DELETE_SUCCESS", "File ID: " + fileId, "File deleted");
                    return true;
                }
                logger.logFileOperation("DELETE_FAIL", "File ID: " + fileId, "No file found");
                return false;
            }
        } catch (SQLException e) {
            logger.logError("Database error during file deletion", e);
            return false;
        }
    }

    // Inner class for file permissions
    public static class FilePermission {
        private final boolean canRead;
        private final boolean canWrite;

        public FilePermission(boolean canRead, boolean canWrite) {
            this.canRead = canRead;
            this.canWrite = canWrite;
        }

        public boolean canRead() { return canRead; }
        public boolean canWrite() { return canWrite; }
    }
}
