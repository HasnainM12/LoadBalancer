package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static final String SQLITE_URL = "jdbc:sqlite:comp20081.db";
    private static final String MYSQL_URL = "jdbc:mysql://localhost:3307/comp20081?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String MYSQL_USER = "user";  // Matches docker-compose.yml
    private static final String MYSQL_PASSWORD = "password";  // Matches docker-compose.yml

    public static Connection getSQLiteConnection() throws SQLException {
        return DriverManager.getConnection(SQLITE_URL);  // ✅ Always for sessions & temp data
    }

    public static Connection getMySQLConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found!", e);
        }
        if (MYSQL_USER == null || MYSQL_PASSWORD == null) {
            throw new SQLException("MySQL credentials not set. Configure MYSQL_USER and MYSQL_PASSWORD.");
        }
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);  // ✅ Always for users & files
    }
}
