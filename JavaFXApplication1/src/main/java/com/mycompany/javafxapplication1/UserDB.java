package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class UserDB {

    private final SystemLogger logger = SystemLogger.getInstance();

    public void createUserTable() throws ClassNotFoundException {
        String query = "CREATE TABLE IF NOT EXISTS Users (" +
                       "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                       "name VARCHAR(255) UNIQUE, " +
                       "password VARCHAR(255), " +
                       "role VARCHAR(50))";  
        try (Connection conn = DBConnection.getMySQLConnection();  // ✅ Always use MySQL
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error creating user table: " + e.getMessage());
        }
    }

    public void deleteUserTable() {
        String disableFK = "SET FOREIGN_KEY_CHECKS=0";
        String dropTable = "DROP TABLE IF EXISTS Users";
        String enableFK = "SET FOREIGN_KEY_CHECKS=1";
        
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt1 = conn.prepareStatement(disableFK);
             PreparedStatement stmt2 = conn.prepareStatement(dropTable);
             PreparedStatement stmt3 = conn.prepareStatement(enableFK)) {
            
            stmt1.executeUpdate();
            stmt2.executeUpdate();
            stmt3.executeUpdate();
            
        } catch (SQLException e) {
            System.err.println("Error deleting user table: " + e.getMessage());
        }
    }
    

    public void addUser(String username, String password, String role) {
        logger.logSecurityEvent("New user account created: " + username + " with role: " + role);
        String query = "INSERT INTO Users (name, password, role) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, role);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error adding user: " + e.getMessage());
        }
    }

    public boolean updateUserWithRole(String oldUsername, String newUsername, String newPassword, String newRole) {
        logger.logSecurityEvent("User account updated - Username: " + oldUsername + " -> " + newUsername + ", Role: " + newRole);
        String query = "UPDATE Users SET name = ?, password = ?, role = ? WHERE name = ?";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, newUsername);
            stmt.setString(2, newPassword);
            stmt.setString(3, newRole);
            stmt.setString(4, oldUsername);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating user: " + e.getMessage());
            return false;
        }
    }

    public boolean validateUser(String username, String password) {
        String query = "SELECT role FROM Users WHERE name = ? AND password = ?";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            SystemLogger.getInstance().logSecurityEvent("Login attempt for user: " + username);
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Error validating user: " + e.getMessage());
            return false;
        }
    }

    public boolean updateUser(String oldUsername, String newUsername, String newPassword) {
        String query = "UPDATE Users SET name = ?, password = ? WHERE name = ?";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, newUsername);
            stmt.setString(2, newPassword);
            stmt.setString(3, oldUsername);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating user: " + e.getMessage());
            return false;
        }
    }

    public boolean usernameExists(String username) {
        String query = "SELECT COUNT(*) FROM Users WHERE name = ?";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error checking if username exists: " + e.getMessage());
        }
        return false;
    }
    
    

    public boolean deleteUser(String username) {
        logger.logSecurityEvent("User account deleted: " + username);
        String query = "DELETE FROM Users WHERE name = ?";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting user: " + e.getMessage());
            return false;
        }
    }

    public String getUserRole(String username) {
        String query = "SELECT role FROM Users WHERE name = ?";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("role");
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving user role: " + e.getMessage());
        }
        return null;
    }

    public boolean promoteToAdmin(String username) {
        logger.logSecurityEvent("User promoted to admin: " + username);
        String query = "UPDATE Users SET role = 'admin' WHERE name = ?";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, username);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error promoting user to admin: " + e.getMessage());
            return false;
        }
    }

    public ObservableList<User> getAllUsers() {
        ObservableList<User> users = FXCollections.observableArrayList();
        String query = "SELECT name, password, role FROM Users";
        try (Connection conn = DBConnection.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                users.add(new User(
                        rs.getString("name"),
                        rs.getString("password"),
                        rs.getString("role")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving users: " + e.getMessage());
        }
        return users;
    }
}
