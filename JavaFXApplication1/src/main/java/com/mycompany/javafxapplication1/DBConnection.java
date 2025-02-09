package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static final String SQLITE_URL = "jdbc:sqlite:comp20081.db";
    private static final String MYSQL_URL = "jdbc:mysql://mysql:3306/comp20081?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String MYSQL_USER = "user";  // Matches docker-compose.yml
    private static final String MYSQL_PASSWORD = "password";  // Matches docker-compose.yml

    public static Connection getSQLiteConnection() throws SQLException {
        return DriverManager.getConnection(SQLITE_URL);  // ✅ Always for sessions & temp data
    }

    private static boolean tablesInitialized = false;

    public static Connection getMySQLConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found!", e);
        }
        
        Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
        
        // Initialize tables only once
        if (!tablesInitialized) {
            synchronized (DBConnection.class) {
                if (!tablesInitialized) {
                    try {
                        // Use the same connection for all table creation
                        UserDB userDB = new UserDB();
                        FileDB fileDB = new FileDB();
                        
                        userDB.createUserTable();
                        fileDB.createFileTable();
                        fileDB.createFilePermissionsTable();
                        fileDB.createChunksTable();
                        
                        tablesInitialized = true;
                    } catch (ClassNotFoundException e) {
                        throw new SQLException("Error initializing database tables", e);
                    }
                }
            }
        }
        
        return conn;
    }
}
