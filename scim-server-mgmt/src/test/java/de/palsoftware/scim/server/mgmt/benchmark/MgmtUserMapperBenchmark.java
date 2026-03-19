package de.palsoftware.scim.server.mgmt.benchmark;

import de.palsoftware.scim.server.common.model.ScimUser;
import de.palsoftware.scim.server.common.model.ScimUserAddress;
import de.palsoftware.scim.server.common.model.ScimUserEmail;
import de.palsoftware.scim.server.common.model.ScimUserEntitlement;
import de.palsoftware.scim.server.common.model.ScimUserIm;
import de.palsoftware.scim.server.common.model.ScimUserPhoneNumber;
import de.palsoftware.scim.server.common.model.ScimUserPhoto;
import de.palsoftware.scim.server.common.model.ScimUserRole;
import de.palsoftware.scim.server.common.model.ScimUserX509Certificate;
import de.palsoftware.scim.server.common.repository.WorkspaceDataStats;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for ManagementController mapping methods.
 * These are the same mapping patterns used in the controller,
 * extracted here for isolated measurement.
 */
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(0)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class MgmtUserMapperBenchmark {

    private ScimUser minimalUser;
    private ScimUser fullUser;
    private List<ScimUser> batchUsers;
    private WorkspaceDataStats stats;

    @Setup(Level.Trial)
    public void setup() {
        minimalUser = buildMinimalUser();
        fullUser = buildFullUser();
        stats = new WorkspaceDataStats(100, 20, 5, 500, 200, 150, 50, 30, 20, 10, 5, 3, 80, 1048576);

        batchUsers = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            batchUsers.add(buildFullUser());
        }
    }

    // ── Baseline benchmarks ──

    @Benchmark
    public Map<String, Object> mapMinimalUser() {
        return userToMap(minimalUser);
    }

    @Benchmark
    public Map<String, Object> mapFullUser() {
        return userToMap(fullUser);
    }

    @Benchmark
    public void mapBatch100Users(Blackhole bh) {
        for (ScimUser user : batchUsers) {
            bh.consume(userToMap(user));
        }
    }

    @Benchmark
    public Map<String, Object> mapUserLookup() {
        return userLookupToMap(fullUser);
    }

    @Benchmark
    public Map<String, Object> mapWorkspaceStats() {
        return workspaceStatsToMap(stats);
    }

    // ── Optimized benchmarks ──

    @Benchmark
    public Map<String, Object> mapMinimalUserOpt() {
        return userToMapOpt(minimalUser);
    }

    @Benchmark
    public Map<String, Object> mapFullUserOpt() {
        return userToMapOpt(fullUser);
    }

    @Benchmark
    public void mapBatch100UsersOpt(Blackhole bh) {
        for (ScimUser user : batchUsers) {
            bh.consume(userToMapOpt(user));
        }
    }

    @Benchmark
    public Map<String, Object> mapUserLookupOpt() {
        return userLookupToMapOpt(fullUser);
    }

    @Benchmark
    public Map<String, Object> mapWorkspaceStatsOpt() {
        return workspaceStatsToMapOpt(stats);
    }

    // ── Mapping methods (exact copy from ManagementController) ──

    private Map<String, Object> userToMap(ScimUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId().toString());
        map.put("userName", user.getUserName());
        map.put("displayName", user.getDisplayName());
        map.put("externalId", user.getExternalId());
        map.put("name", userNameToMap(user));
        map.put("nickName", user.getNickName());
        map.put("profileUrl", user.getProfileUrl());
        map.put("title", user.getTitle());
        map.put("userType", user.getUserType());
        map.put("preferredLanguage", user.getPreferredLanguage());
        map.put("locale", user.getLocale());
        map.put("timezone", user.getTimezone());
        map.put("enterprise", enterpriseToMap(user));
        map.put("emails", user.getEmails().stream()
            .map(email -> multiValueToMap(email.getValue(), email.getType(), email.getDisplay(), email.isPrimaryFlag()))
            .toList());
        map.put("phoneNumbers", user.getPhoneNumbers().stream()
            .map(phone -> multiValueToMap(phone.getValue(), phone.getType(), phone.getDisplay(), phone.isPrimaryFlag()))
            .toList());
        map.put("addresses", user.getAddresses().stream()
            .map(this::addressToMap)
            .toList());
        map.put("entitlements", user.getEntitlements().stream()
            .map(e -> multiValueToMap(e.getValue(), e.getType(), e.getDisplay(), e.isPrimaryFlag()))
            .toList());
        map.put("roles", user.getRoles().stream()
            .map(r -> multiValueToMap(r.getValue(), r.getType(), r.getDisplay(), r.isPrimaryFlag()))
            .toList());
        map.put("ims", user.getIms().stream()
            .map(im -> multiValueToMap(im.getValue(), im.getType(), im.getDisplay(), im.isPrimaryFlag()))
            .toList());
        map.put("photos", user.getPhotos().stream()
            .map(photo -> multiValueToMap(photo.getValue(), photo.getType(), photo.getDisplay(), photo.isPrimaryFlag()))
            .toList());
        map.put("x509Certificates", user.getX509Certificates().stream()
            .map(cert -> multiValueToMap(cert.getValue(), cert.getType(), cert.getDisplay(), cert.isPrimaryFlag()))
            .toList());
        map.put("active", user.isActive());
        map.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        map.put("lastModified", user.getLastModified() != null ? user.getLastModified().toString() : null);
        map.put("meta", metaToMap(user.getCreatedAt(), user.getLastModified(), user.getVersion()));
        return map;
    }

    private Map<String, Object> userLookupToMap(ScimUser user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", user.getId().toString());
        map.put("userName", user.getUserName());
        map.put("displayName", user.getDisplayName());
        return map;
    }

    private Map<String, Object> userNameToMap(ScimUser user) {
        if (user.getNameFormatted() == null
                && user.getNameFamilyName() == null
                && user.getNameGivenName() == null
                && user.getNameMiddleName() == null
                && user.getNameHonorificPrefix() == null
                && user.getNameHonorificSuffix() == null) {
            return Map.of();
        }
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("formatted", user.getNameFormatted());
        name.put("familyName", user.getNameFamilyName());
        name.put("givenName", user.getNameGivenName());
        name.put("middleName", user.getNameMiddleName());
        name.put("honorificPrefix", user.getNameHonorificPrefix());
        name.put("honorificSuffix", user.getNameHonorificSuffix());
        return name;
    }

    private Map<String, Object> enterpriseToMap(ScimUser user) {
        if (user.getEnterpriseEmployeeNumber() == null
                && user.getEnterpriseCostCenter() == null
                && user.getEnterpriseOrganization() == null
                && user.getEnterpriseDivision() == null
                && user.getEnterpriseDepartment() == null
                && user.getEnterpriseManagerValue() == null
                && user.getEnterpriseManagerRef() == null
                && user.getEnterpriseManagerDisplay() == null) {
            return Map.of();
        }
        Map<String, Object> enterprise = new LinkedHashMap<>();
        enterprise.put("employeeNumber", user.getEnterpriseEmployeeNumber());
        enterprise.put("costCenter", user.getEnterpriseCostCenter());
        enterprise.put("organization", user.getEnterpriseOrganization());
        enterprise.put("division", user.getEnterpriseDivision());
        enterprise.put("department", user.getEnterpriseDepartment());
        Map<String, Object> manager = new LinkedHashMap<>();
        manager.put("value", user.getEnterpriseManagerValue());
        manager.put("ref", user.getEnterpriseManagerRef());
        manager.put("display", user.getEnterpriseManagerDisplay());
        enterprise.put("manager", manager);
        return enterprise;
    }

    private Map<String, Object> addressToMap(ScimUserAddress address) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("formatted", address.getFormatted());
        map.put("streetAddress", address.getStreetAddress());
        map.put("locality", address.getLocality());
        map.put("region", address.getRegion());
        map.put("postalCode", address.getPostalCode());
        map.put("country", address.getCountry());
        map.put("type", address.getType());
        map.put("primary", address.isPrimaryFlag());
        return map;
    }

    private Map<String, Object> multiValueToMap(String value, String type, String display, boolean primary) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("value", value);
        map.put("type", type);
        map.put("display", display);
        map.put("primary", primary);
        return map;
    }

    private Map<String, Object> metaToMap(Instant createdAt, Instant lastModified, Long version) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("createdAt", createdAt != null ? createdAt.toString() : null);
        meta.put("lastModified", lastModified != null ? lastModified.toString() : null);
        meta.put("version", version);
        return meta;
    }

    private Map<String, Object> workspaceStatsToMap(WorkspaceDataStats stats) {
        Map<String, Object> map = new LinkedHashMap<>();

        Map<String, Object> objects = new LinkedHashMap<>();
        objects.put("total", stats.objectCount());
        objects.put("users", stats.userCount());
        objects.put("groups", stats.groupCount());
        objects.put("tokens", stats.tokenCount());
        objects.put("logs", stats.logCount());
        objects.put("userAttributeRows", stats.userAttributeObjectCount());

        Map<String, Object> userAttributes = new LinkedHashMap<>();
        userAttributes.put("emails", stats.emailCount());
        userAttributes.put("phoneNumbers", stats.phoneNumberCount());
        userAttributes.put("addresses", stats.addressCount());
        userAttributes.put("entitlements", stats.entitlementCount());
        userAttributes.put("roles", stats.roleCount());
        userAttributes.put("ims", stats.imCount());
        userAttributes.put("photos", stats.photoCount());
        userAttributes.put("x509Certificates", stats.x509CertificateCount());
        objects.put("userAttributes", userAttributes);

        Map<String, Object> relations = new LinkedHashMap<>();
        relations.put("total", stats.relationCount());
        relations.put("groupMemberships", stats.groupMembershipCount());

        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("estimatedRowBytes", stats.estimatedRowBytes());
        storage.put("storedRows", stats.storedRowCount());

        map.put("objects", objects);
        map.put("relations", relations);
        map.put("storage", storage);
        return map;
    }

    // ── Optimized mapping methods ──

    private Map<String, Object> userToMapOpt(ScimUser user) {
        Map<String, Object> map = new LinkedHashMap<>(22, 1.0f);
        map.put("id", user.getId().toString());
        map.put("userName", user.getUserName());
        map.put("displayName", user.getDisplayName());
        map.put("externalId", user.getExternalId());
        map.put("name", userNameToMapOpt(user));
        map.put("nickName", user.getNickName());
        map.put("profileUrl", user.getProfileUrl());
        map.put("title", user.getTitle());
        map.put("userType", user.getUserType());
        map.put("preferredLanguage", user.getPreferredLanguage());
        map.put("locale", user.getLocale());
        map.put("timezone", user.getTimezone());
        map.put("enterprise", enterpriseToMapOpt(user));

        List<ScimUserEmail> emails = user.getEmails();
        List<Map<String, Object>> emailList = new ArrayList<>(emails.size());
        for (ScimUserEmail email : emails) {
            emailList.add(multiValueToMapOpt(email.getValue(), email.getType(), email.getDisplay(), email.isPrimaryFlag()));
        }
        map.put("emails", emailList);

        List<ScimUserPhoneNumber> phones = user.getPhoneNumbers();
        List<Map<String, Object>> phoneList = new ArrayList<>(phones.size());
        for (ScimUserPhoneNumber phone : phones) {
            phoneList.add(multiValueToMapOpt(phone.getValue(), phone.getType(), phone.getDisplay(), phone.isPrimaryFlag()));
        }
        map.put("phoneNumbers", phoneList);

        List<ScimUserAddress> addresses = user.getAddresses();
        List<Map<String, Object>> addrList = new ArrayList<>(addresses.size());
        for (ScimUserAddress addr : addresses) {
            addrList.add(addressToMapOpt(addr));
        }
        map.put("addresses", addrList);

        List<ScimUserEntitlement> entitlements = user.getEntitlements();
        List<Map<String, Object>> entList = new ArrayList<>(entitlements.size());
        for (ScimUserEntitlement e : entitlements) {
            entList.add(multiValueToMapOpt(e.getValue(), e.getType(), e.getDisplay(), e.isPrimaryFlag()));
        }
        map.put("entitlements", entList);

        List<ScimUserRole> roles = user.getRoles();
        List<Map<String, Object>> roleList = new ArrayList<>(roles.size());
        for (ScimUserRole r : roles) {
            roleList.add(multiValueToMapOpt(r.getValue(), r.getType(), r.getDisplay(), r.isPrimaryFlag()));
        }
        map.put("roles", roleList);

        List<ScimUserIm> ims = user.getIms();
        List<Map<String, Object>> imList = new ArrayList<>(ims.size());
        for (ScimUserIm im : ims) {
            imList.add(multiValueToMapOpt(im.getValue(), im.getType(), im.getDisplay(), im.isPrimaryFlag()));
        }
        map.put("ims", imList);

        List<ScimUserPhoto> photos = user.getPhotos();
        List<Map<String, Object>> photoList = new ArrayList<>(photos.size());
        for (ScimUserPhoto photo : photos) {
            photoList.add(multiValueToMapOpt(photo.getValue(), photo.getType(), photo.getDisplay(), photo.isPrimaryFlag()));
        }
        map.put("photos", photoList);

        List<ScimUserX509Certificate> certs = user.getX509Certificates();
        List<Map<String, Object>> certList = new ArrayList<>(certs.size());
        for (ScimUserX509Certificate cert : certs) {
            certList.add(multiValueToMapOpt(cert.getValue(), cert.getType(), cert.getDisplay(), cert.isPrimaryFlag()));
        }
        map.put("x509Certificates", certList);

        map.put("active", user.isActive());
        map.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
        map.put("lastModified", user.getLastModified() != null ? user.getLastModified().toString() : null);
        map.put("meta", metaToMapOpt(user.getCreatedAt(), user.getLastModified(), user.getVersion()));
        return map;
    }

    private Map<String, Object> userLookupToMapOpt(ScimUser user) {
        Map<String, Object> map = new LinkedHashMap<>(3, 1.0f);
        map.put("id", user.getId().toString());
        map.put("userName", user.getUserName());
        map.put("displayName", user.getDisplayName());
        return map;
    }

    private Map<String, Object> userNameToMapOpt(ScimUser user) {
        if (user.getNameFormatted() == null
                && user.getNameFamilyName() == null
                && user.getNameGivenName() == null
                && user.getNameMiddleName() == null
                && user.getNameHonorificPrefix() == null
                && user.getNameHonorificSuffix() == null) {
            return Map.of();
        }
        Map<String, Object> name = new LinkedHashMap<>(6, 1.0f);
        name.put("formatted", user.getNameFormatted());
        name.put("familyName", user.getNameFamilyName());
        name.put("givenName", user.getNameGivenName());
        name.put("middleName", user.getNameMiddleName());
        name.put("honorificPrefix", user.getNameHonorificPrefix());
        name.put("honorificSuffix", user.getNameHonorificSuffix());
        return name;
    }

    private Map<String, Object> enterpriseToMapOpt(ScimUser user) {
        if (user.getEnterpriseEmployeeNumber() == null
                && user.getEnterpriseCostCenter() == null
                && user.getEnterpriseOrganization() == null
                && user.getEnterpriseDivision() == null
                && user.getEnterpriseDepartment() == null
                && user.getEnterpriseManagerValue() == null
                && user.getEnterpriseManagerRef() == null
                && user.getEnterpriseManagerDisplay() == null) {
            return Map.of();
        }
        Map<String, Object> enterprise = new LinkedHashMap<>(6, 1.0f);
        enterprise.put("employeeNumber", user.getEnterpriseEmployeeNumber());
        enterprise.put("costCenter", user.getEnterpriseCostCenter());
        enterprise.put("organization", user.getEnterpriseOrganization());
        enterprise.put("division", user.getEnterpriseDivision());
        enterprise.put("department", user.getEnterpriseDepartment());
        Map<String, Object> manager = new LinkedHashMap<>(3, 1.0f);
        manager.put("value", user.getEnterpriseManagerValue());
        manager.put("ref", user.getEnterpriseManagerRef());
        manager.put("display", user.getEnterpriseManagerDisplay());
        enterprise.put("manager", manager);
        return enterprise;
    }

    private Map<String, Object> addressToMapOpt(ScimUserAddress address) {
        Map<String, Object> map = new LinkedHashMap<>(8, 1.0f);
        map.put("formatted", address.getFormatted());
        map.put("streetAddress", address.getStreetAddress());
        map.put("locality", address.getLocality());
        map.put("region", address.getRegion());
        map.put("postalCode", address.getPostalCode());
        map.put("country", address.getCountry());
        map.put("type", address.getType());
        map.put("primary", address.isPrimaryFlag());
        return map;
    }

    private Map<String, Object> multiValueToMapOpt(String value, String type, String display, boolean primary) {
        Map<String, Object> map = new LinkedHashMap<>(4, 1.0f);
        map.put("value", value);
        map.put("type", type);
        map.put("display", display);
        map.put("primary", primary);
        return map;
    }

    private Map<String, Object> metaToMapOpt(Instant createdAt, Instant lastModified, Long version) {
        Map<String, Object> meta = new LinkedHashMap<>(3, 1.0f);
        meta.put("createdAt", createdAt != null ? createdAt.toString() : null);
        meta.put("lastModified", lastModified != null ? lastModified.toString() : null);
        meta.put("version", version);
        return meta;
    }

    private Map<String, Object> workspaceStatsToMapOpt(WorkspaceDataStats stats) {
        Map<String, Object> map = new LinkedHashMap<>(3, 1.0f);

        Map<String, Object> objects = new LinkedHashMap<>(7, 1.0f);
        objects.put("total", stats.objectCount());
        objects.put("users", stats.userCount());
        objects.put("groups", stats.groupCount());
        objects.put("tokens", stats.tokenCount());
        objects.put("logs", stats.logCount());
        objects.put("userAttributeRows", stats.userAttributeObjectCount());

        Map<String, Object> userAttributes = new LinkedHashMap<>(8, 1.0f);
        userAttributes.put("emails", stats.emailCount());
        userAttributes.put("phoneNumbers", stats.phoneNumberCount());
        userAttributes.put("addresses", stats.addressCount());
        userAttributes.put("entitlements", stats.entitlementCount());
        userAttributes.put("roles", stats.roleCount());
        userAttributes.put("ims", stats.imCount());
        userAttributes.put("photos", stats.photoCount());
        userAttributes.put("x509Certificates", stats.x509CertificateCount());
        objects.put("userAttributes", userAttributes);

        Map<String, Object> relations = new LinkedHashMap<>(2, 1.0f);
        relations.put("total", stats.relationCount());
        relations.put("groupMemberships", stats.groupMembershipCount());

        Map<String, Object> storage = new LinkedHashMap<>(2, 1.0f);
        storage.put("estimatedRowBytes", stats.estimatedRowBytes());
        storage.put("storedRows", stats.storedRowCount());

        map.put("objects", objects);
        map.put("relations", relations);
        map.put("storage", storage);
        return map;
    }

    // ── Fixture builders ──

    private ScimUser buildMinimalUser() {
        ScimUser user = new ScimUser();
        user.setId(UUID.randomUUID());
        user.setUserName("minimal.user");
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setLastModified(Instant.now());
        user.setVersion(1L);
        return user;
    }

    private ScimUser buildFullUser() {
        ScimUser user = new ScimUser();
        user.setId(UUID.randomUUID());
        user.setUserName("john.doe");
        user.setDisplayName("John Doe");
        user.setExternalId("EXT-12345");
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setLastModified(Instant.now());
        user.setVersion(5L);

        // Name
        user.setNameFormatted("Mr. John Michael Doe Jr.");
        user.setNameFamilyName("Doe");
        user.setNameGivenName("John");
        user.setNameMiddleName("Michael");
        user.setNameHonorificPrefix("Mr.");
        user.setNameHonorificSuffix("Jr.");

        // Profile
        user.setNickName("JD");
        user.setProfileUrl("https://profiles.example.com/johndoe");
        user.setTitle("Senior Engineer");
        user.setUserType("Employee");
        user.setPreferredLanguage("en-US");
        user.setLocale("en_US");
        user.setTimezone("America/New_York");

        // Enterprise
        user.setEnterpriseEmployeeNumber("EMP-001");
        user.setEnterpriseCostCenter("CC-42");
        user.setEnterpriseOrganization("Engineering");
        user.setEnterpriseDivision("Platform");
        user.setEnterpriseDepartment("Identity");
        user.setEnterpriseManagerValue("mgr-uuid-001");
        user.setEnterpriseManagerRef("https://scim.example.com/Users/mgr-uuid-001");
        user.setEnterpriseManagerDisplay("Jane Manager");

        // Emails
        addEmail(user, "john.doe@work.com", "work", "John Doe", true);
        addEmail(user, "john@personal.com", "home", "John", false);

        // Phone numbers
        addPhone(user, "+1-555-1234", "work", null, true);
        addPhone(user, "+1-555-5678", "mobile", null, false);

        // Addresses
        addAddress(user, "123 Main St, NY 10001", "123 Main St", "New York", "NY", "10001", "US", "work", true);

        // Entitlements
        addEntitlement(user, "admin-access", "app", "Admin Access", true);

        // Roles
        addRole(user, "engineer", "department", "Engineering", true);

        // IMs
        addIm(user, "john.doe", "xmpp", null, true);

        // Photos
        addPhoto(user, "https://photos.example.com/john.jpg", "photo", null, true);

        // X509 Certificates
        addX509(user, "MIIB...base64cert", "signing", null, true);

        return user;
    }

    private void addEmail(ScimUser user, String value, String type, String display, boolean primary) {
        ScimUserEmail e = new ScimUserEmail();
        e.setUser(user);
        e.setValue(value);
        e.setType(type);
        e.setDisplay(display);
        e.setPrimaryFlag(primary);
        user.getEmails().add(e);
    }

    private void addPhone(ScimUser user, String value, String type, String display, boolean primary) {
        ScimUserPhoneNumber p = new ScimUserPhoneNumber();
        p.setUser(user);
        p.setValue(value);
        p.setType(type);
        p.setDisplay(display);
        p.setPrimaryFlag(primary);
        user.getPhoneNumbers().add(p);
    }

    private void addAddress(ScimUser user, String formatted, String street, String locality,
                            String region, String postalCode, String country, String type, boolean primary) {
        ScimUserAddress a = new ScimUserAddress();
        a.setUser(user);
        a.setFormatted(formatted);
        a.setStreetAddress(street);
        a.setLocality(locality);
        a.setRegion(region);
        a.setPostalCode(postalCode);
        a.setCountry(country);
        a.setType(type);
        a.setPrimaryFlag(primary);
        user.getAddresses().add(a);
    }

    private void addEntitlement(ScimUser user, String value, String type, String display, boolean primary) {
        ScimUserEntitlement e = new ScimUserEntitlement();
        e.setUser(user);
        e.setValue(value);
        e.setType(type);
        e.setDisplay(display);
        e.setPrimaryFlag(primary);
        user.getEntitlements().add(e);
    }

    private void addRole(ScimUser user, String value, String type, String display, boolean primary) {
        ScimUserRole r = new ScimUserRole();
        r.setUser(user);
        r.setValue(value);
        r.setType(type);
        r.setDisplay(display);
        r.setPrimaryFlag(primary);
        user.getRoles().add(r);
    }

    private void addIm(ScimUser user, String value, String type, String display, boolean primary) {
        ScimUserIm im = new ScimUserIm();
        im.setUser(user);
        im.setValue(value);
        im.setType(type);
        im.setDisplay(display);
        im.setPrimaryFlag(primary);
        user.getIms().add(im);
    }

    private void addPhoto(ScimUser user, String value, String type, String display, boolean primary) {
        ScimUserPhoto p = new ScimUserPhoto();
        p.setUser(user);
        p.setValue(value);
        p.setType(type);
        p.setDisplay(display);
        p.setPrimaryFlag(primary);
        user.getPhotos().add(p);
    }

    private void addX509(ScimUser user, String value, String type, String display, boolean primary) {
        ScimUserX509Certificate c = new ScimUserX509Certificate();
        c.setUser(user);
        c.setValue(value);
        c.setType(type);
        c.setDisplay(display);
        c.setPrimaryFlag(primary);
        user.getX509Certificates().add(c);
    }
}
