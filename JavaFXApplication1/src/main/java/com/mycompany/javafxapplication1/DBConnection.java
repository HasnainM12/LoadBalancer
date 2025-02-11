package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class DBConnection {
    private static final String SQLITE_URL = "jdbc:sqlite:databases/comp20081.db";
    private static final String MYSQL_URL = "jdbc:mysql://mysql:3306/comp20081?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String MYSQL_USER = "user";
    private static final String MYSQL_PASSWORD = "password";
    
    private static final int MAX_POOL_SIZE = 10;
    private static final BlockingQueue<Connection> mysqlPool = new ArrayBlockingQueue<>(MAX_POOL_SIZE);

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            for (int i = 0; i < MAX_POOL_SIZE; i++) {
                mysqlPool.offer(createNewMySQLConnection());
            }
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException("Failed to initialize connection pool", e);
        }
    }

    public static Connection getSQLiteConnection() throws SQLException {
        return DriverManager.getConnection(SQLITE_URL);
    }

    private static Connection createNewMySQLConnection() throws SQLException {
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
    }

    public static Connection getMySQLConnection() throws SQLException {
        try {
            Connection conn = mysqlPool.poll();
            if (conn == null || conn.isClosed()) {
                conn = createNewMySQLConnection();
            }
            return conn;
        } catch (SQLException e) {
            System.err.println("MySQL connection failed: " + e.getMessage());
            throw e;
        }
    }

    public static void releaseConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed() && conn.isValid(1)) {
                    mysqlPool.offer(conn);
                } else {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing invalid connection: " + e.getMessage());
                    }
                }
            } catch (SQLException e) {
                System.err.println("Error releasing connection: " + e.getMessage());
            }
        }
    }
}