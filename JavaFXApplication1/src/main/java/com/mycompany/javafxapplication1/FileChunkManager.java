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
import javafx.application.Platform;

public class FileChunkManager {
    private static final int CHUNK_SIZE = 1024 * 1024; // 1MB chunks
    private SecureRandom random;
    private LoadBalancer loadBalancer; // Remove final
    private final FileDB fileDB;
    
    public FileChunkManager(FileDB fileDB) {
        this.random = new SecureRandom();
        this.fileDB = fileDB;
    }
    
    public void setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
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
                
                System.out.println("Starting file split: " + file.getName() + ", Size: " + totalSize);
                
                try (FileInputStream fis = new FileInputStream(file)) {
                    int chunkNumber = 0;
                    int bytesRead;
                    
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        byte[] chunkData = Arrays.copyOf(buffer, bytesRead);
                        byte[] encryptedData = encryptData(chunkData, encryptionKey);
                        
                        FileOperation operation = new FileOperation(
                            file.getName(), 
                            FileOperation.OperationType.UPLOAD,
                            bytesRead
                        );
                        
                        String containerId = loadBalancer.submitOperation(operation).join();
                        System.out.println("Assigned to container: " + containerId);
                        
                        chunks.add(new FileChunk(fileId, chunkNumber, containerId, encryptedData, encryptionKey));
                        
                        processedSize += bytesRead;
                        final double progress = (double) processedSize / totalSize;
                        Platform.runLater(() -> delayManager.progressProperty().set(progress));
                        
                        System.out.println(String.format("Chunk %d processed: %.2f%%", chunkNumber, progress * 100));
                        chunkNumber++;
                    }
                    
                    Platform.runLater(() -> delayManager.progressProperty().set(1.0));
                    System.out.println("File split complete: " + chunks.size() + " chunks created");
                }
                
                return chunks;
            } catch (Exception e) {
                System.err.println("Error splitting file: " + e.getMessage());
                e.printStackTrace();
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
        Path storagePath = Paths.get("storage", chunk.getContainerId());
        Files.createDirectories(storagePath);
        
        Path chunkPath = storagePath.resolve(String.format("chunk_%d_%d", 
            chunk.getFileId(), chunk.getChunkNumber()));
            
        Files.write(chunkPath, chunk.getData());
        System.out.println("Saved chunk to: " + chunkPath);
    }
    
    public byte[] readChunk(String containerId, long fileId, int chunkNumber, String encryptionKey) 
            throws Exception {
        Path chunkPath = Paths.get("storage", containerId, 
            String.format("chunk_%d_%d", fileId, chunkNumber));
            
        System.out.println("Reading chunk from: " + chunkPath);
        byte[] encryptedData = Files.readAllBytes(chunkPath);
        return decryptData(encryptedData, encryptionKey);
    }
}