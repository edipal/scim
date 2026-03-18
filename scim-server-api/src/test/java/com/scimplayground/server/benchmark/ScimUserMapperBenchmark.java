package com.scimplayground.server.benchmark;

import com.scimplayground.server.model.*;
import com.scimplayground.server.scim.mapper.ScimUserMapper;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(0)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class ScimUserMapperBenchmark {

    private ScimUser minimalUser;
    private ScimUser fullUser;
    private String baseUrl;
    private List<Map<String, Object>> groups;
    private Map<String, Object> scimInput;
    private Map<String, Object> fullScimInput;

    @Setup(Level.Trial)
    public void setup() {
        baseUrl = "https://scim.example.com/ws/test/scim/v2";
        minimalUser = buildMinimalUser();
        fullUser = buildFullUser();
        groups = buildGroups();
        scimInput = buildSimpleScimInput();
        fullScimInput = buildFullScimInput();
    }

    @Benchmark
    public Map<String, Object> mapMinimalUser() {
        return ScimUserMapper.toScimResponse(minimalUser, baseUrl, null);
    }

    @Benchmark
    public Map<String, Object> mapFullUser() {
        return ScimUserMapper.toScimResponse(fullUser, baseUrl, groups);
    }

    @Benchmark
    public Map<String, Object> mapFullUserNoGroups() {
        return ScimUserMapper.toScimResponse(fullUser, baseUrl, null);
    }

    @Benchmark
    public void applySimpleScimInput(Blackhole bh) {
        ScimUser user = new ScimUser();
        ScimUserMapper.applyFromScimInput(user, scimInput);
        bh.consume(user);
    }

    @Benchmark
    public void applyFullScimInput(Blackhole bh) {
        ScimUser user = new ScimUser();
        ScimUserMapper.applyFromScimInput(user, fullScimInput);
        bh.consume(user);
    }

    @Benchmark
    public void clearMutableAttributes(Blackhole bh) {
        ScimUser user = buildFullUser();
        ScimUserMapper.clearMutableAttributes(user);
        bh.consume(user);
    }

    // ── Batch mapping (simulates list responses) ──

    private List<ScimUser> batchUsers;

    @Setup(Level.Trial)
    public void setupBatch() {
        batchUsers = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            batchUsers.add(buildFullUser());
        }
    }

    @Benchmark
    public List<Map<String, Object>> mapBatch100Users() {
        List<Map<String, Object>> result = new ArrayList<>(100);
        for (ScimUser user : batchUsers) {
            result.add(ScimUserMapper.toScimResponse(user, baseUrl, groups));
        }
        return result;
    }

    // ── Fixture builders ──

    private ScimUser buildMinimalUser() {
        ScimUser user = new ScimUser();
        user.setId(UUID.randomUUID());
        user.setUserName("john.doe");
        user.setActive(true);
        user.setCreatedAt(Instant.parse("2024-01-15T10:30:00Z"));
        user.setLastModified(Instant.parse("2024-01-15T10:30:00Z"));
        user.setVersion(1L);
        return user;
    }

    private ScimUser buildFullUser() {
        ScimUser user = new ScimUser();
        user.setId(UUID.randomUUID());
        user.setUserName("john.doe@example.com");
        user.setExternalId("ext-12345");
        user.setDisplayName("John Doe");
        user.setNickName("Johnny");
        user.setProfileUrl("https://example.com/johndoe");
        user.setTitle("Software Engineer");
        user.setUserType("Employee");
        user.setPreferredLanguage("en");
        user.setLocale("en_US");
        user.setTimezone("America/New_York");
        user.setActive(true);
        user.setNameFormatted("Mr. John M. Doe Jr.");
        user.setNameFamilyName("Doe");
        user.setNameGivenName("John");
        user.setNameMiddleName("Michael");
        user.setNameHonorificPrefix("Mr.");
        user.setNameHonorificSuffix("Jr.");
        user.setEnterpriseEmployeeNumber("E12345");
        user.setEnterpriseCostCenter("CC1234");
        user.setEnterpriseOrganization("ACME Corp");
        user.setEnterpriseDivision("Engineering");
        user.setEnterpriseDepartment("Backend");
        user.setEnterpriseManagerValue("mgr-uuid");
        user.setEnterpriseManagerRef("https://scim.example.com/Users/mgr-uuid");
        user.setEnterpriseManagerDisplay("Jane Manager");
        user.setCreatedAt(Instant.parse("2024-01-15T10:30:00Z"));
        user.setLastModified(Instant.parse("2024-06-15T14:45:00Z"));
        user.setVersion(5L);

        for (int i = 0; i < 3; i++) {
            ScimUserEmail email = new ScimUserEmail();
            email.setUser(user);
            email.setValue("email" + i + "@example.com");
            email.setType(i == 0 ? "work" : "home");
            email.setPrimaryFlag(i == 0);
            user.getEmails().add(email);
        }
        for (int i = 0; i < 2; i++) {
            ScimUserPhoneNumber phone = new ScimUserPhoneNumber();
            phone.setUser(user);
            phone.setValue("+1-555-000" + i);
            phone.setType(i == 0 ? "work" : "mobile");
            phone.setPrimaryFlag(i == 0);
            user.getPhoneNumbers().add(phone);
        }
        ScimUserAddress addr = new ScimUserAddress();
        addr.setUser(user);
        addr.setStreetAddress("123 Main St");
        addr.setLocality("Springfield");
        addr.setRegion("IL");
        addr.setPostalCode("62701");
        addr.setCountry("US");
        addr.setType("work");
        addr.setPrimaryFlag(true);
        user.getAddresses().add(addr);

        ScimUserIm im = new ScimUserIm();
        im.setUser(user);
        im.setValue("johndoe");
        im.setType("xmpp");
        user.getIms().add(im);

        ScimUserPhoto photo = new ScimUserPhoto();
        photo.setUser(user);
        photo.setValue("https://example.com/photo.jpg");
        photo.setType("photo");
        user.getPhotos().add(photo);

        return user;
    }

    private List<Map<String, Object>> buildGroups() {
        return List.of(
            Map.of("value", UUID.randomUUID().toString(), "display", "Engineering",
                   "$ref", baseUrl + "/Groups/" + UUID.randomUUID()),
            Map.of("value", UUID.randomUUID().toString(), "display", "Backend Team",
                   "$ref", baseUrl + "/Groups/" + UUID.randomUUID())
        );
    }

    private Map<String, Object> buildSimpleScimInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("userName", "new.user@example.com");
        input.put("displayName", "New User");
        input.put("active", true);
        return input;
    }

    private Map<String, Object> buildFullScimInput() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("userName", "new.user@example.com");
        input.put("externalId", "new-ext-id");
        input.put("displayName", "New User");
        input.put("nickName", "Newbie");
        input.put("profileUrl", "https://example.com/newuser");
        input.put("title", "Junior Engineer");
        input.put("userType", "Employee");
        input.put("preferredLanguage", "en");
        input.put("locale", "en_US");
        input.put("timezone", "America/Chicago");
        input.put("active", true);

        Map<String, Object> name = new LinkedHashMap<>();
        name.put("formatted", "New User");
        name.put("familyName", "User");
        name.put("givenName", "New");
        name.put("middleName", "M");
        input.put("name", name);

        input.put("emails", List.of(
            Map.of("value", "new@example.com", "type", "work", "primary", true),
            Map.of("value", "new.personal@example.com", "type", "home", "primary", false)
        ));
        input.put("phoneNumbers", List.of(
            Map.of("value", "+1-555-1234", "type", "work", "primary", true)
        ));
        input.put("addresses", List.of(
            Map.of("streetAddress", "456 Oak Ave", "locality", "Chicago",
                   "region", "IL", "postalCode", "60601", "country", "US",
                   "type", "work", "primary", true)
        ));

        Map<String, Object> enterprise = new LinkedHashMap<>();
        enterprise.put("employeeNumber", "E99999");
        enterprise.put("costCenter", "CC9999");
        enterprise.put("organization", "Test Org");
        enterprise.put("department", "QA");
        enterprise.put("manager", Map.of("value", "mgr-123", "displayName", "Boss Man"));
        input.put("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User", enterprise);

        return input;
    }
}
