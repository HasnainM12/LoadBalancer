package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SessionDB {
    private static final DateTimeFormatter SQLITE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void createSessionTable() {
        String query = "CREATE TABLE IF NOT EXISTS Sessions (" +
                       "username TEXT PRIMARY KEY, " +
                       "user_role TEXT, " +
                       "last_activity TEXT)";
        try (Connection conn = DBConnection.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error creating session table: " + e.getMessage());
        }
    }

    public void saveSession(String username, String role) {
        String query = "INSERT INTO Sessions (username, user_role, last_activity) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, role);
            stmt.setString(3, LocalDateTime.now().format(SQLITE_FORMATTER));
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error saving session: " + e.getMessage());
        }
    }

    public String[] getSession(String username) {
        String query = "SELECT username, user_role, last_activity FROM Sessions WHERE username = ?";
        try (Connection conn = DBConnection.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new String[]{
                    rs.getString("username"),
                    rs.getString("user_role"),
                    rs.getString("last_activity")
                };
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving session: " + e.getMessage());
        }
        return null;
    }

    public String getSessionLastActivity(String username) {
        String query = "SELECT last_activity FROM Sessions WHERE username = ?";
        try (Connection conn = DBConnection.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next() ? rs.getString("last_activity") : null;
        } catch (SQLException e) {
            System.err.println("Error retrieving session last activity: " + e.getMessage());
            return null;
        }
    }

    public void updateSessionActivity(String username) {
        String query = "UPDATE Sessions SET last_activity = ? WHERE username = ?";
        try (Connection conn = DBConnection.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, LocalDateTime.now().format(SQLITE_FORMATTER));
            stmt.setString(2, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating session activity: " + e.getMessage());
        }
    }

    public void clearSession(String username) {
        String query = "DELETE FROM Sessions WHERE username = ?";
        try (Connection conn = DBConnection.getSQLiteConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error clearing session: " + e.getMessage());
        }
    }
}