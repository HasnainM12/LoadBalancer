package com.mycompany.javafxapplication1;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class DBConnection {
    private static final String SQLITE_URL = "jdbc:sqlite:databases/comp20081.db";
    private static final String MYSQL_URL = "jdbc:mysql://mysql:3306/comp20081?allowPublicKeyRetrieval=true&useSSL=false";
    private static final String MYSQL_USER = "user";  // Matches docker-compose.yml
    private static final String MYSQL_PASSWORD = "password";  // Matches docker-compose.yml
    
    private static final int MAX_POOL_SIZE = 10;
    private static final BlockingQueue<Connection> connectionPool = new ArrayBlockingQueue<>(MAX_POOL_SIZE);

    public static Connection getSQLiteConnection() throws SQLException {
        return DriverManager.getConnection(SQLITE_URL);  // ✅ Always for sessions & temp data
    }

    private static boolean tablesInitialized = false;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            for (int i = 0; i < MAX_POOL_SIZE; i++) {
                connectionPool.offer(createNewConnection());
            }
        } catch (ClassNotFoundException | SQLException e) {
            throw new RuntimeException("Failed to initialize connection pool", e);
        }
    }

    private static Connection createNewConnection() throws SQLException {
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
    }

    public static Connection getMySQLConnection() throws SQLException {
        try {
            Connection conn = connectionPool.poll();
            if (conn == null || conn.isClosed()) {
                conn = createNewConnection();
            }
            
            // Initialize tables only once
            if (!tablesInitialized) {
                synchronized (DBConnection.class) {
                    if (!tablesInitialized) {
                        try {
                            // Use a separate connection for initialization
                            Connection initConn = createNewConnection();
                            try {
                                // Create tables in correct order
                                UserDB userDB = new UserDB();
                                FileDB fileDB = new FileDB();
                                LoadBalancerDB loadBalancerDB = new LoadBalancerDB();
                                
                                userDB.createUserTable(initConn);
                                loadBalancerDB.createStorageContainersTable(initConn);
                                fileDB.createFileTable(initConn);
                                fileDB.createFilePermissionsTable(initConn);
                                fileDB.createChunksTable(initConn);
                                
                                // Initialize storage containers
                                initializeStorageContainers(loadBalancerDB, initConn);
                                
                                tablesInitialized = true;
                            } finally {
                                initConn.close();
                            }
                        } catch (ClassNotFoundException e) {
                            throw new SQLException("Error initializing database tables", e);
                        }
                    }
                }
            }
            
            return conn;
        } catch (SQLException e) {
            throw new SQLException("Failed to get connection from pool", e);
        }
    }

    public static void releaseConnection(Connection conn) {
        if (conn != null) {
            try {
                if (!conn.isClosed()) {
                    connectionPool.offer(conn);
                }
            } catch (SQLException e) {
                System.err.println("Error releasing connection: " + e.getMessage());
            }
        }
    }

    private static void initializeStorageContainers(LoadBalancerDB loadBalancerDB, Connection conn) {
        // Initialize storage containers for each file server
        for (int i = 1; i <= 4; i++) {
            String containerName = "comp20081-files" + i;
            loadBalancerDB.addStorageContainer(containerName);
        }
    }
}
