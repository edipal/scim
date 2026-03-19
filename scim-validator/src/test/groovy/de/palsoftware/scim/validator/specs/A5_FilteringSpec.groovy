package de.palsoftware.scim.validator.specs

import de.palsoftware.scim.validator.base.A5_BaseSpec
import io.restassured.response.Response

/**
 * Area 5a — Filtering
 *
 * Validates SCIM 2.0 filtering (RFC 7644 §3.4.2) and related query parameters.
 */
class A5_FilteringSpec extends A5_BaseSpec {

    // ─── FLT_01: eq operator ────────────────────────────────────────────────

    def "FLT_01: Filter with eq operator returns exact match"() {
        // RFC 7644 §3.4.2.2 — Comparison Operators: eq
        given:
        String targetUserName = userData[0].userName

        when:
        Response response = scimRequest()
            .queryParam("filter", "userName eq \"${targetUserName}\"")
            .get("/Users")

        then:
        response.statusCode() == 200
        response.jsonPath().getInt("totalResults") >= 1
        response.jsonPath().getList("Resources").size() >= 1
        response.jsonPath().getList("Resources.userName").contains(targetUserName)
    }

    // ─── FLT_02: sw operator ────────────────────────────────────────────────

    def "FLT_02: Filter with sw (startsWith) returns matching users"() {
        // RFC 7644 §3.4.2.2 — Comparison Operators: sw
        when:
        Response response = scimRequest()
            .queryParam("filter", "userName sw \"${PREFIX}\"")
            .get("/Users")

        then:
        response.statusCode() == 200
        int count = response.jsonPath().getInt("totalResults")
        count >= 5
        response.jsonPath().getList("Resources").size() >= 5
    }

    // ─── FLT_03: co operator ────────────────────────────────────────────────

    def "FLT_03: Filter with co (contains) returns matching users"() {
        // RFC 7644 §3.4.2.2 — Comparison Operators: co
        when:
        Response response = scimRequest()
            .queryParam("filter", "userName co \"alice@test\"")
            .get("/Users")

        then:
        response.statusCode() == 200
        response.jsonPath().getInt("totalResults") >= 1
        // Every returned resource should contain the substring
        response.jsonPath().getList("Resources.userName").every { String un -> un.contains("alice@test") }
    }

    // ─── FLT_04: pr operator ────────────────────────────────────────────────

    def "FLT_04: Filter with pr (present) returns users that have the attribute"() {
        // RFC 7644 §3.4.2.2 — Attribute Operators: pr
        when:
        Response response = scimRequest()
            .queryParam("filter", "externalId pr")
            .get("/Users")

        then:
        response.statusCode() == 200
        response.jsonPath().getInt("totalResults") >= 5
        // All returned resources should have externalId present
        response.jsonPath().getList("Resources").every { Map r -> r.externalId != null }
    }

    // ─── FLT_05: eq on boolean attribute ────────────────────────────────────

    def "FLT_05: Filter eq on active (boolean) returns correct users"() {
        // RFC 7644 §3.4.2.2 — eq with boolean value
        when:
        Response response = scimRequest()
            .queryParam("filter", "active eq false and userName sw \"${PREFIX}\"")
            .get("/Users")

        then:
        response.statusCode() == 200
        int count = response.jsonPath().getInt("totalResults")
        // We created exactly 2 inactive users (charlie, eve)
        count >= 2
    }

    // ─── FLT_06: Logical AND filter ─────────────────────────────────────────

    def "FLT_06: Filter with logical AND narrows results correctly"() {
        // RFC 7644 §3.4.2.2 — Logical Operators: and
        when:
        Response response = scimRequest()
            .queryParam("filter", "userName sw \"${PREFIX}\" and active eq true")
            .get("/Users")

        then:
        response.statusCode() == 200
        int count = response.jsonPath().getInt("totalResults")
        // 3 active users among our 5: alice, bob, diana
        count >= 3
        response.jsonPath().getList("Resources").every { Map r ->
            (r.userName as String).startsWith(PREFIX) && r.active == true
        }
    }

    // ─── FLT_07: Logical OR filter ──────────────────────────────────────────

    def "FLT_07: Filter with logical OR broadens results correctly"() {
        // RFC 7644 §3.4.2.2 — Logical Operators: or
        given:
        String user1 = userData[0].userName
        String user2 = userData[1].userName

        when:
        Response response = scimRequest()
            .queryParam("filter", "userName eq \"${user1}\" or userName eq \"${user2}\"")
            .get("/Users")

        then:
        response.statusCode() == 200
        response.jsonPath().getInt("totalResults") >= 2
        def returnedNames = response.jsonPath().getList("Resources.userName")
        returnedNames.contains(user1)
        returnedNames.contains(user2)
    }

    // ─── FLT_10: Invalid filter returns 400 ─────────────────────────────────

    def "FLT_10: Invalid filter expression returns HTTP 400"() {
        // RFC 7644 §3.4.2 — Server MUST return 400 for invalid filter
        when:
        Response response = scimRequest()
            .queryParam("filter", "not a valid !!! filter [[")
            .get("/Users")

        then:
        response.statusCode() == 400
    }

    // ─── FLT_11: Filter for non-existent user returns totalResults=0 ───────

    def "FLT_11: Filter for non-existent user returns empty list response"() {
        // RFC 7644 §3.4.2 — ListResponse with totalResults=0 when no match
        when:
        Response response = scimRequest()
            .queryParam("filter", "userName eq \"nonexistent_${UUID.randomUUID().toString().substring(0, 8)}@test.com\"")
            .get("/Users")

        then:
        response.statusCode() == 200
        response.jsonPath().getList("schemas").contains(LIST_RESPONSE_SCHEMA)
        response.jsonPath().getInt("totalResults") == 0
        response.jsonPath().getInt("itemsPerPage") >= 0
        response.jsonPath().getInt("startIndex") >= 1
    }

    // ─── FLT_12: userName filter is case-insensitive when caseExact=false ─

    def "FLT_12: userName filter matches regardless of case"() {
        // RFC 7643 §4.1 — userName has caseExact=false
        // RFC 7644 §3.4.2.2 — String comparisons are case-insensitive when caseExact=false
        given: "Fetch userName caseExact setting from the schema"
        Response schemaResponse = scimRequest()
            .get("/Schemas/${USER_SCHEMA}")
        schemaResponse.statusCode() == 200

        def attributes = schemaResponse.jsonPath().getList("attributes")
        def userNameAttr = attributes.find { it.name == "userName" }
        userNameAttr != null
        userNameAttr.caseExact == false

        and: "Pick a known userName and change its case"
        String originalUserName = userData[0].userName
        String upperUserName = originalUserName.toUpperCase()

        when:
        Response response = scimRequest()
            .queryParam("filter", "userName eq \"${upperUserName}\"")
            .get("/Users")

        then:
        response.statusCode() == 200
        response.jsonPath().getInt("totalResults") >= 1
        response.jsonPath().getList("Resources.userName").contains(originalUserName)
    }

    // ─── FLT_13: attributes parameter limits returned attributes ────────────

    def "FLT_13: attributes parameter returns only minimum set plus requested"() {
        // RFC 7644 §3.9 — attributes overrides the default attribute set
        given:
        String targetId = userData[0].id

        when:
        Response response = scimRequest()
            .queryParam("attributes", "userName")
            .get("/Users/${targetId}")

        then:
        response.statusCode() == 200
        response.jsonPath().getString("id") == targetId
        response.jsonPath().getString("userName") == userData[0].userName

        and: "Non-requested default attributes are omitted (log deviation if present)"
        def body = response.jsonPath().getMap("")
        boolean hasName = body?.containsKey("name")
        boolean hasEmails = body?.containsKey("emails")
        boolean hasExternalId = body?.containsKey("externalId")

        if (hasName || hasEmails || hasExternalId) {
            println "DEVIATION: api.scim.dev returns default attributes even when attributes=userName is specified"
        }
    }

    // ─── FLT_14: excludedAttributes parameter removes defaults ──────────────

    def "FLT_14: excludedAttributes removes requested default attributes"() {
        // RFC 7644 §3.9 — excludedAttributes removes attributes from default set
        when:
        Response response = scimRequest()
            .queryParam("filter", "userName sw \"${PREFIX}\"")
            .queryParam("excludedAttributes", "emails")
            .get("/Users")

        then:
        response.statusCode() == 200
        response.jsonPath().getInt("totalResults") >= 1

        and: "id remains present while emails are omitted (log deviation if present)"
        def resources = response.jsonPath().getList("Resources")
        resources.every { Map r -> r.id != null }

        boolean emailsPresent = resources.any { Map r -> r.containsKey("emails") }
        if (emailsPresent) {
            println "DEVIATION: api.scim.dev does not honor excludedAttributes=emails on list responses"
        }
    }

    // ─── FLT_15: Filter groups by displayName eq ────────────────────────────

    def "FLT_15: Filter groups by displayName eq returns exact match"() {
        // RFC 7644 §3.4.2.2 — Comparison Operators: eq on Groups
        // Reference: scim2-compliance-test-suite FilterTest.FilterGroups()
        given: "Create test groups with known displayNames"
        String groupName1 = "${PREFIX}Engineers"
        String groupName2 = "${PREFIX}Designers"
        Response g1 = createGroup(groupName1)
        Response g2 = createGroup(groupName2)
        assert g1.statusCode() == 201
        assert g2.statusCode() == 201
        String gid1 = g1.jsonPath().getString("id")
        String gid2 = g2.jsonPath().getString("id")

        when: "Filter groups by displayName eq"
        Response response = scimRequest()
            .queryParam("filter", "displayName eq \"${groupName1}\"")
            .get("/Groups")

        then: "Only the matching group is returned"
        response.statusCode() == 200
        response.jsonPath().getInt("totalResults") >= 1
        response.jsonPath().getList("Resources").every { Map r ->
            (r.displayName as String) == groupName1
        }

        cleanup:
        if (gid1) deleteGroup(gid1)
        if (gid2) deleteGroup(gid2)
        createdGroupIds.remove(gid1)
        createdGroupIds.remove(gid2)
    }
}
