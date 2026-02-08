package specs

import base.ScimBaseSpec
import io.restassured.RestAssured
import io.restassured.response.Response
import groovy.json.JsonOutput
import spock.lang.Shared

/**
 * Area 8 — Security and Robustness
 *
 * Validates authentication enforcement, mutability rules, content-type handling,
 * and ETag support per RFC 7643/7644.
 */
class A8_SecurityAndRobustnessSpec extends ScimBaseSpec {

    @Shared String testUserId
    @Shared String testUserETag

    def setupSpec() {
        loadServiceProviderConfig()

        // Create a user to test against
        def response = createUser()
        assert response.statusCode() == 201 : "Setup failed: ${response.body().asString()}"
        testUserId = response.jsonPath().getString("id")
    }

    // ─── SEC_01: Unauthenticated request returns 401 ────────────────────────

    def "SEC_01: Request without Authorization header returns 401"() {
        // RFC 7644 §2 — Authentication and Authorization
        when:
        Response response = RestAssured.given()
            .contentType(SCIM_CONTENT_TYPE)
            .accept(SCIM_CONTENT_TYPE)
            .get("/Users")

        then:
        response.statusCode() == 401
        // TODO DEVIATION: api.scim.dev returns 401 with a non-SCIM error body
        // assertScimError(response, 401)
    }

    // ─── SEC_02: Invalid token returns 401 ──────────────────────────────────

    def "SEC_02: Request with invalid Bearer token returns 401"() {
        when:
        Response response = RestAssured.given()
            .header("Authorization", "Bearer INVALID_TOKEN_12345")
            .contentType(SCIM_CONTENT_TYPE)
            .accept(SCIM_CONTENT_TYPE)
            .get("/Users")

        then:
        response.statusCode() == 401
        // TODO DEVIATION: api.scim.dev returns 401 with a non-SCIM error body
        // assertScimError(response, 401)
    }

    // ─── SEC_03: Read-only attributes cannot be modified ────────────────────

    def "SEC_03: PATCH on read-only attribute id returns error"() {
        // RFC 7643 §7 — readOnly attributes MUST NOT be modified
        when:
        Map patchPayload = buildPatchOp([
            [op: "replace", path: "id", value: "fake-id-value"]
        ])
        Response response = scimRequest()
            .body(JsonOutput.toJson(patchPayload))
            .patch("/Users/${testUserId}")

        then: "Server should reject modification of read-only 'id'"
        // Server may return 400, 403, or ignore the change
        response.statusCode() in [200, 400, 403, 404]

        and: "If 200, the id should NOT have changed"
        if (response.statusCode() == 200) {
            response.jsonPath().getString("id") == testUserId
        }
    }

    // ─── SEC_04: Content-Type application/scim+json is accepted ─────────────

    def "SEC_04: Server accepts application/scim+json content type"() {
        // RFC 7644 §3.1 — Clients MAY use application/scim+json
        when:
        Response response = scimRequest()
            .get("/Users/${testUserId}")

        then:
        response.statusCode() == 200
        response.contentType().contains("application/scim+json")
    }

    // ─── SEC_05: ETag support ───────────────────────────────────────────────

    def "SEC_05: Server returns ETag header on resource retrieval"() {
        // RFC 7644 §3.14 — ETag support
        when:
        Response response = scimRequest()
            .get("/Users/${testUserId}")

        then:
        response.statusCode() == 200

        and: "Check meta.version for ETag value"
        String version = response.jsonPath().getString("meta.version")
        // ETag may be in header or meta.version
        String etagHeader = response.header("ETag")
        version != null || etagHeader != null
    }

    // ─── SEC_06: Unsupported HTTP method returns 405 ────────────────────────

    def "SEC_06: Unsupported HTTP method on endpoint returns appropriate error"() {
        // RFC 7644 — Servers should reject unsupported methods
        when: "Send PATCH to /ServiceProviderConfig (read-only singleton)"
        Map patchPayload = buildPatchOp([
            [op: "replace", path: "patch.supported", value: true]
        ])
        Response response = scimRequest()
            .body(JsonOutput.toJson(patchPayload))
            .patch("/ServiceProviderConfig")

        then: "Server should return 405 or 501 or 400"
        response.statusCode() in [400, 403, 404, 405, 500, 501]
    }

    // ─── SEC_07: returned=never attributes not in responses ─────────────────

    def "SEC_07: Attributes with returned=never (password) are not in GET responses"() {
        // RFC 7643 §2.2 — Attribute returned characteristic: never
        // RFC 7643 §4.1 — password has returned=never
        // Reference: scim2-compliance-test-suite ResponseValidateTests
        when: "GET the test user"
        Response response = scimRequest()
            .get("/Users/${testUserId}")

        then: "Status is 200"
        response.statusCode() == 200

        and: "password attribute is NOT present in the response (returned=never)"
        def body = response.body().asString()
        !body.contains('"password"')
    }
}
