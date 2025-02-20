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
import java.util.List;
import java.util.Map;
import java.io.File;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.Channel;



public class FileDB {
    private static final String ENCRYPTION_KEY = "MySecretKey12345";
    private SystemLogger logger = SystemLogger.getInstance();

    public void createFileTable(Connection conn) throws SQLException {
        String query = "CREATE TABLE IF NOT EXISTS Files (" +
            "id INT AUTO_INCREMENT PRIMARY KEY, " +
            "filename VARCHAR(255) NOT NULL, " +
            "owner VARCHAR(255) NOT NULL, " +
            "path VARCHAR(500) NOT NULL)";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }

    void createFilePermissionsTable(Connection conn) throws SQLException {
        String query = "CREATE TABLE IF NOT EXISTS FilePermissions ("
                     + "id INTEGER PRIMARY KEY AUTO_INCREMENT, "
                     + "file_id INTEGER, "
                     + "user_id VARCHAR(255), "
                     + "can_read BOOLEAN DEFAULT false, "
                     + "can_write BOOLEAN DEFAULT false, "
                     + "UNIQUE (file_id, user_id), "
                     + "FOREIGN KEY (file_id) REFERENCES Files(id), "
                     + "FOREIGN KEY (user_id) REFERENCES Users(name))";
    
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
        
        try (Connection conn = DBConnection.getMySQLConnection()) {
            String query = "SELECT DISTINCT f.id, f.filename, f.owner, f.path " +
                        "FROM Files f " +
                        "LEFT JOIN FilePermissions fp ON f.id = fp.file_id " +
                        "WHERE f.owner = ? OR (fp.user_id = ? AND fp.can_read = true)";
                        
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                stmt.setString(2, username);
                
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Long id = rs.getLong("id");
                    String filename = rs.getString("filename");
                    String owner = rs.getString("owner");
                    String path = rs.getString("path");
                    
                    // Check if file exists in container via SSH
                    // Check if file exists in container via SSH
                    String[] pathParts = path.split(":");
                    if (pathParts.length == 2) {
                        String container = pathParts[0];
                        String remotePath = pathParts[1];

                        if (verifyFileInContainer(container, remotePath)) {
                            files.add(new UserFile(id, filename, owner, path));
                        }
                    }

                }
            }
        } catch (SQLException e) {
            logger.log(SystemLogger.LogLevel.ERROR, "Error retrieving user files: " + e.getMessage());
        }
        
        return files;
    }

    public boolean verifyFileInContainer(String container, String remotePath) {
        try {
            // SSH directly into the container and check if the file exists
            JSch jsch = new JSch();
            Session sshSession = jsch.getSession("ntu-user", container, 22);
            sshSession.setPassword("ntu-user");
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.connect(5000);
    
            Channel channel = sshSession.openChannel("sftp");
            channel.connect(5000);
            ChannelSftp sftpChannel = (ChannelSftp) channel;
    
            try {
                sftpChannel.lstat(remotePath); // Directly check in `/files/`
                return true;
            } catch (SftpException e) {
                return false;
            } finally {
                sftpChannel.exit();
                sshSession.disconnect();
            }
        } catch (Exception e) {
            logger.log(SystemLogger.LogLevel.ERROR, "Error verifying file in container: " + e.getMessage());
            return false;
        }
    }


    public boolean verifyFileExists(String containerPath) {
        String[] parts = containerPath.split(":");
        if (parts.length != 2) {
            logger.logError("Invalid file path format: " + containerPath, null);
            return false;
        }
    
        String container = parts[0];
        String remotePath = parts[1];
    
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp sftpChannel = null;
    
        try {
            session = jsch.getSession("ntu-user", container, 22);
            session.setPassword("ntu-user");
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(5000);
    
            Channel channel = session.openChannel("sftp");
            channel.connect(5000);
            sftpChannel = (ChannelSftp) channel;
    
            // Try to get file attributes - will throw exception if file doesn't exist
            sftpChannel.lstat(remotePath);
            return true;
        } catch (Exception e) {
            logger.logError("Failed to verify file existence: " + containerPath, e);
            return false;
        } finally {
            if (sftpChannel != null) {
                try {
                    sftpChannel.exit();
                } catch (Exception e) {
                    logger.logError("Error closing SFTP channel", e);
                }
            }
            if (session != null) {
                try {
                    session.disconnect();
                } catch (Exception e) {
                    logger.logError("Error closing SSH session", e);
                }
            }
        }
    }
    

    Long addFileMetadata(String filename, String owner, String filePath) {
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
                    Long fileId = rs.getLong(1);
    
                    // Automatically grant full permissions to the file owner
                    boolean permissionSet = setFilePermissions(fileId, owner, true, true);
                    if (!permissionSet) {
                        System.err.println("[ERROR] Failed to set file permissions for owner: " + owner);
                    }
    
                    return fileId;
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
    
    public String getFileContent(String containerPath) {
        String[] pathParts = containerPath.split(":");
        if (pathParts.length != 2) {
            logger.logError("Invalid file path format: " + containerPath, null);
            return null;
        }
    
        String container = pathParts[0];
        String remotePath = pathParts[1];
    
        // Create a temporary file to store the downloaded content
        File tempFile = null;
        try {
            tempFile = File.createTempFile("download", ".tmp");
            
            JSch jsch = new JSch();
            Session sshSession = jsch.getSession("ntu-user", container, 22);
            sshSession.setPassword("ntu-user");
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.connect(5000);
    
            Channel channel = sshSession.openChannel("sftp");
            channel.connect(5000);
            ChannelSftp sftpChannel = (ChannelSftp) channel;
    
            try {
                // Download the file to temp location
                sftpChannel.get(remotePath, tempFile.getAbsolutePath());
                
                // Read the content
                return new String(Files.readAllBytes(tempFile.toPath()));
            } finally {
                sftpChannel.exit();
                sshSession.disconnect();
                if (tempFile != null) {
                    tempFile.delete(); // Clean up temp file
                }
            }
        } catch (Exception e) {
            logger.logError("Error retrieving file content: " + containerPath, e);
            if (tempFile != null) {
                tempFile.delete(); // Ensure temp file is cleaned up on error
            }
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



    public boolean downloadFile(Long fileId, String localPath) {
        String containerPath = getFilePath(fileId);
        if (containerPath == null) {
            logger.logError("File path not found for ID: " + fileId, null);
            return false;
        }
    
        String[] pathParts = containerPath.split(":");
        if (pathParts.length != 2) {
            logger.logError("Invalid file path format: " + containerPath, null);
            return false;
        }
    
        String container = pathParts[0];
        String remotePath = pathParts[1];
    
        try {
            JSch jsch = new JSch();
            Session sshSession = jsch.getSession("ntu-user", container, 22);
            sshSession.setPassword("ntu-user");
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.connect(5000);
    
            Channel channel = sshSession.openChannel("sftp");
            channel.connect(5000);
            ChannelSftp sftpChannel = (ChannelSftp) channel;
    
            // Create parent directory if it doesn't exist
            File localFile = new File(localPath);
            if (!localFile.getParentFile().exists()) {
                localFile.getParentFile().mkdirs();
            }

            if (!verifyFileExists(containerPath)) {
                logger.logError("File does not exist: " + containerPath, null);
                return false;
            }
    
            // Download the file
            sftpChannel.get(remotePath, localPath);
    
            sftpChannel.exit();
            sshSession.disconnect();
            return true;
        } catch (Exception e) {
            logger.logError("Failed to download file", e);
            return false;
        }
    }
    
    public boolean deleteFile(Long fileId) {
        String containerPath = getFilePath(fileId);
        if (containerPath == null) {
            logger.logError("File path not found for ID: " + fileId, null);
            return false;
        }
    
        String[] pathParts = containerPath.split(":");
        if (pathParts.length != 2) {
            logger.logError("Invalid file path format: " + containerPath, null);
            return false;
        }

        if (!verifyFileExists(containerPath)) {
            logger.logError("File does not exist: " + containerPath, null);
            return false;
        }
    
        String container = pathParts[0];
        String remotePath = pathParts[1];
    
        try {
            // First delete the file from the container
            JSch jsch = new JSch();
            Session sshSession = jsch.getSession("ntu-user", container, 22);
            sshSession.setPassword("ntu-user");
            sshSession.setConfig("StrictHostKeyChecking", "no");
            sshSession.connect(5000);
    
            Channel channel = sshSession.openChannel("sftp");
            channel.connect(5000);
            ChannelSftp sftpChannel = (ChannelSftp) channel;
    
            try {
                sftpChannel.rm(remotePath);
            } finally {
                sftpChannel.exit();
                sshSession.disconnect();
            }
    
            // Then delete the metadata from the database
            try (Connection conn = DBConnection.getMySQLConnection();
                 PreparedStatement stmt = conn.prepareStatement("DELETE FROM Files WHERE id = ?")) {
                stmt.setLong(1, fileId);
                return stmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            logger.logError("Failed to delete file", e);
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
