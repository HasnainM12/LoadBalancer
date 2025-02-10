package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Statement;
public class FileDB {
    private static final String ENCRYPTION_KEY = "MySecretKey12345";
    private final FileChunkManager chunkManager = new FileChunkManager();
    private final LoadBalancer loadBalancer = LoadBalancer.getInstance();
    private final LoadBalancerDB loadBalancerDB = new LoadBalancerDB();
    private SystemLogger logger = SystemLogger.getInstance();

    public void createFileTable(Connection conn) {
        String query = "CREATE TABLE IF NOT EXISTS Files (" +
                       "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                       "filename VARCHAR(255), owner VARCHAR(255), path VARCHAR(255), " +
                       "last_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                       "FOREIGN KEY(owner) REFERENCES Users(name))";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error creating file table: " + e.getMessage());
        }
    }
    
    public void createFilePermissionsTable(Connection conn) {
        String query = "CREATE TABLE IF NOT EXISTS FilePermissions (id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                       "file_id INTEGER, " +
                       "user_id VARCHAR(255), " +
                       "can_read BOOLEAN DEFAULT false, " +
                       "can_write BOOLEAN DEFAULT false, " +
                       "FOREIGN KEY(file_id) REFERENCES Files(id), " +
                       "FOREIGN KEY(user_id) REFERENCES Users(name))";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error creating file permissions table: " + e.getMessage());
        }
    }

    public boolean setFilePermissions(long fileId, String userId, boolean canRead, boolean canWrite) {
        String query = "INSERT INTO FilePermissions (file_id, user_id, can_read, can_write) " +
                       "VALUES (?, ?, ?, ?) " +
                       "ON DUPLICATE KEY UPDATE can_read = VALUES(can_read), can_write = VALUES(can_write)";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setLong(1, fileId);
            stmt.setString(2, userId);
            stmt.setBoolean(3, canRead);
            stmt.setBoolean(4, canWrite);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error setting file permissions: " + e.getMessage());
            return false;
        }
    }
    
    public CompletableFuture<Long> addFileRecord(String filename, String owner, String path) {
        logger.logFileOperation("UPLOAD_START", filename, "Initiated by " + owner);
        DelayManager delayManager = DelayManager.getInstance();
        delayManager.resetProgress();

        SystemLogger.getInstance().logFileOperation("UPLOAD", filename, "Started");
        try {
            // existing code
            SystemLogger.getInstance().logFileOperation("UPLOAD", filename, "Completed");
        } catch (Exception e) {
            SystemLogger.getInstance().logError("Upload failed", e);
        }
    
        
        FileOperation operation = new FileOperation(
            filename, 
            FileOperation.OperationType.UPLOAD,
            new File(path).length()

    
        );
    
        return loadBalancer.submitOperation(operation)
        .thenCompose(container -> 
            delayManager.simulateDelay(30,90)
                .thenCompose(v -> {
                    Long fileId = insertFileRecord(filename, owner, path);
                    if (fileId == -1L) {
                        logger.logFileOperation("UPLOAD_FAIL", filename, "Failed to create file record");
                        return CompletableFuture.completedFuture(-1L);
                    }

                    if (!FileLockManager.getInstance().tryLock(fileId, owner)) {
                        logger.logFileOperation("UPLOAD_FAIL", filename, "Failed to acquire lock");
                        return CompletableFuture.completedFuture(-1L);
                    }

                    try {
                        File file = new File(path);
                        return chunkManager.splitFile(file, fileId)
                            .thenApply(chunks -> {
                                try {
                                    for (FileChunk chunk : chunks) {
                                        chunkManager.saveChunk(chunk);
                                        loadBalancerDB.saveChunkMetadata(chunk);
                                    }
                                    logger.logFileOperation("UPLOAD_SUCCESS", filename, "File uploaded successfully");
                                    return fileId;
                                } catch (Exception e) {
                                    logger.logError("Chunk processing failed", e);
                                    return -1L;
                                } finally {
                                    FileLockManager.getInstance().unlock(fileId);
                                }
                            });
                    } catch (Exception e) {
                        FileLockManager.getInstance().unlock(fileId);
                        logger.logError("File processing failed", e);
                        return CompletableFuture.completedFuture(-1L);
                    }
                }));
    }


    public void createChunksTable(Connection conn) {
        String query = "CREATE TABLE IF NOT EXISTS FileChunks (" +
                       "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                       "file_id INTEGER, " +
                       "chunk_number INTEGER, " +
                       "container_id VARCHAR(255), " +
                       "encryption_key TEXT, " +
                       "FOREIGN KEY(file_id) REFERENCES Files(id))";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error creating chunks table: " + e.getMessage());
        }
    }



    public String getFileStorageLocation(long fileId) {
        String query = "SELECT path FROM Files WHERE id = ?";
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                stmt.setLong(1, fileId);
                if (rs.next()) {
                    return rs.getString("path");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving file storage location: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
        return null;
    }

    
    public ObservableList<UserFile> getUserFiles(String username) {
        ObservableList<UserFile> files = FXCollections.observableArrayList();
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "SELECT id, filename, owner, path FROM Files WHERE owner = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                stmt.setString(1, username);
                while (rs.next()) {
                    files.add(new UserFile(
                        rs.getLong("id"),
                        rs.getString("filename"),
                        rs.getString("owner"),
                        rs.getString("path")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving user files: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
        return files;
    }

    private Long insertFileRecord(String filename, String owner, String path) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "INSERT INTO Files (filename, owner, path, last_modified) VALUES (?, ?, ?, CURRENT_TIMESTAMP)";
            try (PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, filename);
                stmt.setString(2, owner);
                stmt.setString(3, path);
                stmt.executeUpdate();
                
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error inserting file record: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
        return -1L;
    }

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

    public FilePermission getFilePermissions(long fileId, String userId) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "SELECT can_read, can_write FROM FilePermissions WHERE file_id = ? AND user_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                stmt.setLong(1, fileId);
                stmt.setString(2, userId);
                if (rs.next()) {
                    return new FilePermission(rs.getBoolean("can_read"), rs.getBoolean("can_write"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving file permissions: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
        return new FilePermission(false, false);
    }
    
    

    public String encryptContent(String content) throws Exception {
        SecretKey key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(content.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decryptContent(String encryptedContent) throws Exception {
        SecretKey key = new SecretKeySpec(ENCRYPTION_KEY.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedContent));
        return new String(decrypted);
    }

    public CompletableFuture<String> readFileContent(Long fileId) {
        SystemLogger logger = SystemLogger.getInstance();
        DelayManager delayManager = DelayManager.getInstance();
        delayManager.resetProgress();
        
        logger.logFileOperation("READ_START", "File ID: " + fileId, "Started");
        
        if (!FileLockManager.getInstance().tryLock(fileId, Session.getInstance().getUsername())) {
            logger.logFileOperation("READ_FAIL", "File ID: " + fileId, "Failed to acquire lock");
            return CompletableFuture.completedFuture(null);
        }
    
        FileOperation operation = new FileOperation(
            getFileName(fileId), 
            FileOperation.OperationType.DOWNLOAD,
            getFileSize(fileId)
        );
    
        return loadBalancer.submitOperation(operation)
            .thenCompose(container -> 
                delayManager.simulateDelay(30,90)
                    .thenApply(v -> {
                        try {
                            List<FileChunk> chunks = getFileChunks(fileId);
                            if (chunks.isEmpty()) {
                                logger.logFileOperation("READ_FAIL", "File ID: " + fileId, "No chunks found");
                                return null;
                            }
                            
                            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                            for (FileChunk chunk : chunks) {
                                byte[] chunkData = chunkManager.readChunk(
                                    chunk.getContainerId(),
                                    fileId,
                                    chunk.getChunkNumber(),
                                    chunk.getEncryptionKey()
                                );
                                byteStream.write(chunkData);
                            }
                            
                            logger.logFileOperation("READ_SUCCESS", "File ID: " + fileId, "File read successfully");
                            return new String(byteStream.toByteArray());
                        } catch (Exception e) {
                            logger.logError("File read failed", e);
                            return null;
                        } finally {
                            FileLockManager.getInstance().unlock(fileId);
                        }
                    })
            );
    }


    public CompletableFuture<Boolean> deleteFile(Long fileId) {
    SystemLogger logger = SystemLogger.getInstance();
    DelayManager delayManager = DelayManager.getInstance();
    delayManager.resetProgress();
    
    // Log deletion attempt
    logger.logFileOperation("DELETE_START", "File ID: " + fileId, "Started by " + Session.getInstance().getUsername());

    return delayManager.simulateDelay(30, 90)
        .thenApplyAsync(v -> {
            // First check if file exists and get metadata
            String filename = getFileName(fileId);
            if (filename == null) {
                logger.logFileOperation("DELETE_FAIL", "File ID: " + fileId, "File not found");
                return false;
            }

            // Try to acquire lock
            if (!FileLockManager.getInstance().tryLock(fileId, Session.getInstance().getUsername())) {
                logger.logFileOperation("DELETE_FAIL", filename, "Could not acquire lock - file may be in use");
                return false;
            }

            try (Connection conn = DBConnection.getMySQLConnection()) {
                conn.setAutoCommit(false);  // Start transaction
                
                try {
                    // Delete file chunks first
                    List<FileChunk> chunks = getFileChunks(fileId);
                    for (FileChunk chunk : chunks) {
                        // Delete physical chunk file
                        File chunkFile = new File("storage/" + chunk.getContainerId() + 
                            "/chunk_" + chunk.getFileId() + "_" + chunk.getChunkNumber());
                        if (chunkFile.exists() && !chunkFile.delete()) {
                            throw new IOException("Failed to delete chunk file: " + chunkFile.getPath());
                        }
                        
                        // Delete chunk metadata
                        String deleteChunkQuery = "DELETE FROM FileChunks WHERE file_id = ? AND chunk_number = ?";
                        try (PreparedStatement stmt = conn.prepareStatement(deleteChunkQuery)) {
                            stmt.setLong(1, fileId);
                            stmt.setInt(2, chunk.getChunkNumber());
                            stmt.executeUpdate();
                        }
                    }

                    // Delete file permissions
                    String deletePermsQuery = "DELETE FROM FilePermissions WHERE file_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(deletePermsQuery)) {
                        stmt.setLong(1, fileId);
                        stmt.executeUpdate();
                    }

                    // Finally delete the file record
                    String deleteFileQuery = "DELETE FROM Files WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(deleteFileQuery)) {
                        stmt.setLong(1, fileId);
                        int result = stmt.executeUpdate();
                        
                        if (result > 0) {
                            conn.commit();
                            logger.logFileOperation("DELETE_SUCCESS", filename, "File and all associated data deleted");
                            return true;
                        } else {
                            conn.rollback();
                            logger.logFileOperation("DELETE_FAIL", filename, "No file record found to delete");
                            return false;
                        }
                    }

                } catch (Exception e) {
                    conn.rollback();
                    logger.logError("File deletion failed", e);
                    return false;
                }
                
            } catch (SQLException e) {
                logger.logError("Database connection failed during file deletion", e);
                return false;
            } finally {
                FileLockManager.getInstance().unlock(fileId);
            }
        });
}
    


    private List<FileChunk> getFileChunks(Long fileId) {
        List<FileChunk> chunks = new ArrayList<>();
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "SELECT chunk_number, container_id, encryption_key FROM FileChunks WHERE file_id = ? ORDER BY chunk_number";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                stmt.setLong(1, fileId);
                while (rs.next()) {
                    chunks.add(new FileChunk(
                        fileId,
                        rs.getInt("chunk_number"),
                        rs.getString("container_id"),
                        new byte[0], // Data will be loaded when needed
                        rs.getString("encryption_key")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving file chunks: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
        return chunks;
    }

    private String getFileName(Long fileId) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "SELECT filename FROM Files WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                stmt.setLong(1, fileId);
                if (rs.next()) {
                    return rs.getString("filename");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving filename: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
        return "";
    }

    private long getFileSize(Long fileId) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "SELECT path FROM Files WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                stmt.setLong(1, fileId);
                if (rs.next()) {
                    File file = new File(rs.getString("path"));
                    return file.length();
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving file size: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
        return 0;
    }

    public long getFileId(String filename, String owner) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            String query = "SELECT id FROM Files WHERE filename = ? AND owner = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                stmt.setString(1, filename);
                stmt.setString(2, owner);
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving file ID: " + e.getMessage());
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
        return -1;
    }

    public boolean setFilePermissions(String filename, String owner, String targetUser, boolean canRead, boolean canWrite) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            long fileId = getFileId(filename, owner);
            if (fileId < 0) {
                System.err.println("File not found for the given filename and owner.");
                return false;
            }
            return setFilePermissions(fileId, targetUser, canRead, canWrite);
        } catch (SQLException e) {
            System.err.println("Error setting file permissions: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
    }

    public FilePermission getFilePermissions(String filename, String owner, String user) {
        Connection conn = null;
        try {
            conn = DBConnection.getMySQLConnection();
            long fileId = getFileId(filename, owner);
            if (fileId < 0) {
                System.err.println("File not found for the given filename and owner.");
                return null;
            }
            return getFilePermissions(fileId, user);
        } catch (SQLException e) {
            System.err.println("Error getting file permissions: " + e.getMessage());
            return new FilePermission(false, false);
        } finally {
            if (conn != null) {
                DBConnection.releaseConnection(conn);
            }
        }
    }

    public CompletableFuture<Boolean> updateFile(Long fileId, String encryptedContent) {
        DelayManager delayManager = DelayManager.getInstance();
        delayManager.resetProgress();
        
        return delayManager.simulateDelay(30,90)
            .thenApplyAsync(v -> {
                Connection conn = null;
                try {
                    String filePath = getFileStorageLocation(fileId);
                    if (filePath == null) return false;
                    
                    conn = DBConnection.getMySQLConnection();
                    String query = "UPDATE Files SET last_modified = CURRENT_TIMESTAMP WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(query)) {
                        stmt.setLong(1, fileId);
                        stmt.executeUpdate();
                    }
                    
                    try (FileWriter writer = new FileWriter(filePath)) {
                        writer.write(encryptedContent);
                    }
                    return true;
                } catch (Exception e) {
                    System.err.println("Error updating file: " + e.getMessage());
                    return false;
                } finally {
                    if (conn != null) {
                        DBConnection.releaseConnection(conn);
                    }
                }
            });
    }
}
