package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class UserDB {
    private final SystemLogger logger = SystemLogger.getInstance();

    public void createUserTable(Connection conn) throws SQLException {
        String query = "CREATE TABLE IF NOT EXISTS Users (" +
                       "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                       "name VARCHAR(255) UNIQUE, " +
                       "password VARCHAR(255), " +
                       "role VARCHAR(50))";  
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }

    public void addUser(String username, String password, String role) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            logger.logSecurityEvent("New user account created: " + username + " with role: " + role);
            String query = "INSERT INTO Users (name, password, role) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                stmt.setString(3, role);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.logError("Error adding user", e);
            throw new RuntimeException(e);
        }
    }

    public boolean validateUser(String username, String password) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            String query = "SELECT role FROM Users WHERE name = ? AND password = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                stmt.setString(2, password);
                try (ResultSet rs = stmt.executeQuery()) {
                    logger.logSecurityEvent("Login attempt for user: " + username);
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            logger.logError("Error validating user", e);
            return false;
        }
    }

    public boolean updateUser(String oldUsername, String newUsername, String newPassword) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            String query = "UPDATE Users SET name = ?, password = ? WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, newUsername);
                stmt.setString(2, newPassword);
                stmt.setString(3, oldUsername);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.logError("Error updating user", e);
            return false;
        }
    }

    public boolean updateUserWithRole(String oldUsername, String newUsername, String newPassword, String newRole) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            String query = "UPDATE Users SET name = ?, password = ?, role = ? WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, newUsername);
                stmt.setString(2, newPassword);
                stmt.setString(3, newRole);
                stmt.setString(4, oldUsername);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.logError("Error updating user with role", e);
            return false;
        }
    }

    public boolean usernameExists(String username) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            String query = "SELECT COUNT(*) FROM Users WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.logError("Error checking username existence", e);
            return false;
        }
    }

    public boolean deleteUser(String username) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            logger.logSecurityEvent("User account deleted: " + username);
            String query = "DELETE FROM Users WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.logError("Error deleting user", e);
            return false;
        }
    }

    public String getUserRole(String username) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            String query = "SELECT role FROM Users WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                return rs.next() ? rs.getString("role") : null;
            }
        } catch (SQLException e) {
            logger.logError("Error getting user role", e);
            return null;
        }
    }

    public boolean promoteToAdmin(String username) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            logger.logSecurityEvent("User promoted to admin: " + username);
            String query = "UPDATE Users SET role = 'admin' WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.logError("Error promoting user", e);
            return false;
        }
    }

    public ObservableList<User> getAllUsers() {
        ObservableList<User> users = FXCollections.observableArrayList();
        try (Connection conn = DBConnection.getMySQLConnection()) {
            String query = "SELECT name, password, role FROM Users";
            try (PreparedStatement stmt = conn.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    users.add(new User(
                        rs.getString("name"),
                        rs.getString("password"),
                        rs.getString("role")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.logError("Error getting all users", e);
        }
        return users;
    }
}