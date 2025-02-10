package com.mycompany.javafxapplication1;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.nio.file.*;

public class SystemLogger {
    private static SystemLogger instance;
    private final BlockingQueue<LogEntry> logQueue = new LinkedBlockingQueue<>();
    private final String LOG_DIR = System.getProperty("user.dir") + File.separator + "logs";
    private final String SYSTEM_LOG = "system.log";
    private final String ACCESS_LOG = "access.log";
    private final String ERROR_LOG = "error.log";
    
    private static class LogEntry {
        final LogLevel level;
        final String message;
        final LocalDateTime timestamp;
        final String username;
        
        LogEntry(LogLevel level, String message, String username) {
            this.level = level;
            this.message = message;
            this.timestamp = LocalDateTime.now();
            this.username = username;
        }
    }
    
    public enum LogLevel {
        INFO, WARN, ERROR, AUDIT
    }
    
    private SystemLogger() {
        initializeLogDirectory();
        startLoggingThread();
    }
    
    public static SystemLogger getInstance() {
        if (instance == null) {
            synchronized(SystemLogger.class) {
                if (instance == null) {
                    instance = new SystemLogger();
                }
            }
        }
        return instance;
    }
    
    private void initializeLogDirectory() {
        try {
            Files.createDirectories(Paths.get(LOG_DIR));
        } catch (IOException e) {
            System.err.println("Failed to create log directory: " + e.getMessage());
        }
    }
    
    private void startLoggingThread() {
        Thread loggingThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    LogEntry entry = logQueue.take();
                    writeLogEntry(entry);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        loggingThread.setDaemon(true);
        loggingThread.start();
    }
    
    private void writeLogEntry(LogEntry entry) {
        String logFile;
        switch(entry.level) {
            case ERROR:
                logFile = LOG_DIR + "/" + ERROR_LOG;
                break;
            case AUDIT:
                logFile = LOG_DIR + "/" + ACCESS_LOG;
                break;
            default:
                logFile = LOG_DIR + "/" + SYSTEM_LOG;
        }
        String formattedEntry = String.format("[%s] [%s] [%s] %s%n",
            entry.timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            entry.level,
            entry.username,
            entry.message
        );
        
        try {
            Files.write(Paths.get(logFile), 
                formattedEntry.getBytes(), 
                StandardOpenOption.CREATE, 
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.err.println("Failed to write log entry: " + e.getMessage());
        }
    }
    
    public void log(LogLevel level, String message) {
        String username = Session.getInstance().getUsername();
        logQueue.offer(new LogEntry(level, message, username));
    }
    
    public void logFileOperation(String operation, String filename, String result) {
        String username = Session.getInstance().getUsername();
        String message = String.format("File operation: %s on %s - %s", operation, filename, result);
        logQueue.offer(new LogEntry(LogLevel.AUDIT, message, username));
    }
    
    public void logSecurityEvent(String event) {
        String username = Session.getInstance().getUsername();
        logQueue.offer(new LogEntry(LogLevel.AUDIT, event, username));
    }
    
    public void logError(String error, Exception e) {
        String username = Session.getInstance().getUsername();
        String message = String.format("%s: %s", error, e.getMessage());
        logQueue.offer(new LogEntry(LogLevel.ERROR, message, username));
    }
}