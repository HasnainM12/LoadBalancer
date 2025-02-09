package com.mycompany.javafxapplication1;

public class FileChunk {
    private final long fileId;
    private final int chunkNumber;
    private final String containerId;
    private final byte[] data;
    private final String encryptionKey;
    
    public FileChunk(long fileId, int chunkNumber, String containerId, byte[] data, String encryptionKey) {
        this.fileId = fileId;
        this.chunkNumber = chunkNumber;
        this.containerId = containerId;
        this.data = data;
        this.encryptionKey = encryptionKey;
    }
    
    // Getters
    public long getFileId() { return fileId; }
    public int getChunkNumber() { return chunkNumber; }
    public String getContainerId() { return containerId; }
    public byte[] getData() { return data; }
    public String getEncryptionKey() { return encryptionKey; }
}