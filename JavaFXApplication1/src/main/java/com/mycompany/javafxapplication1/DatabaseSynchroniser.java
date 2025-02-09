package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.mycompany.javafxapplication1.SystemLogger.LogLevel;

public class DatabaseSynchroniser {
    private static DatabaseSynchroniser instance;
    private final SystemLogger logger = SystemLogger.getInstance();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int SYNC_INTERVAL_SECONDS = 300; // 5 minutes

    private DatabaseSynchroniser() {
        startPeriodicSync();
    }

    public static DatabaseSynchroniser getInstance() {
        if (instance == null) {
            synchronized(DatabaseSynchroniser.class) {
                if (instance == null) {
                    instance = new DatabaseSynchroniser();
                }
            }
        }
        return instance;
    }

    private void startPeriodicSync() {
        scheduler.scheduleAtFixedRate(this::synchroniseDatabases,
            0, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void synchroniseDatabases() {
        try {
            synchroniseSessions();
            synchroniseFiles();
            logger.log(LogLevel.INFO, "Database synchronisation completed");
        } catch (Exception e) {
            logger.logError("Database synchronisation failed", e);
        }
    }

    private void synchroniseSessions() throws SQLException {
        Map<String, SessionData> sqliteSessions = getSessionsFromSQLite();
        Map<String, SessionData> mysqlSessions = getSessionsFromMySQL();

        // Resolve conflicts based on timestamp
        for (Map.Entry<String, SessionData> entry : sqliteSessions.entrySet()) {
            String username = entry.getKey();
            SessionData localData = entry.getValue();
            SessionData remoteData = mysqlSessions.get(username);

            if (remoteData == null || localData.lastActivity.isAfter(remoteData.lastActivity)) {
                updateMySQLSession(username, localData);
            } else {
                updateSQLiteSession(username, remoteData);
            }
        }
    }

    private void synchroniseFiles() throws SQLException {
        try (Connection sqliteConn = DBConnection.getSQLiteConnection();
             Connection mysqlConn = DBConnection.getMySQLConnection()) {

            // Get timestamps from both databases
            Map<Long, Timestamp> sqliteTimestamps = getFileTimestamps(sqliteConn);
            Map<Long, Timestamp> mysqlTimestamps = getFileTimestamps(mysqlConn);

            // Sync files that are newer in SQLite to MySQL
            for (Map.Entry<Long, Timestamp> entry : sqliteTimestamps.entrySet()) {
                Long fileId = entry.getKey();
                Timestamp localTime = entry.getValue();
                Timestamp remoteTime = mysqlTimestamps.get(fileId);

                if (remoteTime == null || localTime.after(remoteTime)) {
                    syncFileToMySQL(fileId, sqliteConn, mysqlConn);
                }
            }

            // Sync files that are newer in MySQL to SQLite
            for (Map.Entry<Long, Timestamp> entry : mysqlTimestamps.entrySet()) {
                Long fileId = entry.getKey();
                Timestamp remoteTime = entry.getValue();
                Timestamp localTime = sqliteTimestamps.get(fileId);

                if (localTime == null || remoteTime.after(localTime)) {
                    syncFileToSQLite(fileId, mysqlConn, sqliteConn);
                }
            }
        }
    }

    private Map<String, SessionData> getSessionsFromSQLite() throws SQLException {
        Map<String, SessionData> sessions = new HashMap<>();
        String query = "SELECT username, user_role, last_activity FROM Sessions";

        try (Connection conn = DBConnection.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                sessions.put(rs.getString("username"),
                    new SessionData(rs.getString("user_role"),
                        LocalDateTime.parse(rs.getString("last_activity"))));
            }
        }
        return sessions;
    }

    private Map<String, SessionData> getSessionsFromMySQL() throws SQLException {
        Map<String, SessionData> sessions = new HashMap<>();
        String query = "SELECT username, user_role, last_activity FROM Sessions";

        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                sessions.put(rs.getString("username"),
                    new SessionData(rs.getString("user_role"),
                        rs.getTimestamp("last_activity").toLocalDateTime()));
            }
        }
        return sessions;
    }

    private static class SessionData {
        final String role;
        final LocalDateTime lastActivity;

        SessionData(String role, LocalDateTime lastActivity) {
            this.role = role;
            this.lastActivity = lastActivity;
        }
    }

    private Map<Long, Timestamp> getFileTimestamps(Connection conn) throws SQLException {
        Map<Long, Timestamp> timestamps = new HashMap<>();
        String query = "SELECT id, last_modified FROM Files";
        
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                timestamps.put(rs.getLong("id"), rs.getTimestamp("last_modified"));
            }
        }
        return timestamps;
    }

    private void syncFileToMySQL(Long fileId, Connection sqliteConn, Connection mysqlConn) throws SQLException {
        String selectQuery = "SELECT * FROM Files WHERE id = ?";
        String insertQuery = "INSERT INTO Files (id, filename, owner, path, last_modified) " +
                           "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                           "filename = VALUES(filename), owner = VALUES(owner), " +
                           "path = VALUES(path), last_modified = VALUES(last_modified)";

        try (PreparedStatement selectStmt = sqliteConn.prepareStatement(selectQuery)) {
            selectStmt.setLong(1, fileId);
            ResultSet rs = selectStmt.executeQuery();
            
            if (rs.next()) {
                try (PreparedStatement insertStmt = mysqlConn.prepareStatement(insertQuery)) {
                    insertStmt.setLong(1, rs.getLong("id"));
                    insertStmt.setString(2, rs.getString("filename"));
                    insertStmt.setString(3, rs.getString("owner"));
                    insertStmt.setString(4, rs.getString("path"));
                    insertStmt.setTimestamp(5, rs.getTimestamp("last_modified"));
                    insertStmt.executeUpdate();
                }
            }
        }
    }

    private void syncFileToSQLite(Long fileId, Connection mysqlConn, Connection sqliteConn) throws SQLException {
        String selectQuery = "SELECT * FROM Files WHERE id = ?";
        String insertQuery = "INSERT OR REPLACE INTO Files (id, filename, owner, path, last_modified) " +
                           "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement selectStmt = mysqlConn.prepareStatement(selectQuery)) {
            selectStmt.setLong(1, fileId);
            ResultSet rs = selectStmt.executeQuery();
            
            if (rs.next()) {
                try (PreparedStatement insertStmt = sqliteConn.prepareStatement(insertQuery)) {
                    insertStmt.setLong(1, rs.getLong("id"));
                    insertStmt.setString(2, rs.getString("filename"));
                    insertStmt.setString(3, rs.getString("owner"));
                    insertStmt.setString(4, rs.getString("path"));
                    insertStmt.setTimestamp(5, rs.getTimestamp("last_modified"));
                    insertStmt.executeUpdate();
                }
            }
        }
    }

    private void updateMySQLSession(String username, SessionData data) throws SQLException {
        String query = "INSERT INTO Sessions (username, user_role, last_activity) " +
                      "VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE " +
                      "user_role = VALUES(user_role), last_activity = VALUES(last_activity)";
                      
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, data.role);
            stmt.setTimestamp(3, Timestamp.valueOf(data.lastActivity));
            stmt.executeUpdate();
        }
    }

    private void updateSQLiteSession(String username, SessionData data) throws SQLException {
        String query = "INSERT OR REPLACE INTO Sessions (username, user_role, last_activity) " +
                      "VALUES (?, ?, ?)";
                      
        try (Connection conn = DBConnection.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, data.role);
            stmt.setString(3, data.lastActivity.toString());
            stmt.executeUpdate();
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}