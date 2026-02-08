package com.scimplayground.server.scim.patch;

import com.scimplayground.server.model.*;
import com.scimplayground.server.scim.error.ScimException;
import com.scimplayground.server.scim.mapper.ScimUserMapper;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SCIM PATCH operation engine per RFC 7644 §3.5.2.
 * Supports add, replace, remove operations with path filters.
 */
public class ScimPatchEngine {

    private static final String ENTERPRISE_PREFIX = "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User";
    // Pattern: attrName[filterExpr].subAttr
    private static final Pattern FILTERED_PATH = Pattern.compile("^(\\w+)\\[(.+)](?:\\.(\\w+))?$");

    // Read-only attributes that cannot be modified via PATCH
    private static final Set<String> READ_ONLY_ATTRS = Set.of(
            "id", "meta", "meta.created", "meta.lastModified", "meta.location",
            "meta.resourceType", "meta.version", "groups"
    );

        private static final Set<String> EMAIL_TYPES = Set.of("work", "home", "other");
        private static final Set<String> PHONE_TYPES = Set.of("work", "home", "mobile", "fax", "pager", "other");
        private static final Set<String> IM_TYPES = Set.of("aim", "gtalk", "icq", "xmpp", "skype", "qq", "msn", "yahoo");
        private static final Set<String> PHOTO_TYPES = Set.of("photo", "thumbnail");
        private static final Set<String> ADDRESS_TYPES = Set.of("work", "home", "other");

    public static void applyPatchOperations(ScimUser user, List<Map<String, Object>> operations) {
        if (operations == null || operations.isEmpty()) {
            throw new ScimException(400, "invalidSyntax", "PATCH request must contain at least one operation");
        }

        for (Map<String, Object> op : operations) {
            String opType = ((String) op.get("op")).toLowerCase();
            String path = (String) op.get("path");
            Object value = op.get("value");

            // Validate read-only
            if (path != null && READ_ONLY_ATTRS.contains(path)) {
                throw new ScimException(400, "mutability", "Attribute '" + path + "' is readOnly and cannot be modified");
            }

            switch (opType) {
                case "add" -> applyAdd(user, path, value);
                case "replace" -> applyReplace(user, path, value);
                case "remove" -> applyRemove(user, path);
                default -> throw new ScimException(400, "invalidSyntax", "Unknown PATCH operation: " + opType);
            }
        }
    }

    // ── ADD ─────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void applyAdd(ScimUser user, String path, Object value) {
        if (path == null || path.isEmpty()) {
            // No path → merge the value as a map into the resource
            if (value instanceof Map) {
                applyValueMap(user, (Map<String, Object>) value);
            } else {
                throw new ScimException(400, "invalidSyntax", "Add without path requires a map value");
            }
            return;
        }

        // Check for filtered path on multi-valued attributes
        Matcher m = FILTERED_PATH.matcher(path);
        if (m.matches()) {
            applyFilteredAdd(user, m.group(1), m.group(2), m.group(3), value);
            return;
        }

        // Handle enterprise extension paths
        if (path.startsWith(ENTERPRISE_PREFIX + ":")) {
            String entAttr = path.substring(ENTERPRISE_PREFIX.length() + 1);
            setEnterpriseAttribute(user, entAttr, value);
            return;
        }

        // Handle sub-attribute paths (name.givenName)
        if (path.contains(".")) {
            setSubAttribute(user, path, value);
            return;
        }

        // Multi-valued attributes: add appends
        if (isMultiValuedAttribute(path)) {
            addToMultiValued(user, path, value);
            return;
        }

        // Single-valued: add = replace
        setSingleAttribute(user, path, value);
    }

    // ── REPLACE ─────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static void applyReplace(ScimUser user, String path, Object value) {
        if (path == null || path.isEmpty()) {
            // No path → merge
            if (value instanceof Map) {
                applyValueMap(user, (Map<String, Object>) value);
            } else {
                throw new ScimException(400, "invalidSyntax", "Replace without path requires a map value");
            }
            return;
        }

        // Filtered path
        Matcher m = FILTERED_PATH.matcher(path);
        if (m.matches()) {
            applyFilteredReplace(user, m.group(1), m.group(2), m.group(3), value);
            return;
        }

        // Enterprise extension
        if (path.startsWith(ENTERPRISE_PREFIX + ":")) {
            String entAttr = path.substring(ENTERPRISE_PREFIX.length() + 1);
            setEnterpriseAttribute(user, entAttr, value);
            return;
        }

        // Sub-attribute
        if (path.contains(".")) {
            setSubAttribute(user, path, value);
            return;
        }

        // Multi-valued: replace replaces the entire array
        if (isMultiValuedAttribute(path) && value instanceof List) {
            replaceMultiValued(user, path, value);
            return;
        }

        setSingleAttribute(user, path, value);
    }

    // ── REMOVE ──────────────────────────────────────────────

    private static void applyRemove(ScimUser user, String path) {
        if (path == null || path.isEmpty()) {
            throw new ScimException(400, "noTarget", "Remove operation requires a path");
        }

        // Filtered path
        Matcher m = FILTERED_PATH.matcher(path);
        if (m.matches()) {
            applyFilteredRemove(user, m.group(1), m.group(2));
            return;
        }

        // Enterprise extension
        if (path.startsWith(ENTERPRISE_PREFIX + ":")) {
            String entAttr = path.substring(ENTERPRISE_PREFIX.length() + 1);
            setEnterpriseAttribute(user, entAttr, null);
            return;
        }

        // Sub-attribute
        if (path.contains(".")) {
            setSubAttribute(user, path, null);
            return;
        }

        // Full attribute removal
        clearAttribute(user, path);
    }

    // ── FILTERED OPERATIONS ─────────────────────────────────

    private static void applyFilteredAdd(ScimUser user, String attr, String filter, String subAttr, Object value) {
        // Find matching items
        if ("emails".equals(attr)) {
            applyFilteredOnCollection(user.getEmails(), filter,
                    email -> matchesFilter(email, filter),
                    email -> setEmailSubAttribute(email, subAttr, value));
        } else if ("phoneNumbers".equals(attr)) {
            applyFilteredOnCollection(user.getPhoneNumbers(), filter,
                    phone -> matchesPhoneFilter(phone, filter),
                    phone -> setPhoneSubAttribute(phone, subAttr, value));
        } else if ("addresses".equals(attr)) {
            applyFilteredOnCollection(user.getAddresses(), filter,
                    addr -> matchesAddressFilter(addr, filter),
                    addr -> setAddressSubAttribute(addr, subAttr, value));
        } else if ("ims".equals(attr)) {
            applyFilteredOnCollection(user.getIms(), filter,
                    im -> matchesImFilter(im, filter),
                    im -> setImSubAttribute(im, subAttr, value));
        } else if ("photos".equals(attr)) {
            applyFilteredOnCollection(user.getPhotos(), filter,
                    photo -> matchesPhotoFilter(photo, filter),
                    photo -> setPhotoSubAttribute(photo, subAttr, value));
        } else if ("roles".equals(attr)) {
            applyFilteredOnCollection(user.getRoles(), filter,
                    role -> matchesRoleFilter(role, filter),
                    role -> setRoleSubAttribute(role, subAttr, value));
        } else if ("entitlements".equals(attr)) {
            applyFilteredOnCollection(user.getEntitlements(), filter,
                    ent -> matchesEntitlementFilter(ent, filter),
                    ent -> setEntitlementSubAttribute(ent, subAttr, value));
        } else if ("x509Certificates".equals(attr)) {
            applyFilteredOnCollection(user.getX509Certificates(), filter,
                    cert -> matchesCertFilter(cert, filter),
                    cert -> setCertSubAttribute(cert, subAttr, value));
        } else {
            throw new ScimException(400, "noTarget", "Filtered path not supported for attribute: " + attr);
        }
    }

    private static void applyFilteredReplace(ScimUser user, String attr, String filter,
                                              String subAttr, Object value) {
        if ("emails".equals(attr)) {
            boolean found = false;
            for (ScimUserEmail email : user.getEmails()) {
                if (matchesFilter(email, filter)) {
                    if (subAttr != null) {
                        setEmailSubAttribute(email, subAttr, value);
                    }
                    found = true;
                }
            }
            if (!found) {
                throw new ScimException(400, "noTarget", "No emails match filter: " + filter);
            }
        } else if ("phoneNumbers".equals(attr)) {
            boolean found = false;
            for (ScimUserPhoneNumber phone : user.getPhoneNumbers()) {
                if (matchesPhoneFilter(phone, filter)) {
                    if (subAttr != null) {
                        setPhoneSubAttribute(phone, subAttr, value);
                    }
                    found = true;
                }
            }
            if (!found) {
                throw new ScimException(400, "noTarget", "No phoneNumbers match filter: " + filter);
            }
        } else if ("addresses".equals(attr)) {
            boolean found = false;
            for (ScimUserAddress addr : user.getAddresses()) {
                if (matchesAddressFilter(addr, filter)) {
                    if (subAttr != null) {
                        setAddressSubAttribute(addr, subAttr, value);
                    }
                    found = true;
                }
            }
            if (!found) {
                throw new ScimException(400, "noTarget", "No addresses match filter: " + filter);
            }
        } else if ("ims".equals(attr)) {
            boolean found = false;
            for (ScimUserIm im : user.getIms()) {
                if (matchesImFilter(im, filter)) {
                    if (subAttr != null) {
                        setImSubAttribute(im, subAttr, value);
                    }
                    found = true;
                }
            }
            if (!found) {
                throw new ScimException(400, "noTarget", "No ims match filter: " + filter);
            }
        } else if ("photos".equals(attr)) {
            boolean found = false;
            for (ScimUserPhoto photo : user.getPhotos()) {
                if (matchesPhotoFilter(photo, filter)) {
                    if (subAttr != null) {
                        setPhotoSubAttribute(photo, subAttr, value);
                    }
                    found = true;
                }
            }
            if (!found) {
                throw new ScimException(400, "noTarget", "No photos match filter: " + filter);
            }
        } else if ("roles".equals(attr)) {
            boolean found = false;
            for (ScimUserRole role : user.getRoles()) {
                if (matchesRoleFilter(role, filter)) {
                    if (subAttr != null) {
                        setRoleSubAttribute(role, subAttr, value);
                    }
                    found = true;
                }
            }
            if (!found) {
                throw new ScimException(400, "noTarget", "No roles match filter: " + filter);
            }
        } else if ("entitlements".equals(attr)) {
            boolean found = false;
            for (ScimUserEntitlement ent : user.getEntitlements()) {
                if (matchesEntitlementFilter(ent, filter)) {
                    if (subAttr != null) {
                        setEntitlementSubAttribute(ent, subAttr, value);
                    }
                    found = true;
                }
            }
            if (!found) {
                throw new ScimException(400, "noTarget", "No entitlements match filter: " + filter);
            }
        } else if ("x509Certificates".equals(attr)) {
            boolean found = false;
            for (ScimUserX509Certificate cert : user.getX509Certificates()) {
                if (matchesCertFilter(cert, filter)) {
                    if (subAttr != null) {
                        setCertSubAttribute(cert, subAttr, value);
                    }
                    found = true;
                }
            }
            if (!found) {
                throw new ScimException(400, "noTarget", "No x509Certificates match filter: " + filter);
            }
        } else if ("members".equals(attr)) {
            // Group members handled elsewhere
            throw new ScimException(400, "noTarget", "Use group-specific PATCH for members");
        } else {
            throw new ScimException(400, "noTarget", "Filtered path not supported for attribute: " + attr);
        }
    }

    private static void applyFilteredRemove(ScimUser user, String attr, String filter) {
        if ("emails".equals(attr)) {
            user.getEmails().removeIf(email -> matchesFilter(email, filter));
        } else if ("phoneNumbers".equals(attr)) {
            user.getPhoneNumbers().removeIf(phone -> matchesPhoneFilter(phone, filter));
        } else if ("addresses".equals(attr)) {
            user.getAddresses().removeIf(addr -> matchesAddressFilter(addr, filter));
        } else if ("roles".equals(attr)) {
            user.getRoles().removeIf(role -> matchesRoleFilter(role, filter));
        } else if ("entitlements".equals(attr)) {
            user.getEntitlements().removeIf(ent -> matchesEntitlementFilter(ent, filter));
        } else if ("ims".equals(attr)) {
            user.getIms().removeIf(im -> matchesImFilter(im, filter));
        } else if ("photos".equals(attr)) {
            user.getPhotos().removeIf(photo -> matchesPhotoFilter(photo, filter));
        } else if ("x509Certificates".equals(attr)) {
            user.getX509Certificates().removeIf(cert -> matchesCertFilter(cert, filter));
        } else {
            throw new ScimException(400, "noTarget", "Filtered remove not supported for: " + attr);
        }
    }

    // ── FILTER MATCHING ─────────────────────────────────────

    private static boolean matchesFilter(ScimUserEmail email, String filter) {
        return matchesGenericFilter(filter,
                Map.of("value", email.getValue() != null ? email.getValue() : "",
                       "type", email.getType() != null ? email.getType() : "",
                       "primary", String.valueOf(email.isPrimaryFlag())));
    }

    private static boolean matchesPhoneFilter(ScimUserPhoneNumber phone, String filter) {
        return matchesGenericFilter(filter,
                Map.of("value", phone.getValue() != null ? phone.getValue() : "",
                       "type", phone.getType() != null ? phone.getType() : "",
                       "primary", String.valueOf(phone.isPrimaryFlag())));
    }

    private static boolean matchesAddressFilter(ScimUserAddress addr, String filter) {
        return matchesGenericFilter(filter,
                Map.of("type", addr.getType() != null ? addr.getType() : "",
                       "primary", String.valueOf(addr.isPrimaryFlag())));
    }

    private static boolean matchesRoleFilter(ScimUserRole role, String filter) {
        return matchesGenericFilter(filter,
              Map.of("value", role.getValue() != null ? role.getValue() : "",
                  "type", role.getType() != null ? role.getType() : "",
                  "primary", String.valueOf(role.isPrimaryFlag())));
    }

    private static boolean matchesEntitlementFilter(ScimUserEntitlement ent, String filter) {
        return matchesGenericFilter(filter,
              Map.of("value", ent.getValue() != null ? ent.getValue() : "",
                  "type", ent.getType() != null ? ent.getType() : "",
                  "primary", String.valueOf(ent.isPrimaryFlag())));
    }

    private static boolean matchesImFilter(ScimUserIm im, String filter) {
        return matchesGenericFilter(filter,
              Map.of("value", im.getValue() != null ? im.getValue() : "",
                  "type", im.getType() != null ? im.getType() : "",
                  "primary", String.valueOf(im.isPrimaryFlag())));
    }

    private static boolean matchesPhotoFilter(ScimUserPhoto photo, String filter) {
        return matchesGenericFilter(filter,
              Map.of("value", photo.getValue() != null ? photo.getValue() : "",
                  "type", photo.getType() != null ? photo.getType() : "",
                  "primary", String.valueOf(photo.isPrimaryFlag())));
    }

    private static boolean matchesCertFilter(ScimUserX509Certificate cert, String filter) {
        return matchesGenericFilter(filter,
              Map.of("value", cert.getValue() != null ? cert.getValue() : "",
                  "type", cert.getType() != null ? cert.getType() : "",
                  "primary", String.valueOf(cert.isPrimaryFlag())));
    }

    /**
     * Simple filter matching: supports "attr eq \"value\"" syntax.
     */
    private static boolean matchesGenericFilter(String filter, Map<String, String> attributes) {
        // Parse simple "attr eq \"value\"" filters
        Pattern p = Pattern.compile("(\\w+)\\s+eq\\s+(\"([^\"]+)\"|(true|false))", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(filter);
        if (m.find()) {
            String attr = m.group(1);
            String value = m.group(3) != null ? m.group(3) : m.group(4);
            String actual = attributes.get(attr);
            return value.equalsIgnoreCase(actual);
        }
        return false;
    }

    // ── ATTRIBUTE SETTERS ───────────────────────────────────

    private static void setSingleAttribute(ScimUser user, String attr, Object value) {
        switch (attr) {
            case "userName" -> user.setUserName(toString(value));
            case "externalId" -> user.setExternalId(toString(value));
            case "displayName" -> user.setDisplayName(toString(value));
            case "nickName" -> user.setNickName(toString(value));
            case "profileUrl" -> {
                String ref = toString(value);
                user.setProfileUrl(ref);
            }
            case "title" -> user.setTitle(toString(value));
            case "userType" -> user.setUserType(toString(value));
            case "preferredLanguage" -> user.setPreferredLanguage(toString(value));
            case "locale" -> user.setLocale(toString(value));
            case "timezone" -> user.setTimezone(toString(value));
            case "active" -> user.setActive(toBoolean(value));
            case "password" -> user.setPassword(toString(value));
            default -> throw new ScimException(400, "noTarget", "Unknown attribute: " + attr);
        }
    }

    private static void setSubAttribute(ScimUser user, String path, Object value) {
        String[] parts = path.split("\\.", 2);
        String parent = parts[0];
        String sub = parts[1];

        if ("name".equals(parent)) {
            switch (sub) {
                case "formatted" -> user.setNameFormatted(toString(value));
                case "familyName" -> user.setNameFamilyName(toString(value));
                case "givenName" -> user.setNameGivenName(toString(value));
                case "middleName" -> user.setNameMiddleName(toString(value));
                case "honorificPrefix" -> user.setNameHonorificPrefix(toString(value));
                case "honorificSuffix" -> user.setNameHonorificSuffix(toString(value));
                default -> throw new ScimException(400, "noTarget", "Unknown name sub-attribute: " + sub);
            }
        } else {
            throw new ScimException(400, "noTarget", "Unknown complex attribute: " + parent);
        }
    }

    private static void setEnterpriseAttribute(ScimUser user, String attr, Object value) {
        switch (attr) {
            case "employeeNumber" -> user.setEnterpriseEmployeeNumber(toString(value));
            case "costCenter" -> user.setEnterpriseCostCenter(toString(value));
            case "organization" -> user.setEnterpriseOrganization(toString(value));
            case "division" -> user.setEnterpriseDivision(toString(value));
            case "department" -> user.setEnterpriseDepartment(toString(value));
            case "manager" -> setEnterpriseManager(user, value);
            default -> throw new ScimException(400, "noTarget", "Unknown enterprise attribute: " + attr);
        }
    }

    private static void setEnterpriseManager(ScimUser user, Object value) {
        if (value == null) {
            user.setEnterpriseManagerValue(null);
            user.setEnterpriseManagerRef(null);
            user.setEnterpriseManagerDisplay(null);
            return;
        }

        if (value instanceof String) {
            String managerValue = ((String) value).trim();
            if (managerValue.isEmpty()) {
                user.setEnterpriseManagerValue(null);
                user.setEnterpriseManagerRef(null);
                user.setEnterpriseManagerDisplay(null);
            } else {
                user.setEnterpriseManagerValue(managerValue);
            }
            return;
        }

        if (value instanceof Map) {
            Map<String, Object> mgr = (Map<String, Object>) value;
            user.setEnterpriseManagerValue(toString(mgr.get("value")));
            String ref = toString(mgr.get("$ref"));
            if (ref != null) {
                validateReference(ref, "enterprise.manager.$ref");
            }
            user.setEnterpriseManagerRef(ref);
            user.setEnterpriseManagerDisplay(toString(mgr.get("displayName")));
            return;
        }

        throw new ScimException(400, "invalidValue", "Enterprise manager must be a string or object");
    }

    private static void applyValueMap(ScimUser user, Map<String, Object> valueMap) {
        Map<String, Object> normalized = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : valueMap.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            if (key.startsWith(ENTERPRISE_PREFIX + ":")) {
                String entAttr = key.substring(ENTERPRISE_PREFIX.length() + 1);
                setEnterpriseAttribute(user, entAttr, val);
                continue;
            }

            if (key.contains(".")) {
                setSubAttribute(user, key, val);
                continue;
            }

            normalized.put(key, val);
        }

        if (!normalized.isEmpty()) {
            ScimUserMapper.applyFromScimInput(user, normalized);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addToMultiValued(ScimUser user, String attr, Object value) {
        List<Map<String, Object>> items;
        if (value instanceof List) {
            items = (List<Map<String, Object>>) value;
        } else if (value instanceof Map) {
            items = List.of((Map<String, Object>) value);
        } else {
            throw new ScimException(400, "invalidSyntax", "Add to multi-valued requires array or object value");
        }

        switch (attr) {
            case "emails" -> {
                for (Map<String, Object> em : items) {
                    ScimUserEmail email = new ScimUserEmail();
                    email.setUser(user);
                    email.setValue((String) em.get("value"));
                    email.setType(normalizeCanonical((String) em.get("type"), EMAIL_TYPES, "emails.type"));
                    email.setDisplay((String) em.get("display"));
                    email.setPrimaryFlag(toBoolean(em.get("primary")));
                    user.getEmails().add(email);
                }
            }
            case "phoneNumbers" -> {
                for (Map<String, Object> ph : items) {
                    ScimUserPhoneNumber phone = new ScimUserPhoneNumber();
                    phone.setUser(user);
                    phone.setValue((String) ph.get("value"));
                    phone.setType(normalizeCanonical((String) ph.get("type"), PHONE_TYPES, "phoneNumbers.type"));
                    phone.setDisplay((String) ph.get("display"));
                    phone.setPrimaryFlag(toBoolean(ph.get("primary")));
                    user.getPhoneNumbers().add(phone);
                }
            }
            case "addresses" -> {
                for (Map<String, Object> addr : items) {
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
            case "ims" -> {
                for (Map<String, Object> im : items) {
                    ScimUserIm imEntity = new ScimUserIm();
                    imEntity.setUser(user);
                    imEntity.setValue((String) im.get("value"));
                    imEntity.setType(normalizeCanonical((String) im.get("type"), IM_TYPES, "ims.type"));
                    imEntity.setDisplay((String) im.get("display"));
                    imEntity.setPrimaryFlag(toBoolean(im.get("primary")));
                    user.getIms().add(imEntity);
                }
            }
            case "photos" -> {
                for (Map<String, Object> ph : items) {
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
            case "roles" -> {
                for (Map<String, Object> r : items) {
                    ScimUserRole role = new ScimUserRole();
                    role.setUser(user);
                    role.setValue((String) r.get("value"));
                    role.setType((String) r.get("type"));
                    role.setDisplay((String) r.get("display"));
                    role.setPrimaryFlag(toBoolean(r.get("primary")));
                    user.getRoles().add(role);
                }
            }
            case "entitlements" -> {
                for (Map<String, Object> e : items) {
                    ScimUserEntitlement ent = new ScimUserEntitlement();
                    ent.setUser(user);
                    ent.setValue((String) e.get("value"));
                    ent.setType((String) e.get("type"));
                    ent.setDisplay((String) e.get("display"));
                    ent.setPrimaryFlag(toBoolean(e.get("primary")));
                    user.getEntitlements().add(ent);
                }
            }
            case "x509Certificates" -> {
                for (Map<String, Object> c : items) {
                    ScimUserX509Certificate cert = new ScimUserX509Certificate();
                    cert.setUser(user);
                    cert.setValue((String) c.get("value"));
                    cert.setType((String) c.get("type"));
                    cert.setDisplay((String) c.get("display"));
                    cert.setPrimaryFlag(toBoolean(c.get("primary")));
                    validateBinary(cert.getValue(), "x509Certificates.value");
                    user.getX509Certificates().add(cert);
                }
            }
            default -> throw new ScimException(400, "noTarget", "Cannot add to attribute: " + attr);
        }
    }

    private static void replaceMultiValued(ScimUser user, String attr, Object value) {
        clearAttribute(user, attr);
        addToMultiValued(user, attr, value);
    }

    private static void clearAttribute(ScimUser user, String attr) {
        switch (attr) {
            case "externalId" -> user.setExternalId(null);
            case "displayName" -> user.setDisplayName(null);
            case "nickName" -> user.setNickName(null);
            case "profileUrl" -> user.setProfileUrl(null);
            case "title" -> user.setTitle(null);
            case "userType" -> user.setUserType(null);
            case "preferredLanguage" -> user.setPreferredLanguage(null);
            case "locale" -> user.setLocale(null);
            case "timezone" -> user.setTimezone(null);
            case "name" -> {
                user.setNameFormatted(null);
                user.setNameFamilyName(null);
                user.setNameGivenName(null);
                user.setNameMiddleName(null);
                user.setNameHonorificPrefix(null);
                user.setNameHonorificSuffix(null);
            }
            case "emails" -> user.getEmails().clear();
            case "phoneNumbers" -> user.getPhoneNumbers().clear();
            case "addresses" -> user.getAddresses().clear();
            case "ims" -> user.getIms().clear();
            case "photos" -> user.getPhotos().clear();
            case "entitlements" -> user.getEntitlements().clear();
            case "roles" -> user.getRoles().clear();
            case "x509Certificates" -> user.getX509Certificates().clear();
            default -> throw new ScimException(400, "noTarget", "Cannot remove attribute: " + attr);
        }
    }

    private static void setEmailSubAttribute(ScimUserEmail email, String subAttr, Object value) {
        if (subAttr == null) return;
        switch (subAttr) {
            case "value" -> email.setValue(toString(value));
            case "type" -> email.setType(normalizeCanonical(toString(value), EMAIL_TYPES, "emails.type"));
            case "display" -> email.setDisplay(toString(value));
            case "primary" -> email.setPrimaryFlag(toBoolean(value));
            default -> throw new ScimException(400, "noTarget", "Unknown email sub-attribute: " + subAttr);
        }
    }

    private static void setPhoneSubAttribute(ScimUserPhoneNumber phone, String subAttr, Object value) {
        if (subAttr == null) return;
        switch (subAttr) {
            case "value" -> phone.setValue(toString(value));
            case "type" -> phone.setType(normalizeCanonical(toString(value), PHONE_TYPES, "phoneNumbers.type"));
            case "display" -> phone.setDisplay(toString(value));
            case "primary" -> phone.setPrimaryFlag(toBoolean(value));
            default -> throw new ScimException(400, "noTarget", "Unknown phone sub-attribute: " + subAttr);
        }
    }

    private static void setAddressSubAttribute(ScimUserAddress address, String subAttr, Object value) {
        if (subAttr == null) return;
        switch (subAttr) {
            case "formatted" -> address.setFormatted(toString(value));
            case "streetAddress" -> address.setStreetAddress(toString(value));
            case "locality" -> address.setLocality(toString(value));
            case "region" -> address.setRegion(toString(value));
            case "postalCode" -> address.setPostalCode(toString(value));
            case "country" -> address.setCountry(toString(value));
            case "type" -> address.setType(normalizeCanonical(toString(value), ADDRESS_TYPES, "addresses.type"));
            case "primary" -> address.setPrimaryFlag(toBoolean(value));
            default -> throw new ScimException(400, "noTarget", "Unknown address sub-attribute: " + subAttr);
        }
    }

    private static void setImSubAttribute(ScimUserIm im, String subAttr, Object value) {
        if (subAttr == null) return;
        switch (subAttr) {
            case "value" -> im.setValue(toString(value));
            case "type" -> im.setType(normalizeCanonical(toString(value), IM_TYPES, "ims.type"));
            case "display" -> im.setDisplay(toString(value));
            case "primary" -> im.setPrimaryFlag(toBoolean(value));
            default -> throw new ScimException(400, "noTarget", "Unknown ims sub-attribute: " + subAttr);
        }
    }

    private static void setPhotoSubAttribute(ScimUserPhoto photo, String subAttr, Object value) {
        if (subAttr == null) return;
        switch (subAttr) {
            case "value" -> {
                String ref = toString(value);
                photo.setValue(ref);
            }
            case "type" -> photo.setType(normalizeCanonical(toString(value), PHOTO_TYPES, "photos.type"));
            case "display" -> photo.setDisplay(toString(value));
            case "primary" -> photo.setPrimaryFlag(toBoolean(value));
            default -> throw new ScimException(400, "noTarget", "Unknown photos sub-attribute: " + subAttr);
        }
    }

    private static void setRoleSubAttribute(ScimUserRole role, String subAttr, Object value) {
        if (subAttr == null) return;
        switch (subAttr) {
            case "value" -> role.setValue(toString(value));
            case "type" -> role.setType(toString(value));
            case "display" -> role.setDisplay(toString(value));
            case "primary" -> role.setPrimaryFlag(toBoolean(value));
            default -> throw new ScimException(400, "noTarget", "Unknown role sub-attribute: " + subAttr);
        }
    }

    private static void setEntitlementSubAttribute(ScimUserEntitlement ent, String subAttr, Object value) {
        if (subAttr == null) return;
        switch (subAttr) {
            case "value" -> ent.setValue(toString(value));
            case "type" -> ent.setType(toString(value));
            case "display" -> ent.setDisplay(toString(value));
            case "primary" -> ent.setPrimaryFlag(toBoolean(value));
            default -> throw new ScimException(400, "noTarget", "Unknown entitlements sub-attribute: " + subAttr);
        }
    }

    private static void setCertSubAttribute(ScimUserX509Certificate cert, String subAttr, Object value) {
        if (subAttr == null) return;
        switch (subAttr) {
            case "value" -> {
                String binary = toString(value);
                validateBinary(binary, "x509Certificates.value");
                cert.setValue(binary);
            }
            case "type" -> cert.setType(toString(value));
            case "display" -> cert.setDisplay(toString(value));
            case "primary" -> cert.setPrimaryFlag(toBoolean(value));
            default -> throw new ScimException(400, "noTarget", "Unknown x509Certificates sub-attribute: " + subAttr);
        }
    }

    private static <T> void applyFilteredOnCollection(List<T> collection, String filter,
                                                        Function<T, Boolean> matcher,
                                                        java.util.function.Consumer<T> action) {
        boolean found = false;
        for (T item : collection) {
            if (matcher.apply(item)) {
                action.accept(item);
                found = true;
            }
        }
        if (!found) {
            throw new ScimException(400, "noTarget", "No items match filter: " + filter);
        }
    }

    // ── MULTI-VALUED CHECK ──────────────────────────────────

    private static boolean isMultiValuedAttribute(String attr) {
        return Set.of("emails", "phoneNumbers", "addresses", "ims", "photos",
                "entitlements", "roles", "x509Certificates").contains(attr);
    }

    // ── TYPE HELPERS ────────────────────────────────────────

    private static String normalizeCanonical(String value, Set<String> allowed, String fieldName) {
        if (value == null) return null;
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new ScimException(400, "invalidValue", "Invalid " + fieldName + ": " + value);
        }
        return normalized;
    }

    private static void validateReference(String value, String fieldName) {
        if (value == null || value.isBlank()) return;
        try {
            java.net.URI uri = new java.net.URI(value);
            if (!uri.isAbsolute()) {
                throw new ScimException(400, "invalidValue", fieldName + " must be an absolute URI");
            }
        } catch (java.net.URISyntaxException e) {
            throw new ScimException(400, "invalidValue", fieldName + " must be a valid URI");
        }
    }

    private static void validateBinary(String value, String fieldName) {
        if (value == null || value.isBlank()) return;
        try {
            java.util.Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new ScimException(400, "invalidValue", fieldName + " must be base64-encoded");
        }
    }

    private static String toString(Object value) {
        return value != null ? value.toString() : null;
    }

    private static boolean toBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return false;
    }
}
