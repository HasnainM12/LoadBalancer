package com.mycompany.javafxapplication1;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import javafx.application.Platform;

public class FileChunkManager {
    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB chunks
    private final LoadBalancer loadBalancer;
    private final SecureRandom random;
    
    public FileChunkManager() {
        this.loadBalancer = LoadBalancer.getInstance();
        this.random = new SecureRandom();
    }
    
    public CompletableFuture<List<FileChunk>> splitFile(File file, long fileId) {
        return CompletableFuture.supplyAsync(() -> {
            List<FileChunk> chunks = new ArrayList<>();
            byte[] buffer = new byte[CHUNK_SIZE];
            DelayManager delayManager = DelayManager.getInstance();
            
            try {
                String encryptionKey = generateEncryptionKey();
                long totalSize = file.length();
                long processedSize = 0;
                
                try (FileInputStream fis = new FileInputStream(file)) {
                    int chunkNumber = 0;
                    int bytesRead;
                    
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        byte[] chunkData = Arrays.copyOf(buffer, bytesRead);
                        byte[] encryptedData = encryptData(chunkData, encryptionKey);
                        
                        FileOperation operation = new FileOperation(file.getName(), FileOperation.OperationType.UPLOAD, bytesRead);
                        CompletableFuture<String> containerFuture = loadBalancer.submitOperation(operation);
                        
                        // Non-blocking wait for container assignment
                        String containerId = containerFuture.join();
                        chunks.add(new FileChunk(fileId, chunkNumber++, containerId, encryptedData, encryptionKey));
                        
                        // Update progress
                        processedSize += bytesRead;
                        final double progress = (double) processedSize / totalSize;
                        Platform.runLater(() -> delayManager.progressProperty().set(progress));
                    }
                    
                    // Ensure progress reaches 100%
                    Platform.runLater(() -> delayManager.progressProperty().set(1.0));
                }
                
                return chunks;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    
    private String generateEncryptionKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, random);
        SecretKey key = keyGen.generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }
    
    private byte[] encryptData(byte[] data, String keyString) throws Exception {
        byte[] keyData = Base64.getDecoder().decode(keyString);
        SecretKey key = new SecretKeySpec(keyData, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }
    
    private byte[] decryptData(byte[] encryptedData, String keyString) throws Exception {
        byte[] keyData = Base64.getDecoder().decode(keyString);
        SecretKey key = new SecretKeySpec(keyData, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(encryptedData);
    }
    
    public void saveChunk(FileChunk chunk) throws IOException {
        String chunkPath = "storage/" + chunk.getContainerId() + "/chunk_" +
                          chunk.getFileId() + "_" + chunk.getChunkNumber();
        Files.write(Paths.get(chunkPath), chunk.getData());
    }
    
    
    public byte[] readChunk(String containerId, long fileId, int chunkNumber, String encryptionKey) throws Exception {
        String chunkPath = "storage/" + containerId + "/chunk_" + fileId + "_" + chunkNumber;
        byte[] encryptedData = Files.readAllBytes(Paths.get(chunkPath));
        return decryptData(encryptedData, encryptionKey);
    }
    
}
