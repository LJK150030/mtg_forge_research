package com.example.research;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Metrics;
import org.neo4j.driver.Query;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;
import org.neo4j.driver.async.AsyncSession;
import org.neo4j.driver.async.AsyncTransaction;
import org.neo4j.driver.async.AsyncTransactionCallback;
import org.neo4j.driver.async.AsyncTransactionContext;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Singleton service for managing Neo4j database connections and operations.
 * This class provides a thread-safe way to interact with Neo4j from anywhere
 * in the application without circular dependencies.
 */
public class Neo4jService implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(Neo4jService.class.getName());

    // Singleton instance
    private static volatile Neo4jService instance;

    // Driver is thread-safe and should be reused
    private final Driver driver;

    // Configuration
    private final String uri;
    private final String username;
    private final String password;
    private final String database;

    /**
     * Configuration class for Neo4j connection settings
     */
    public static class Neo4jConfig {
        public String uri = "neo4j://localhost:7687";  // Use neo4j+s:// for production
        public String username = "neo4j";
        public String password = "password";
        public String database = "neo4j";  // default database
        public int maxConnectionPoolSize = 50;
        public long connectionAcquisitionTimeout = 60; // seconds
        public long maxConnectionLifetime = 3600; // seconds

        public static Neo4jConfig defaultConfig() {
            return new Neo4jConfig();
        }

        public Neo4jConfig withUri(String uri) {
            this.uri = uri;
            return this;
        }

        public Neo4jConfig withAuth(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public Neo4jConfig withDatabase(String database) {
            this.database = database;
            return this;
        }
    }

    /**
     * Private constructor for singleton pattern
     */
    private Neo4jService(Neo4jConfig config) {
        this.uri = config.uri;
        this.username = config.username;
        this.password = config.password;
        this.database = config.database;

        try {
            // Create driver with connection pool configuration
            Config driverConfig = Config.builder()
                    .withMaxConnectionPoolSize(config.maxConnectionPoolSize)
                    .withConnectionAcquisitionTimeout(config.connectionAcquisitionTimeout,
                            java.util.concurrent.TimeUnit.SECONDS)
                    .withMaxConnectionLifetime(config.maxConnectionLifetime,
                            java.util.concurrent.TimeUnit.SECONDS)
                    .withDriverMetrics()
                    .build();

            this.driver = GraphDatabase.driver(uri, AuthTokens.basic(username, password), driverConfig);

            // Verify connectivity immediately
            driver.verifyConnectivity();

            LOGGER.info("✓ Neo4j connection established successfully to " + uri);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "❌ Failed to connect to Neo4j: " + e.getMessage(), e);
            throw new RuntimeException("Failed to initialize Neo4j connection", e);
        }
    }

    /**
     * Initialize the singleton instance with configuration
     * This should be called once during application startup (e.g., in ForgeApp)
     */
    public static void initialize(Neo4jConfig config) {
        if (instance == null) {
            synchronized (Neo4jService.class) {
                if (instance == null) {
                    instance = new Neo4jService(config);
                }
            }
        } else {
            LOGGER.warning("Neo4jService already initialized. Ignoring duplicate initialization.");
        }
    }

    /**
     * Get the singleton instance
     * @throws IllegalStateException if the service hasn't been initialized
     */
    public static Neo4jService getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "Neo4jService not initialized. Call Neo4jService.initialize() first.");
        }
        return instance;
    }

    /**
     * Execute a write transaction with automatic retry on failure
     */
    public <T> T writeTransaction(TransactionCallback<T> work) {
        try (Session session = driver.session(SessionConfig.builder()
                .withDatabase(database)
                .build())) {
            return session.executeWrite(work);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Write transaction failed: " + e.getMessage(), e);
            throw new RuntimeException("Write transaction failed", e);
        }
    }

    /**
     * Execute a read transaction with automatic retry on failure
     */
    public <T> T readTransaction(TransactionCallback<T> work) {
        try (Session session = driver.session(SessionConfig.builder()
                .withDatabase(database)
                .build())) {
            return session.executeRead(work);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Read transaction failed: " + e.getMessage(), e);
            throw new RuntimeException("Read transaction failed", e);
        }
    }

    /**
     * Execute a single auto-commit query (for simple operations)
     * Use transactions for complex operations or when ACID guarantees are needed
     */
    public Result runQuery(String query, Map<String, Object> parameters) {
        try (Session session = driver.session(SessionConfig.builder()
                .withDatabase(database)
                .build())) {
            return session.run(query, parameters);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Query execution failed: " + e.getMessage(), e);
            throw new RuntimeException("Query execution failed", e);
        }
    }

    /**
     * Execute an asynchronous write transaction
     */
    public <T> CompletableFuture<T> writeTransactionAsync(AsyncTransactionCallback<CompletionStage<T>> work) {
        AsyncSession session = driver.session(AsyncSession.class,
                SessionConfig.builder().withDatabase(database).build());

        return session.executeWriteAsync(work)
                .toCompletableFuture()
                .whenComplete((result, error) -> session.closeAsync());
    }

    /**
     * Execute an asynchronous read transaction
     */
    public <T> CompletableFuture<T> readTransactionAsync(AsyncTransactionCallback<CompletionStage<T>> work) {
        AsyncSession session = driver.session(AsyncSession.class,
                SessionConfig.builder().withDatabase(database).build());

        return session.executeReadAsync(work)
                .toCompletableFuture()
                .whenComplete((result, error) -> session.closeAsync());
    }

    /**
     * Convenience method to create a node
     */
    public void createNode(String label, Map<String, Object> properties) {
        writeTransaction(tx -> {
            StringBuilder query = new StringBuilder("CREATE (n:");
            query.append(label);
            query.append(" $props) RETURN n");

            Map<String, Object> params = Map.of("props", properties);
            tx.run(query.toString(), params);
            return null;
        });
    }

    /**
     * Convenience method to create a relationship
     */
    public void createRelationship(String fromLabel, String fromProperty, Object fromValue,
                                   String toLabel, String toProperty, Object toValue,
                                   String relationshipType, Map<String, Object> relProperties) {
        writeTransaction(tx -> {
            String query = String.format(
                    "MATCH (a:%s {%s: $fromValue}), (b:%s {%s: $toValue}) " +
                            "CREATE (a)-[r:%s $props]->(b) " +
                            "RETURN r",
                    fromLabel, fromProperty, toLabel, toProperty, relationshipType
            );

            Map<String, Object> params = Map.of(
                    "fromValue", fromValue,
                    "toValue", toValue,
                    "props", relProperties != null ? relProperties : Map.of()
            );

            tx.run(query, params);
            return null;
        });
    }

    /**
     * Check if the service is connected
     */
    public boolean isConnected() {
        try {
            driver.verifyConnectivity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get driver metrics (useful for monitoring)
     */
    public void printMetrics() {
        if (driver.isMetricsEnabled()) {
            Metrics metrics = driver.metrics();
            LOGGER.info("Neo4j Driver Metrics:");
            LOGGER.info("  Connection pool metrics: " + metrics.connectionPoolMetrics());
        } else {
            LOGGER.info("Driver metrics are not enabled");
        }
    }

    /**
     * Close the driver connection
     * This should be called when the application shuts down
     */
    @Override
    public void close() {
        if (driver != null) {
            driver.close();
            LOGGER.info("Neo4j connection closed");
        }
    }

    /**
     * Shutdown the singleton instance
     */
    public static void shutdown() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}