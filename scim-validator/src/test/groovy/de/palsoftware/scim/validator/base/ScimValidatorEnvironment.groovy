package de.palsoftware.scim.validator.base

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant

@SuppressWarnings("resource")
final class ScimValidatorEnvironment {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScimValidatorEnvironment)
    private static final SecureRandom SECURE_RANDOM = new SecureRandom()

    private static final String DEFAULT_POSTGRES_IMAGE = "postgres:16-alpine"
    private static final String DEFAULT_API_IMAGE = "edipal/scim-server-api:latest"
    private static final String POSTGRES_ALIAS = "validator-postgres"
    private static final String DATABASE_NAME = "scimplayground"
    private static final String DATABASE_USERNAME = "scim_playground"
    private static final String DATABASE_PASSWORD = "scim_playground"
    private static final int API_PORT = 8080

    private static Network network
    private static PostgreSQLContainer<?> postgres
    private static GenericContainer<?> api
    private static ScimRuntimeConfiguration runtimeConfiguration

    private ScimValidatorEnvironment() {
    }

    static synchronized ScimRuntimeConfiguration ensureStarted() {
        if (runtimeConfiguration != null) {
            return runtimeConfiguration
        }

        try {
            network = Network.newNetwork()

            postgres = new PostgreSQLContainer<>(dockerImageName(
                "scim.validator.postgresImage",
                "SCIM_VALIDATOR_POSTGRES_IMAGE",
                DEFAULT_POSTGRES_IMAGE
            ))
                .withDatabaseName(DATABASE_NAME)
                .withUsername(DATABASE_USERNAME)
                .withPassword(DATABASE_PASSWORD)
                .withNetwork(network)
                .withNetworkAliases(POSTGRES_ALIAS)
            postgres.start()

            String actuatorApiKey = randomToken(32)
            api = new GenericContainer<>(dockerImageName(
                "scim.validator.apiImage",
                "SCIM_VALIDATOR_API_IMAGE",
                DEFAULT_API_IMAGE
            ))
                .withNetwork(network)
                .withExposedPorts(API_PORT)
                .withEnv("SERVER_PORT", Integer.toString(API_PORT))
                .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://${POSTGRES_ALIAS}:5432/${DATABASE_NAME}")
                .withEnv("SPRING_DATASOURCE_USERNAME", DATABASE_USERNAME)
                .withEnv("SPRING_DATASOURCE_PASSWORD", DATABASE_PASSWORD)
                .withEnv("ACTUATOR_API_KEY", actuatorApiKey)
                .waitingFor(Wait.forHttp("/actuator/health")
                    .forPort(API_PORT)
                    .withHeader("X-API-KEY", actuatorApiKey)
                    .forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(3))
            api.start()

            SeededTenant seededTenant = seedTenant()
            String apiUrl = "http://${api.getHost()}:${api.getMappedPort(API_PORT)}"
            runtimeConfiguration = new ScimRuntimeConfiguration(apiUrl, seededTenant.workspaceId, seededTenant.authToken)

            Runtime.runtime.addShutdownHook(new Thread(ScimValidatorEnvironment::shutdown))
            LOGGER.info("Started validator test environment against {} with workspace {}", apiUrl, seededTenant.workspaceId)
            return runtimeConfiguration
        } catch (Exception exception) {
            shutdown()
            throw new IllegalStateException("Unable to start the SCIM validator test environment", exception)
        }
    }

    private static SeededTenant seedTenant() throws SQLException {
        UUID workspaceId = UUID.randomUUID()
        UUID tokenId = UUID.randomUUID()
        Instant now = Instant.now()
        String rawToken = randomToken(64)
        String tokenHash = sha256Hex(rawToken)

        try (Connection connection = DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            insertWorkspace(connection, workspaceId, now)
            insertWorkspaceToken(connection, tokenId, workspaceId, tokenHash, now)
        }

        return new SeededTenant(workspaceId.toString(), rawToken)
    }

    private static void insertWorkspace(Connection connection, UUID workspaceId, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO workspaces (id, name, description, created_by_username, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, workspaceId)
            statement.setString(2, "validator-${workspaceId.toString().substring(0, 8)}")
            statement.setString(3, "Validator test workspace")
            statement.setString(4, "validator@test.local")
            statement.setTimestamp(5, Timestamp.from(now))
            statement.setTimestamp(6, Timestamp.from(now))
            statement.executeUpdate()
        }
    }

    private static void insertWorkspaceToken(Connection connection,
                                             UUID tokenId,
                                             UUID workspaceId,
                                             String tokenHash,
                                             Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO workspace_tokens (id, workspace_id, token_hash, name, description, expires_at, revoked, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, tokenId)
            statement.setObject(2, workspaceId)
            statement.setString(3, tokenHash)
            statement.setString(4, "validator-token")
            statement.setString(5, "Validator bootstrap token")
            statement.setObject(6, null)
            statement.setBoolean(7, false)
            statement.setTimestamp(8, Timestamp.from(now))
            statement.setTimestamp(9, Timestamp.from(now))
            statement.executeUpdate()
        }
    }

    private static DockerImageName dockerImageName(String systemPropertyName, String environmentVariableName, String defaultImage) {
        String imageName = System.getProperty(systemPropertyName)
        if (imageName == null || imageName.isBlank()) {
            imageName = System.getenv(environmentVariableName)
        }
        if (imageName == null || imageName.isBlank()) {
            imageName = defaultImage
        }
        return DockerImageName.parse(imageName)
    }

    private static String randomToken(int bytes) {
        byte[] randomBytes = new byte[bytes]
        SECURE_RANDOM.nextBytes(randomBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256")
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8))
            StringBuilder builder = new StringBuilder(hash.length * 2)
            for (byte b : hash) {
                builder.append(Character.forDigit((b >> 4) & 0xF, 16))
                builder.append(Character.forDigit(b & 0xF, 16))
            }
            return builder.toString()
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception)
        }
    }

    private static synchronized void shutdown() {
        if (api != null) {
            api.stop()
            api = null
        }
        if (postgres != null) {
            postgres.stop()
            postgres = null
        }
        if (network != null) {
            network.close()
            network = null
        }
        runtimeConfiguration = null
    }

    static final class SeededTenant {
        final String workspaceId
        final String authToken

        SeededTenant(String workspaceId, String authToken) {
            this.workspaceId = workspaceId
            this.authToken = authToken
        }
    }

    static final class ScimRuntimeConfiguration {
        final String apiUrl
        final String workspaceId
        final String authToken

        ScimRuntimeConfiguration(String apiUrl, String workspaceId, String authToken) {
            this.apiUrl = apiUrl
            this.workspaceId = workspaceId
            this.authToken = authToken
        }
    }
}