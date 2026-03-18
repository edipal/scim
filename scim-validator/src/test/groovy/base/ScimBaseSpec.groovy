package base

import io.restassured.RestAssured
import io.restassured.filter.log.RequestLoggingFilter
import io.restassured.filter.log.ResponseLoggingFilter
import io.restassured.http.ContentType
import io.restassured.response.Response
import io.restassured.specification.RequestSpecification
import groovy.json.JsonOutput
import net.datafaker.Faker
import spock.lang.Shared
import spock.lang.Specification

/**
 * Abstract base class for all SCIM 2.0 compliance test specifications.
 * Configures REST Assured with base URI, auth headers, and provides shared helper methods.
 *
 * Builds the SCIM base URL from SCIM_API_URL and SCIM_WORKSPACE_ID.
 * SCIM_WORKSPACE_ID and SCIM_AUTH_TOKEN must be provided unless SCIM_BASE_URL is set.
 */
abstract class ScimBaseSpec extends Specification {

    // ─── Configuration ───────────────────────────────────────────────────
    static String SCIM_API_URL

    @Shared static String BASE_URL
    @Shared static String BASE_PATH
    @Shared static String AUTH_TOKEN
    @Shared static String workspaceId

    static {
        refreshConfiguration()
    }
    static final String SCIM_CONTENT_TYPE = "application/scim+json"
    static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User"
    static final String GROUP_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:Group"
    static final String ENTERPRISE_USER_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"
    static final String PATCH_OP_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp"
    static final String BULK_REQUEST_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:BulkRequest"
    static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error"
    static final String LIST_RESPONSE_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse"
    static final String SPC_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"

    // ─── Service Provider Config (loaded once) ───────────────────────────
    @Shared static boolean spcLoaded = false
    @Shared static boolean patchSupported = false
    @Shared static boolean bulkSupported = false
    @Shared static int bulkMaxOperations = 0
    @Shared static int filterMaxResults = 0
    @Shared static boolean etagSupported = false
    @Shared static boolean sortSupported = false


    // ─── Dynamic data generator ──────────────────────────────────────────
    @Shared Faker faker = new Faker()

    // ─── Track created resource IDs for cleanup ──────────────────────────
    @Shared List<String> createdUserIds = []
    @Shared List<String> createdGroupIds = []

    def setupSpec() {
        refreshConfiguration()
        configureRestAssured()
        loadServiceProviderConfig()
    }

    protected static void refreshConfiguration() {
        String explicitBaseUrl = System.getProperty("scim.baseUrl") ?: System.getenv("SCIM_BASE_URL")
        String configuredWorkspace = System.getProperty("scim.workspaceId") ?: System.getenv("SCIM_WORKSPACE_ID")
        String configuredApiUrl = System.getProperty("scim.apiUrl") ?: System.getenv("SCIM_API_URL") ?: "http://localhost:8080"

        workspaceId = configuredWorkspace
        AUTH_TOKEN = System.getProperty("scim.authToken") ?: System.getenv("SCIM_AUTH_TOKEN")

        if (AUTH_TOKEN == null || AUTH_TOKEN.isBlank()) {
            throw new IllegalStateException("SCIM_AUTH_TOKEN or -Dscim.authToken must be configured for validator runs")
        }

        if (explicitBaseUrl != null && !explicitBaseUrl.isBlank()) {
            URI uri = new URI(explicitBaseUrl.trim())
            SCIM_API_URL = "${uri.scheme}://${uri.authority}"
            BASE_PATH = uri.path != null && !uri.path.isBlank() ? uri.path : "/"
            BASE_URL = explicitBaseUrl.trim()
        } else {
            if (workspaceId == null || workspaceId.isBlank()) {
                throw new IllegalStateException("SCIM_WORKSPACE_ID or -Dscim.workspaceId must be configured when SCIM_BASE_URL is not set")
            }
            SCIM_API_URL = configuredApiUrl
            BASE_PATH = "/ws/${workspaceId}/scim/v2"
            BASE_URL = "${SCIM_API_URL}${BASE_PATH}"
        }
    }

    protected static void configureRestAssured() {
        RestAssured.baseURI = SCIM_API_URL
        RestAssured.basePath = BASE_PATH
        // Avoid stacking duplicate capture filters across repeated configure calls.
        RestAssured.replaceFiltersWith(new ScimExchangeCaptureFilter())
    }

    /**
     * Load ServiceProviderConfig once across all specs.
     */
    protected void loadServiceProviderConfig() {
        configureRestAssured()
        if (spcLoaded) return
        try {
            Response response = scimRequest()
                .get("/ServiceProviderConfig")

            if (response.statusCode() == 200) {
                def json = response.jsonPath()
                patchSupported = asBoolean(json.get("patch.supported"), false)
                bulkSupported = asBoolean(json.get("bulk.supported"), false)
                bulkMaxOperations = asInt(json.get("bulk.maxOperations"), 0)
                filterMaxResults = asInt(json.get("filter.maxResults"), 0)
                etagSupported = asBoolean(json.get("etag.supported"), false)
                sortSupported = asBoolean(json.get("sort.supported"), false)
                spcLoaded = true
            }
        } catch (Exception e) {
            System.err.println("WARNING: Could not load ServiceProviderConfig: ${e.message}")
        }
    }

    protected static boolean asBoolean(Object value, boolean defaultValue = false) {
        if (value == null) return defaultValue
        if (value instanceof Boolean) return (Boolean) value
        if (value instanceof String) {
            String normalized = ((String) value).trim().toLowerCase()
            if (normalized == "true") return true
            if (normalized == "false") return false
        }
        return defaultValue
    }

    protected static int asInt(Object value, int defaultValue = 0) {
        if (value == null) return defaultValue
        if (value instanceof Number) return ((Number) value).intValue()
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim())
            } catch (NumberFormatException ignored) {
                return defaultValue
            }
        }
        return defaultValue
    }

    /**
     * Build a REST Assured request with default SCIM headers.
     */
    protected RequestSpecification scimRequest() {
        configureRestAssured()
        return RestAssured.given()
            .header("Authorization", "Bearer ${AUTH_TOKEN}")
            .contentType(SCIM_CONTENT_TYPE)
            .accept(SCIM_CONTENT_TYPE)
            .filter(new RequestLoggingFilter())
            .filter(new ResponseLoggingFilter())
    }

    /**
     * Build a REST Assured request without logging (for cleanup operations).
     */
    protected RequestSpecification scimRequestQuiet() {
        configureRestAssured()
        return RestAssured.given()
            .header("Authorization", "Bearer ${AUTH_TOKEN}")
            .contentType(SCIM_CONTENT_TYPE)
            .accept(SCIM_CONTENT_TYPE)
    }

    /**
     * Create a minimal SCIM User and return the response.
     */
    protected Response createUser(Map overrides = [:]) {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8)
        Map payload = [
            schemas : [USER_SCHEMA],
            userName: overrides.userName ?: "testuser_${uniqueSuffix}_${faker.name().username()}@test.com",
            emails  : overrides.emails ?: [
                [value: "user_${uniqueSuffix}@test.com", type: "work", primary: true]
            ]
        ]
        payload.putAll(overrides)

        Response response = scimRequestQuiet()
            .body(JsonOutput.toJson(payload))
            .post("/Users")

        if (response.statusCode() == 201) {
            String id = response.jsonPath().getString("id")
            if (id) createdUserIds << id
        }
        return response
    }

    /**
     * Create a full SCIM User with enterprise extension and return the response.
     */
    protected Response createFullUser(Map overrides = [:]) {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8)
        String fakeEmail = "work_${uniqueSuffix}@${faker.internet().domainName()}"
        String homeEmail = "home_${uniqueSuffix}@${faker.internet().domainName()}"

        Map payload = [
            schemas     : [USER_SCHEMA, ENTERPRISE_USER_SCHEMA],
            userName    : overrides.userName ?: "fulluser_${uniqueSuffix}_${faker.name().username()}@test.com",
            displayName : overrides.displayName ?: faker.name().fullName(),
            active      : overrides.containsKey('active') ? overrides.active : true,
            name        : overrides.name ?: [
                givenName : faker.name().firstName(),
                familyName: faker.name().lastName()
            ],
            emails      : overrides.emails ?: [
                [value: fakeEmail, type: "work", primary: true],
                [value: homeEmail, type: "home", primary: false]
            ],
            ("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User"): overrides.enterprise ?: [
                employeeNumber: faker.number().digits(6),
                department    : faker.commerce().department()
            ]
        ]

        if (overrides.title) payload.title = overrides.title

        Response response = scimRequestQuiet()
            .body(JsonOutput.toJson(payload))
            .post("/Users")

        if (response.statusCode() == 201) {
            String id = response.jsonPath().getString("id")
            if (id) createdUserIds << id
        }
        return response
    }

    /**
     * Create a SCIM Group and return the response.
     */
    protected Response createGroup(String displayName, List<String> memberIds = []) {
        Map payload = [
            schemas    : [GROUP_SCHEMA],
            displayName: displayName
        ]
        if (memberIds) {
            payload.members = memberIds.collect { [value: it] }
        }

        Response response = scimRequestQuiet()
            .body(JsonOutput.toJson(payload))
            .post("/Groups")

        if (response.statusCode() == 201) {
            String id = response.jsonPath().getString("id")
            if (id) createdGroupIds << id
        }
        return response
    }

    /**
     * Delete a user by ID (silently, for cleanup).
     */
    protected void deleteUser(String id) {
        if (!id) return
        try {
            scimRequestQuiet().delete("/Users/${id}")
        } catch (Exception ignored) {}
    }

    /**
     * Delete a group by ID (silently, for cleanup).
     */
    protected void deleteGroup(String id) {
        if (!id) return
        try {
            scimRequestQuiet().delete("/Groups/${id}")
        } catch (Exception ignored) {}
    }

    /**
     * Clean up all tracked resources.
     */
    def cleanupSpec() {
        createdGroupIds.each { deleteGroup(it) }
        createdUserIds.each { deleteUser(it) }
        createdGroupIds.clear()
        createdUserIds.clear()
    }

    /**
     * Build a PATCH operation payload.
     */
    protected Map buildPatchOp(List<Map> operations) {
        return [
            schemas   : [PATCH_OP_SCHEMA],
            Operations: operations
        ]
    }

    /**
     * Assert standard SCIM error response body structure.
     */
    protected void assertScimError(Response response, int expectedStatus) {
        assert response.jsonPath().getList("schemas")?.contains(ERROR_SCHEMA) ||
               response.jsonPath().getString("status") != null :
            "Response should follow SCIM error schema"
        assert response.jsonPath().getString("status") == String.valueOf(expectedStatus) ||
               response.statusCode() == expectedStatus
    }

}
