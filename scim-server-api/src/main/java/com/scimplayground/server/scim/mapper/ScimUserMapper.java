package com.scimplayground.server.scim.mapper;

import com.scimplayground.server.model.*;
import com.scimplayground.server.scim.error.ScimException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts between JPA entities and SCIM JSON Maps.
 * All SCIM responses are built as Map<String,Object> for maximum flexibility.
 */
public class ScimUserMapper {

    public static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String ENTERPRISE_SCHEMA = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";

    private static final Set<String> EMAIL_TYPES = Set.of("work", "home", "other");
    private static final Set<String> PHONE_TYPES = Set.of("work", "home", "mobile", "fax", "pager", "other");
    private static final Set<String> IM_TYPES = Set.of("aim", "gtalk", "icq", "xmpp", "skype", "qq", "msn", "yahoo");
    private static final Set<String> PHOTO_TYPES = Set.of("photo", "thumbnail");
    private static final Set<String> ADDRESS_TYPES = Set.of("work", "home", "other");

    /**
     * Convert ScimUser entity to SCIM JSON response map.
     */
    public static Map<String, Object> toScimResponse(ScimUser user, String baseUrl,
                                                       List<Map<String, Object>> groups) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Determine schemas
        List<String> schemas = new ArrayList<>();
        schemas.add(USER_SCHEMA);
        if (hasEnterpriseData(user)) {
            schemas.add(ENTERPRISE_SCHEMA);
        }
        result.put("schemas", schemas);
        result.put("id", user.getId().toString());

        if (user.getExternalId() != null) {
            result.put("externalId", user.getExternalId());
        }

        result.put("userName", user.getUserName());

        // Name complex attribute
        Map<String, Object> name = buildNameMap(user);
        if (!name.isEmpty()) {
            result.put("name", name);
        }

        if (user.getDisplayName() != null) result.put("displayName", user.getDisplayName());
        if (user.getNickName() != null) result.put("nickName", user.getNickName());
        if (user.getProfileUrl() != null) result.put("profileUrl", user.getProfileUrl());
        if (user.getTitle() != null) result.put("title", user.getTitle());
        if (user.getUserType() != null) result.put("userType", user.getUserType());
        if (user.getPreferredLanguage() != null) result.put("preferredLanguage", user.getPreferredLanguage());
        if (user.getLocale() != null) result.put("locale", user.getLocale());
        if (user.getTimezone() != null) result.put("timezone", user.getTimezone());

        result.put("active", user.isActive());

        // Multi-valued attributes
        if (user.getEmails() != null && !user.getEmails().isEmpty()) {
            result.put("emails", user.getEmails().stream().map(ScimUserMapper::emailToMap).collect(Collectors.toList()));
        }
        if (user.getPhoneNumbers() != null && !user.getPhoneNumbers().isEmpty()) {
            result.put("phoneNumbers", user.getPhoneNumbers().stream().map(ScimUserMapper::phoneToMap).collect(Collectors.toList()));
        }
        if (user.getIms() != null && !user.getIms().isEmpty()) {
            result.put("ims", user.getIms().stream().map(ScimUserMapper::imToMap).collect(Collectors.toList()));
        }
        if (user.getPhotos() != null && !user.getPhotos().isEmpty()) {
            result.put("photos", user.getPhotos().stream().map(ScimUserMapper::photoToMap).collect(Collectors.toList()));
        }
        if (user.getAddresses() != null && !user.getAddresses().isEmpty()) {
            result.put("addresses", user.getAddresses().stream().map(ScimUserMapper::addressToMap).collect(Collectors.toList()));
        }
        if (user.getEntitlements() != null && !user.getEntitlements().isEmpty()) {
            result.put("entitlements", user.getEntitlements().stream().map(ScimUserMapper::entitlementToMap).collect(Collectors.toList()));
        }
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            result.put("roles", user.getRoles().stream().map(ScimUserMapper::roleToMap).collect(Collectors.toList()));
        }
        if (user.getX509Certificates() != null && !user.getX509Certificates().isEmpty()) {
            result.put("x509Certificates", user.getX509Certificates().stream().map(ScimUserMapper::certToMap).collect(Collectors.toList()));
        }

        // Groups (read-only, computed)
        if (groups != null && !groups.isEmpty()) {
            result.put("groups", groups);
        }

        // Enterprise extension
        if (hasEnterpriseData(user)) {
            Map<String, Object> ent = new LinkedHashMap<>();
            if (user.getEnterpriseEmployeeNumber() != null) ent.put("employeeNumber", user.getEnterpriseEmployeeNumber());
            if (user.getEnterpriseCostCenter() != null) ent.put("costCenter", user.getEnterpriseCostCenter());
            if (user.getEnterpriseOrganization() != null) ent.put("organization", user.getEnterpriseOrganization());
            if (user.getEnterpriseDivision() != null) ent.put("division", user.getEnterpriseDivision());
            if (user.getEnterpriseDepartment() != null) ent.put("department", user.getEnterpriseDepartment());
            if (user.getEnterpriseManagerValue() != null || user.getEnterpriseManagerRef() != null || user.getEnterpriseManagerDisplay() != null) {
                Map<String, Object> manager = new LinkedHashMap<>();
                if (user.getEnterpriseManagerValue() != null) manager.put("value", user.getEnterpriseManagerValue());
                if (user.getEnterpriseManagerRef() != null) manager.put("$ref", user.getEnterpriseManagerRef());
                if (user.getEnterpriseManagerDisplay() != null) manager.put("displayName", user.getEnterpriseManagerDisplay());
                ent.put("manager", manager);
            }
            result.put(ENTERPRISE_SCHEMA, ent);
        }

        // Meta
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("resourceType", "User");
        meta.put("created", user.getCreatedAt().toString());
        meta.put("lastModified", user.getLastModified().toString());
        meta.put("location", baseUrl + "/Users/" + user.getId());
        meta.put("version", "W/\"" + user.getVersion() + "\"");
        result.put("meta", meta);

        return result;
    }

    /**
     * Apply SCIM JSON input to a ScimUser entity (for CREATE and PUT).
     */
    @SuppressWarnings("unchecked")
    public static void applyFromScimInput(ScimUser user, Map<String, Object> input) {
        if (input.containsKey("userName")) user.setUserName((String) input.get("userName"));
        if (input.containsKey("externalId")) user.setExternalId((String) input.get("externalId"));
        if (input.containsKey("displayName")) user.setDisplayName((String) input.get("displayName"));
        if (input.containsKey("nickName")) user.setNickName((String) input.get("nickName"));
        if (input.containsKey("profileUrl")) {
            String profileUrl = (String) input.get("profileUrl");
            user.setProfileUrl(profileUrl);
        }
        if (input.containsKey("title")) user.setTitle((String) input.get("title"));
        if (input.containsKey("userType")) user.setUserType((String) input.get("userType"));
        if (input.containsKey("preferredLanguage")) user.setPreferredLanguage((String) input.get("preferredLanguage"));
        if (input.containsKey("locale")) user.setLocale((String) input.get("locale"));
        if (input.containsKey("timezone")) user.setTimezone((String) input.get("timezone"));
        if (input.containsKey("active")) user.setActive(toBoolean(input.get("active")));
        if (input.containsKey("password")) user.setPassword((String) input.get("password"));

        // Name complex attribute
        if (input.containsKey("name")) {
            Object nameObj = input.get("name");
            if (nameObj instanceof Map) {
                Map<String, Object> nameMap = (Map<String, Object>) nameObj;
                user.setNameFormatted((String) nameMap.get("formatted"));
                user.setNameFamilyName((String) nameMap.get("familyName"));
                user.setNameGivenName((String) nameMap.get("givenName"));
                user.setNameMiddleName((String) nameMap.get("middleName"));
                user.setNameHonorificPrefix((String) nameMap.get("honorificPrefix"));
                user.setNameHonorificSuffix((String) nameMap.get("honorificSuffix"));
            }
        }

        // Emails
        if (input.containsKey("emails")) {
            user.getEmails().clear();
            List<Map<String, Object>> emailsList = (List<Map<String, Object>>) input.get("emails");
            if (emailsList != null) {
                for (Map<String, Object> em : emailsList) {
                    ScimUserEmail email = new ScimUserEmail();
                    email.setUser(user);
                    email.setValue((String) em.get("value"));
                    email.setType(normalizeCanonical((String) em.get("type"), EMAIL_TYPES, "emails.type"));
                    email.setDisplay((String) em.get("display"));
                    email.setPrimaryFlag(toBoolean(em.get("primary")));
                    user.getEmails().add(email);
                }
            }
        }

        // Phone numbers
        if (input.containsKey("phoneNumbers")) {
            user.getPhoneNumbers().clear();
            List<Map<String, Object>> phoneList = (List<Map<String, Object>>) input.get("phoneNumbers");
            if (phoneList != null) {
                for (Map<String, Object> ph : phoneList) {
                    ScimUserPhoneNumber phone = new ScimUserPhoneNumber();
                    phone.setUser(user);
                    phone.setValue((String) ph.get("value"));
                    phone.setType(normalizeCanonical((String) ph.get("type"), PHONE_TYPES, "phoneNumbers.type"));
                    phone.setDisplay((String) ph.get("display"));
                    phone.setPrimaryFlag(toBoolean(ph.get("primary")));
                    user.getPhoneNumbers().add(phone);
                }
            }
        }

        // Addresses
        if (input.containsKey("addresses")) {
            user.getAddresses().clear();
            List<Map<String, Object>> addrList = (List<Map<String, Object>>) input.get("addresses");
            if (addrList != null) {
                for (Map<String, Object> addr : addrList) {
                    ScimUserAddress address = new ScimUserAddress();
                    address.setUser(user);
                    address.setFormatted((String) addr.get("formatted"));
                    address.setStreetAddress((String) addr.get("streetAddress"));
                    address.setLocality((String) addr.get("locality"));
                    address.setRegion((String) addr.get("region"));
                    address.setPostalCode((String) addr.get("postalCode"));
                    address.setCountry((String) addr.get("country"));
                    address.setType(normalizeCanonical((String) addr.get("type"), ADDRESS_TYPES, "addresses.type"));
                    address.setPrimaryFlag(toBoolean(addr.get("primary")));
                    user.getAddresses().add(address);
                }
            }
        }

        // IMs
        if (input.containsKey("ims")) {
            user.getIms().clear();
            List<Map<String, Object>> imsList = (List<Map<String, Object>>) input.get("ims");
            if (imsList != null) {
                for (Map<String, Object> im : imsList) {
                    ScimUserIm imEntity = new ScimUserIm();
                    imEntity.setUser(user);
                    imEntity.setValue((String) im.get("value"));
                    imEntity.setType(normalizeCanonical((String) im.get("type"), IM_TYPES, "ims.type"));
                    imEntity.setDisplay((String) im.get("display"));
                    imEntity.setPrimaryFlag(toBoolean(im.get("primary")));
                    user.getIms().add(imEntity);
                }
            }
        }

        // Photos
        if (input.containsKey("photos")) {
            user.getPhotos().clear();
            List<Map<String, Object>> photoList = (List<Map<String, Object>>) input.get("photos");
            if (photoList != null) {
                for (Map<String, Object> ph : photoList) {
                    ScimUserPhoto photo = new ScimUserPhoto();
                    photo.setUser(user);
                    String photoValue = (String) ph.get("value");
                    photo.setValue(photoValue);
                    photo.setType(normalizeCanonical((String) ph.get("type"), PHOTO_TYPES, "photos.type"));
                    photo.setDisplay((String) ph.get("display"));
                    photo.setPrimaryFlag(toBoolean(ph.get("primary")));
                    user.getPhotos().add(photo);
                }
            }
        }

        // Entitlements
        if (input.containsKey("entitlements")) {
            user.getEntitlements().clear();
            List<Map<String, Object>> entList = (List<Map<String, Object>>) input.get("entitlements");
            if (entList != null) {
                for (Map<String, Object> ent : entList) {
                    ScimUserEntitlement entitlement = new ScimUserEntitlement();
                    entitlement.setUser(user);
                    entitlement.setValue((String) ent.get("value"));
                    entitlement.setType((String) ent.get("type"));
                    entitlement.setDisplay((String) ent.get("display"));
                    entitlement.setPrimaryFlag(toBoolean(ent.get("primary")));
                    user.getEntitlements().add(entitlement);
                }
            }
        }

        // Roles
        if (input.containsKey("roles")) {
            user.getRoles().clear();
            List<Map<String, Object>> roleList = (List<Map<String, Object>>) input.get("roles");
            if (roleList != null) {
                for (Map<String, Object> role : roleList) {
                    ScimUserRole roleEntity = new ScimUserRole();
                    roleEntity.setUser(user);
                    roleEntity.setValue((String) role.get("value"));
                    roleEntity.setType((String) role.get("type"));
                    roleEntity.setDisplay((String) role.get("display"));
                    roleEntity.setPrimaryFlag(toBoolean(role.get("primary")));
                    user.getRoles().add(roleEntity);
                }
            }
        }

        // X509 Certificates
        if (input.containsKey("x509Certificates")) {
            user.getX509Certificates().clear();
            List<Map<String, Object>> certList = (List<Map<String, Object>>) input.get("x509Certificates");
            if (certList != null) {
                for (Map<String, Object> cert : certList) {
                    ScimUserX509Certificate certEntity = new ScimUserX509Certificate();
                    certEntity.setUser(user);
                    certEntity.setValue((String) cert.get("value"));
                    certEntity.setType((String) cert.get("type"));
                    certEntity.setDisplay((String) cert.get("display"));
                    certEntity.setPrimaryFlag(toBoolean(cert.get("primary")));
                    validateBinary(certEntity.getValue(), "x509Certificates.value");
                    user.getX509Certificates().add(certEntity);
                }
            }
        }

        // Enterprise extension
        Map<String, Object> enterprise = (Map<String, Object>) input.get(ENTERPRISE_SCHEMA);
        if (enterprise != null) {
            user.setEnterpriseEmployeeNumber((String) enterprise.get("employeeNumber"));
            user.setEnterpriseCostCenter((String) enterprise.get("costCenter"));
            user.setEnterpriseOrganization((String) enterprise.get("organization"));
            user.setEnterpriseDivision((String) enterprise.get("division"));
            user.setEnterpriseDepartment((String) enterprise.get("department"));
            if (enterprise.containsKey("manager")) {
                Object mgrObj = enterprise.get("manager");
                if (mgrObj instanceof Map) {
                    Map<String, Object> mgr = (Map<String, Object>) mgrObj;
                    user.setEnterpriseManagerValue((String) mgr.get("value"));
                    user.setEnterpriseManagerRef((String) mgr.get("$ref"));
                    user.setEnterpriseManagerDisplay((String) mgr.get("displayName"));
                } else if (mgrObj instanceof String) {
                    user.setEnterpriseManagerValue((String) mgrObj);
                }
            }
        }
    }

    /**
     * Clear all mutable attributes for PUT (full replacement).
     */
    public static void clearMutableAttributes(ScimUser user) {
        user.setExternalId(null);
        user.setNameFormatted(null);
        user.setNameFamilyName(null);
        user.setNameGivenName(null);
        user.setNameMiddleName(null);
        user.setNameHonorificPrefix(null);
        user.setNameHonorificSuffix(null);
        user.setDisplayName(null);
        user.setNickName(null);
        user.setProfileUrl(null);
        user.setTitle(null);
        user.setUserType(null);
        user.setPreferredLanguage(null);
        user.setLocale(null);
        user.setTimezone(null);
        user.setActive(true);
        user.setPassword(null);
        user.getEmails().clear();
        user.getPhoneNumbers().clear();
        user.getAddresses().clear();
        user.getIms().clear();
        user.getPhotos().clear();
        user.getEntitlements().clear();
        user.getRoles().clear();
        user.getX509Certificates().clear();
        user.setEnterpriseEmployeeNumber(null);
        user.setEnterpriseCostCenter(null);
        user.setEnterpriseOrganization(null);
        user.setEnterpriseDivision(null);
        user.setEnterpriseDepartment(null);
        user.setEnterpriseManagerValue(null);
        user.setEnterpriseManagerRef(null);
        user.setEnterpriseManagerDisplay(null);
    }

    // ── Helper methods ─────────────────────────────────────────

    private static boolean hasEnterpriseData(ScimUser user) {
        return user.getEnterpriseEmployeeNumber() != null ||
               user.getEnterpriseCostCenter() != null ||
               user.getEnterpriseOrganization() != null ||
               user.getEnterpriseDivision() != null ||
               user.getEnterpriseDepartment() != null ||
               user.getEnterpriseManagerValue() != null;
    }

    private static Map<String, Object> buildNameMap(ScimUser user) {
        Map<String, Object> name = new LinkedHashMap<>();
        if (user.getNameFormatted() != null) name.put("formatted", user.getNameFormatted());
        if (user.getNameFamilyName() != null) name.put("familyName", user.getNameFamilyName());
        if (user.getNameGivenName() != null) name.put("givenName", user.getNameGivenName());
        if (user.getNameMiddleName() != null) name.put("middleName", user.getNameMiddleName());
        if (user.getNameHonorificPrefix() != null) name.put("honorificPrefix", user.getNameHonorificPrefix());
        if (user.getNameHonorificSuffix() != null) name.put("honorificSuffix", user.getNameHonorificSuffix());
        return name;
    }

    private static Map<String, Object> emailToMap(ScimUserEmail e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", e.getValue());
        if (e.getType() != null) m.put("type", e.getType());
        if (e.getDisplay() != null) m.put("display", e.getDisplay());
        m.put("primary", e.isPrimaryFlag());
        return m;
    }

    private static Map<String, Object> phoneToMap(ScimUserPhoneNumber p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", p.getValue());
        if (p.getType() != null) m.put("type", p.getType());
        if (p.getDisplay() != null) m.put("display", p.getDisplay());
        m.put("primary", p.isPrimaryFlag());
        return m;
    }

    private static Map<String, Object> imToMap(ScimUserIm i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", i.getValue());
        if (i.getType() != null) m.put("type", i.getType());
        if (i.getDisplay() != null) m.put("display", i.getDisplay());
        m.put("primary", i.isPrimaryFlag());
        return m;
    }

    private static Map<String, Object> photoToMap(ScimUserPhoto p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", p.getValue());
        if (p.getType() != null) m.put("type", p.getType());
        if (p.getDisplay() != null) m.put("display", p.getDisplay());
        m.put("primary", p.isPrimaryFlag());
        return m;
    }

    private static Map<String, Object> addressToMap(ScimUserAddress a) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (a.getFormatted() != null) m.put("formatted", a.getFormatted());
        if (a.getStreetAddress() != null) m.put("streetAddress", a.getStreetAddress());
        if (a.getLocality() != null) m.put("locality", a.getLocality());
        if (a.getRegion() != null) m.put("region", a.getRegion());
        if (a.getPostalCode() != null) m.put("postalCode", a.getPostalCode());
        if (a.getCountry() != null) m.put("country", a.getCountry());
        if (a.getType() != null) m.put("type", a.getType());
        m.put("primary", a.isPrimaryFlag());
        return m;
    }

    private static Map<String, Object> entitlementToMap(ScimUserEntitlement e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", e.getValue());
        if (e.getType() != null) m.put("type", e.getType());
        if (e.getDisplay() != null) m.put("display", e.getDisplay());
        m.put("primary", e.isPrimaryFlag());
        return m;
    }

    private static Map<String, Object> roleToMap(ScimUserRole r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", r.getValue());
        if (r.getType() != null) m.put("type", r.getType());
        if (r.getDisplay() != null) m.put("display", r.getDisplay());
        m.put("primary", r.isPrimaryFlag());
        return m;
    }

    private static Map<String, Object> certToMap(ScimUserX509Certificate c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", c.getValue());
        if (c.getType() != null) m.put("type", c.getType());
        if (c.getDisplay() != null) m.put("display", c.getDisplay());
        m.put("primary", c.isPrimaryFlag());
        return m;
    }

    private static String normalizeCanonical(String value, Set<String> allowed, String fieldName) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new ScimException(400, "invalidValue", "Invalid " + fieldName + ": " + value);
        }
        return normalized;
    }

    private static void validateBinary(String value, String fieldName) {
        if (value == null || value.isBlank()) return;
        try {
            java.util.Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new ScimException(400, "invalidValue", fieldName + " must be base64-encoded");
        }
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return false;
    }
}
