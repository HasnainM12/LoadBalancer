package com.mycompany.javafxapplication1;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class Session {
    private static Session instance;
    private String username;
    private String userRole;
    private LocalDateTime lastActivity;
    private static final int SESSION_TIMEOUT_MINUTES = 1;
    private static final DateTimeFormatter SQLITE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final SessionDB sessionDB;
    private final SystemLogger logger = SystemLogger.getInstance();

    private Session() {
        sessionDB = new SessionDB();
    }

    public static Session getInstance() {
        if (instance == null) {
            instance = new Session();
            instance.restoreSession(); // Restore session when the app starts
        }
        return instance;
    }

    public void setUser(String username, String role) {
        logger.logSecurityEvent("New session started for user: " + username);
        if (this.username != null) {
            Arrays.fill(this.username.toCharArray(), '0');
        }
        System.out.println("[DEBUG] Setting user: " + username);
        this.username = username;
        this.userRole = role;
        this.lastActivity = LocalDateTime.now();
        sessionDB.saveSession(username, role);
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return userRole;
    }

    public boolean isAdmin() {
        return "admin".equals(userRole);
    }

    public boolean isValid() {
        if (username == null) {
            logger.logSecurityEvent("Invalid session: no username found");
            System.out.println("[DEBUG] Session is invalid: no username found.");
            return false;
        }
        
        String lastActivityStr = sessionDB.getSessionLastActivity(username);
        if (lastActivityStr == null) {
            logger.logSecurityEvent("Invalid session: no last activity found for " + username);
            System.out.println("[DEBUG] Session is invalid: no last activity found for " + username);
            return false;
        }
    
        try {
            LocalDateTime lastActivityTime = LocalDateTime.parse(lastActivityStr, SQLITE_FORMATTER);
            LocalDateTime now = LocalDateTime.now();
            boolean valid = lastActivityTime.plusMinutes(SESSION_TIMEOUT_MINUTES).isAfter(now);
            
            if (!valid) {
                logger.logSecurityEvent("Session timeout for user: " + username);
            }
            
            return valid;
        } catch (Exception e) {
            logger.logSecurityEvent("Error validating session: " + e.getMessage());
            System.err.println("Error parsing session timestamp: " + e.getMessage());
            return false;
        }
    }
    


    private void restoreSession() {
        System.out.println("[DEBUG] Attempting to restore session...");
        String[] sessionData = sessionDB.getSession(username);
        if (sessionData != null) {
            this.username = sessionData[0];
            this.userRole = sessionData[1];
            this.lastActivity = LocalDateTime.parse(sessionData[2], SQLITE_FORMATTER);
            System.out.println("[DEBUG] Session restored for: " + username);
        } else {
            System.out.println("[DEBUG] No active session found.");
        }
    }
    

    public void updateLastActivity() {
        this.lastActivity = LocalDateTime.now();
        sessionDB.updateSessionActivity(username);
    }

    public void clearSession() {
        System.out.println("[DEBUG] Clearing session for: " + username);
        logger.logSecurityEvent("Session ended for user: " + username);
        sessionDB.clearSession(username);
        username = null;
        userRole = null;
        lastActivity = null;
    }
    
}
