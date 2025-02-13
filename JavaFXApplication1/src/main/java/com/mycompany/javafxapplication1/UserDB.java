package com.mycompany.javafxapplication1;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;
import java.util.Base64;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class UserDB {
    private final SystemLogger logger = SystemLogger.getInstance();
    private static final int SALT_LENGTH = 16;

    private String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        new Random().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashPassword(String password, String salt) {
        try {
            String saltedPassword = password + salt;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = md.digest(saltedPassword.getBytes());
            return Base64.getEncoder().encodeToString(hashedBytes);
        } catch (NoSuchAlgorithmException e) {
            logger.logError("Password hashing failed", e);
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    public void createUserTable(Connection conn) throws SQLException {
        String query = "CREATE TABLE IF NOT EXISTS Users (" +
                       "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                       "name VARCHAR(255) UNIQUE, " +
                       "password VARCHAR(255), " +
                       "salt VARCHAR(255), " +
                       "role VARCHAR(50))";  
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        }
    }

    public void addUser(String username, String password, String role) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            String salt = generateSalt();
            String hashedPassword = hashPassword(password, salt);
            
            String query = "INSERT INTO Users (name, password, salt, role) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                stmt.setString(2, hashedPassword);
                stmt.setString(3, salt);
                stmt.setString(4, role);
                stmt.executeUpdate();
                logger.logSecurityEvent("New user account created: " + username);
            }
        } catch (SQLException e) {
            logger.logError("Error adding user", e);
            throw new RuntimeException(e);
        }
    }

    public boolean validateUser(String username, String password) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            String query = "SELECT password, salt FROM Users WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    String storedHash = rs.getString("password");
                    String salt = rs.getString("salt");
                    String hashedAttempt = hashPassword(password, salt);
                    return storedHash.equals(hashedAttempt);
                }
                return false;
            }
        } catch (SQLException e) {
            logger.logError("Error validating user", e);
            return false;
        }
    }

    public boolean updateUser(String oldUsername, String newUsername, String newPassword) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            String salt = generateSalt();
            String hashedPassword = hashPassword(newPassword, salt);
            
            String query = "UPDATE Users SET name = ?, password = ?, salt = ? WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, newUsername);
                stmt.setString(2, hashedPassword);
                stmt.setString(3, salt);
                stmt.setString(4, oldUsername);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.logError("Error updating user", e);
            return false;
        }
    }
    

    public boolean updateUserWithRole(String oldUsername, String newUsername, String newPassword, String newRole) {
        try (Connection conn = DBConnection.getMySQLConnection()) {
            String salt = generateSalt();
            String hashedPassword = hashPassword(newPassword, salt);
            
            String query = "UPDATE Users SET name = ?, password = ?, salt = ?, role = ? WHERE name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, newUsername);
                stmt.setString(2, hashedPassword);
                stmt.setString(3, salt);
                stmt.setString(4, newRole);
                stmt.setString(5, oldUsername);
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