package com.mycompany.javafxapplication1;

public class FileOperation {
    private final String filename;
    private final OperationType type;
    private final long timestamp;
    private final long size;

    public enum OperationType {
        UPLOAD, DOWNLOAD, DELETE, READ  // Added READ operation type
    }

    public FileOperation(String filename, OperationType type, long size) {
        this.filename = filename;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.size = size;
    }

    public String getFilename() { return filename; }
    public OperationType getType() { return type; }
    public long getTimestamp() { return timestamp; }
    private String filePath;
    public FileOperation setFilePath(String path) { 
        this.filePath = path;
        return this;
    }
    public String getFilePath() { return this.filePath; }

    public long getEstimatedProcessingTime() {
        long baseTime;
        switch(type) {
            case UPLOAD: baseTime = 1000; break;
            case DOWNLOAD: baseTime = 800; break;
            case DELETE: baseTime = 500; break;
            case READ: baseTime = 300; break;  // Added processing time for READ
            default: baseTime = 1000;
        }
        return baseTime + (size / 1024 / 1024 * 100);
    }
}