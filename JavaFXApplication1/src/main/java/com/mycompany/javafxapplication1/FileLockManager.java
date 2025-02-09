package com.mycompany.javafxapplication1;

import java.util.concurrent.*;
import java.util.Map;
import java.nio.channels.FileLock;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.io.IOException;

public class FileLockManager {
    private static FileLockManager instance;
    private final Map<Long, LockInfo> fileLocks = new ConcurrentHashMap<>();
    private final Map<Long, Semaphore> filePermits = new ConcurrentHashMap<>();
    
    private static class LockInfo {
        final String owner;
        final FileChannel channel;
        final FileLock lock;
        final long timestamp;
        
        LockInfo(String owner, FileChannel channel, FileLock lock) {
            this.owner = owner;
            this.channel = channel;
            this.lock = lock;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public static FileLockManager getInstance() {
        if (instance == null) {
            synchronized(FileLockManager.class) {
                if (instance == null) {
                    instance = new FileLockManager();
                }
            }
        }
        return instance;
    }
    
    public boolean tryLock(long fileId, String userId) {
        Semaphore permit = filePermits.computeIfAbsent(fileId, k -> new Semaphore(1, true));
        
        try {
            if (!permit.tryAcquire(30, TimeUnit.SECONDS)) {
                return false;
            }
            
            FileDB fileDB = new FileDB();
            String path = fileDB.getFileStorageLocation(fileId);
            if (path == null) {
                permit.release();
                return false;
            }
            
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                Files.createDirectories(filePath.getParent());
                Files.createFile(filePath);
            }
            
            FileChannel channel = FileChannel.open(filePath, 
                StandardOpenOption.READ, StandardOpenOption.WRITE);
                
            FileLock lock = channel.tryLock();
            if (lock != null) {
                fileLocks.put(fileId, new LockInfo(userId, channel, lock));
                return true;
            }
            
            channel.close();
            permit.release();
            return false;
            
        } catch (Exception e) {
            permit.release();
            System.err.println("Error acquiring lock: " + e.getMessage());
            return false;
        }
    }
    
    public void unlock(long fileId) {
        LockInfo lockInfo = fileLocks.remove(fileId);
        if (lockInfo != null) {
            try {
                lockInfo.lock.release();
                lockInfo.channel.close();
            } catch (IOException e) {
                System.err.println("Error releasing lock: " + e.getMessage());
            } finally {
                filePermits.get(fileId).release();
            }
        }
    }
    
    public boolean isLocked(long fileId) {
        return fileLocks.containsKey(fileId);
    }
    
    public String getLockOwner(long fileId) {
        LockInfo info = fileLocks.get(fileId);
        return info != null ? info.owner : null;
    }
}