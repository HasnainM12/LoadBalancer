package com.mycompany.javafxapplication1;

public class FileOperation {
    private final String filename;
    private final OperationType type;
    private final long timestamp;
    private final long size; // Add a size field

    public enum OperationType {
        UPLOAD, DOWNLOAD, DELETE
    }

    public FileOperation(String filename, OperationType type, long size) {
        this.filename = filename;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.size = size; // Initialize the size field
    }

    public String getFilename() { return filename; }
    public OperationType getType() { return type; }
    public long getTimestamp() { return timestamp; }

    public long getEstimatedProcessingTime() {
        long baseTime;
        switch(type) {
            case UPLOAD: baseTime = 1000; break;
            case DOWNLOAD: baseTime = 800; break;
            case DELETE: baseTime = 500; break;
            default: baseTime = 1000;
        }
        return baseTime + (size / 1024 / 1024 * 100); // Time scales with file size
    }
}
