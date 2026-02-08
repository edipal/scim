package com.scimplayground.server.scim.filter;

import com.scimplayground.server.model.ScimUser;
import com.scimplayground.server.model.ScimGroup;
import com.scimplayground.server.scim.error.ScimException;
import jakarta.persistence.criteria.*;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses SCIM filter expressions (RFC 7644 §3.4.2.2) into JPA Specifications.
 * Supports: eq, ne, co, sw, ew, pr, gt, ge, lt, le, and, or, not, grouping ().
 */
public class ScimFilterParser {

    // Token patterns
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "\\(|\\)|" +                           // Grouping
            "\"(?:[^\"\\\\]|\\\\.)*\"|" +           // Quoted string
            "\\b(?:true|false)\\b|" +               // Boolean
            "\\b(?:and|or|not)\\b|" +               // Logical operators
            "\\b(?:eq|ne|co|sw|ew|pr|gt|ge|lt|le)\\b|" + // Comparison operators
            "[\\w.:]+",                              // Attribute paths (including URN prefixes)
            Pattern.CASE_INSENSITIVE
    );

    // ── USER FILTER ─────────────────────────────────────

    public static Specification<ScimUser> parseUserFilter(String filter, UUID workspaceId) {
        if (filter == null || filter.isBlank()) {
            return (root, query, cb) -> cb.equal(root.get("workspace").get("id"), workspaceId);
        }
        try {
            List<String> tokens = tokenize(filter);
            int[] pos = {0};
            Specification<ScimUser> spec = parseOrExpression(tokens, pos, true);
            Specification<ScimUser> wsSpec = (root, query, cb) -> cb.equal(root.get("workspace").get("id"), workspaceId);
            return wsSpec.and(spec);
        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            throw new ScimException(400, "invalidFilter", "Invalid filter expression: " + e.getMessage());
        }
    }

    // ── GROUP FILTER ─────────────────────────────────────

    public static Specification<ScimGroup> parseGroupFilter(String filter, UUID workspaceId) {
        if (filter == null || filter.isBlank()) {
            return (root, query, cb) -> cb.equal(root.get("workspace").get("id"), workspaceId);
        }
        try {
            List<String> tokens = tokenize(filter);
            int[] pos = {0};
            Specification<ScimGroup> spec = parseOrExpression(tokens, pos, false);
            Specification<ScimGroup> wsSpec = (root, query, cb) -> cb.equal(root.get("workspace").get("id"), workspaceId);
            return wsSpec.and(spec);
        } catch (ScimException e) {
            throw e;
        } catch (Exception e) {
            throw new ScimException(400, "invalidFilter", "Invalid filter expression: " + e.getMessage());
        }
    }

    // ── RECURSIVE DESCENT PARSER ─────────────────────────

    private static <T> Specification<T> parseOrExpression(List<String> tokens, int[] pos, boolean isUser) {
        Specification<T> left = parseAndExpression(tokens, pos, isUser);
        while (pos[0] < tokens.size() && "or".equalsIgnoreCase(tokens.get(pos[0]))) {
            pos[0]++;
            Specification<T> right = parseAndExpression(tokens, pos, isUser);
            left = left.or(right);
        }
        return left;
    }

    private static <T> Specification<T> parseAndExpression(List<String> tokens, int[] pos, boolean isUser) {
        Specification<T> left = parseNotExpression(tokens, pos, isUser);
        while (pos[0] < tokens.size() && "and".equalsIgnoreCase(tokens.get(pos[0]))) {
            pos[0]++;
            Specification<T> right = parseNotExpression(tokens, pos, isUser);
            left = left.and(right);
        }
        return left;
    }

    private static <T> Specification<T> parseNotExpression(List<String> tokens, int[] pos, boolean isUser) {
        if (pos[0] < tokens.size() && "not".equalsIgnoreCase(tokens.get(pos[0]))) {
            pos[0]++;
            Specification<T> inner = parseAtom(tokens, pos, isUser);
            return Specification.not(inner);
        }
        return parseAtom(tokens, pos, isUser);
    }

    private static <T> Specification<T> parseAtom(List<String> tokens, int[] pos, boolean isUser) {
        if (pos[0] >= tokens.size()) {
            throw new ScimException(400, "invalidFilter", "Unexpected end of filter expression");
        }

        String token = tokens.get(pos[0]);

        // Grouping
        if ("(".equals(token)) {
            pos[0]++;
            Specification<T> inner = parseOrExpression(tokens, pos, isUser);
            if (pos[0] >= tokens.size() || !")".equals(tokens.get(pos[0]))) {
                throw new ScimException(400, "invalidFilter", "Missing closing parenthesis");
            }
            pos[0]++;
            return inner;
        }

        // Attribute comparison or presence
        String attrPath = token;
        pos[0]++;

        if (pos[0] >= tokens.size()) {
            throw new ScimException(400, "invalidFilter", "Incomplete filter expression after: " + attrPath);
        }

        String operator = tokens.get(pos[0]).toLowerCase();
        pos[0]++;

        // Presence operator (no value)
        if ("pr".equals(operator)) {
            return (root, query, cb) -> {
                Path<?> path = resolveAttributePath(root, attrPath, isUser);
                return cb.isNotNull(path);
            };
        }

        // Comparison operators need a value
        if (pos[0] >= tokens.size()) {
            throw new ScimException(400, "invalidFilter", "Missing value for operator: " + operator);
        }

        String rawValue = tokens.get(pos[0]);
        pos[0]++;

        // Remove quotes from string values
        String value = rawValue;
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }

        final String finalValue = value;

        return (root, query, cb) -> {
            Path<?> path = resolveAttributePath(root, attrPath, isUser);
            return buildComparisonPredicate(cb, path, operator, finalValue, attrPath);
        };
    }

    @SuppressWarnings({"unchecked"})
    private static Predicate buildComparisonPredicate(CriteriaBuilder cb, Path<?> path,
                                                       String operator, String value, String attrPath) {
        // Determine if case-insensitive (most SCIM string attributes are case-insensitive)
        boolean caseInsensitive = isCaseInsensitiveAttribute(attrPath);

        switch (operator) {
            case "eq":
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    return cb.equal(path, Boolean.parseBoolean(value));
                }
                if (caseInsensitive) {
                    return cb.equal(cb.lower((Expression<String>) path), value.toLowerCase());
                }
                return cb.equal(path, value);

            case "ne":
                if (caseInsensitive) {
                    return cb.notEqual(cb.lower((Expression<String>) path), value.toLowerCase());
                }
                return cb.notEqual(path, value);

            case "co":
                if (caseInsensitive) {
                    return cb.like(cb.lower((Expression<String>) path), "%" + value.toLowerCase() + "%");
                }
                return cb.like((Expression<String>) path, "%" + value + "%");

            case "sw":
                if (caseInsensitive) {
                    return cb.like(cb.lower((Expression<String>) path), value.toLowerCase() + "%");
                }
                return cb.like((Expression<String>) path, value + "%");

            case "ew":
                if (caseInsensitive) {
                    return cb.like(cb.lower((Expression<String>) path), "%" + value.toLowerCase());
                }
                return cb.like((Expression<String>) path, "%" + value);

            case "gt":
                return buildOrderPredicate(cb, path, value, "gt");
            case "ge":
                return buildOrderPredicate(cb, path, value, "ge");
            case "lt":
                return buildOrderPredicate(cb, path, value, "lt");
            case "le":
                return buildOrderPredicate(cb, path, value, "le");

            default:
                throw new ScimException(400, "invalidFilter", "Unknown filter operator: " + operator);
        }
    }

    @SuppressWarnings({"unchecked"})
    private static Predicate buildOrderPredicate(CriteriaBuilder cb, Path<?> path, String value, String op) {
        // Try as datetime
        try {
            Instant instant = Instant.parse(value);
            Path<Instant> instantPath = (Path<Instant>) path;
            return switch (op) {
                case "gt" -> cb.greaterThan(instantPath, instant);
                case "ge" -> cb.greaterThanOrEqualTo(instantPath, instant);
                case "lt" -> cb.lessThan(instantPath, instant);
                case "le" -> cb.lessThanOrEqualTo(instantPath, instant);
                default -> throw new ScimException(400, "invalidFilter", "Unknown operator: " + op);
            };
        } catch (Exception e) {
            // Fall through to string comparison
        }

        Path<String> stringPath = (Path<String>) path;
        return switch (op) {
            case "gt" -> cb.greaterThan(stringPath, value);
            case "ge" -> cb.greaterThanOrEqualTo(stringPath, value);
            case "lt" -> cb.lessThan(stringPath, value);
            case "le" -> cb.lessThanOrEqualTo(stringPath, value);
            default -> throw new ScimException(400, "invalidFilter", "Unknown operator: " + op);
        };
    }

    // ── ATTRIBUTE PATH RESOLUTION ─────────────────────────

    private static Path<?> resolveAttributePath(Root<?> root, String attrPath, boolean isUser) {
        if (isUser) {
            return resolveUserAttributePath(root, attrPath);
        } else {
            return resolveGroupAttributePath(root, attrPath);
        }
    }

    private static Path<?> resolveUserAttributePath(Root<?> root, String attrPath) {
        // Handle meta sub-attributes
        if (attrPath.startsWith("meta.")) {
            String sub = attrPath.substring(5);
            return switch (sub) {
                case "created" -> root.get("createdAt");
                case "lastModified" -> root.get("lastModified");
                default -> throw new ScimException(400, "invalidFilter", "Unknown meta attribute: " + sub);
            };
        }

        // Handle name sub-attributes
        if (attrPath.startsWith("name.")) {
            String sub = attrPath.substring(5);
            return switch (sub) {
                case "familyName" -> root.get("nameFamilyName");
                case "givenName" -> root.get("nameGivenName");
                case "formatted" -> root.get("nameFormatted");
                case "middleName" -> root.get("nameMiddleName");
                case "honorificPrefix" -> root.get("nameHonorificPrefix");
                case "honorificSuffix" -> root.get("nameHonorificSuffix");
                default -> throw new ScimException(400, "invalidFilter", "Unknown name sub-attribute: " + sub);
            };
        }

        // Handle enterprise extension attributes
        if (attrPath.startsWith("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:")) {
            String sub = attrPath.substring("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:".length());
            return switch (sub) {
                case "employeeNumber" -> root.get("enterpriseEmployeeNumber");
                case "costCenter" -> root.get("enterpriseCostCenter");
                case "organization" -> root.get("enterpriseOrganization");
                case "division" -> root.get("enterpriseDivision");
                case "department" -> root.get("enterpriseDepartment");
                default -> throw new ScimException(400, "invalidFilter", "Unknown enterprise attribute: " + sub);
            };
        }

        // Direct attributes
        return switch (attrPath) {
            case "id" -> root.get("id");
            case "userName" -> root.get("userName");
            case "externalId" -> root.get("externalId");
            case "displayName" -> root.get("displayName");
            case "nickName" -> root.get("nickName");
            case "title" -> root.get("title");
            case "userType" -> root.get("userType");
            case "profileUrl" -> root.get("profileUrl");
            case "preferredLanguage" -> root.get("preferredLanguage");
            case "locale" -> root.get("locale");
            case "timezone" -> root.get("timezone");
            case "active" -> root.get("active");
            default -> throw new ScimException(400, "invalidFilter", "Unknown attribute: " + attrPath);
        };
    }

    private static Path<?> resolveGroupAttributePath(Root<?> root, String attrPath) {
        if (attrPath.startsWith("meta.")) {
            String sub = attrPath.substring(5);
            return switch (sub) {
                case "created" -> root.get("createdAt");
                case "lastModified" -> root.get("lastModified");
                default -> throw new ScimException(400, "invalidFilter", "Unknown meta attribute: " + sub);
            };
        }

        return switch (attrPath) {
            case "id" -> root.get("id");
            case "displayName" -> root.get("displayName");
            case "externalId" -> root.get("externalId");
            default -> throw new ScimException(400, "invalidFilter", "Unknown attribute: " + attrPath);
        };
    }

    // ── TOKENIZER ─────────────────────────────────────────

    private static List<String> tokenize(String filter) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(filter);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        if (tokens.isEmpty()) {
            throw new ScimException(400, "invalidFilter", "Empty filter expression");
        }
        return tokens;
    }

    // ── CASE SENSITIVITY ──────────────────────────────────

    private static boolean isCaseInsensitiveAttribute(String attrPath) {
        // Per RFC 7643, userName, emails.value, etc. are case-insensitive
        return Set.of("userName", "displayName", "nickName", "title", "userType",
                "preferredLanguage", "locale", "timezone", "externalId",
                "name.familyName", "name.givenName", "name.formatted",
                "name.middleName").contains(attrPath);
    }

    // ── SORTING ───────────────────────────────────────────

    public static String resolveUserSortAttribute(String sortBy) {
        if (sortBy == null) return "userName";
        return switch (sortBy) {
            case "userName" -> "userName";
            case "name.familyName" -> "nameFamilyName";
            case "name.givenName" -> "nameGivenName";
            case "displayName" -> "displayName";
            case "title" -> "title";
            case "externalId" -> "externalId";
            case "meta.created" -> "createdAt";
            case "meta.lastModified" -> "lastModified";
            case "id" -> "id";
            default -> "userName";
        };
    }

    public static String resolveGroupSortAttribute(String sortBy) {
        if (sortBy == null) return "displayName";
        return switch (sortBy) {
            case "displayName" -> "displayName";
            case "externalId" -> "externalId";
            case "meta.created" -> "createdAt";
            case "meta.lastModified" -> "lastModified";
            case "id" -> "id";
            default -> "displayName";
        };
    }
}
