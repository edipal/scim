package de.palsoftware.scim.server.api.scim.filter;

import de.palsoftware.scim.server.common.model.ScimUser;
import de.palsoftware.scim.server.common.model.ScimGroup;
import de.palsoftware.scim.server.api.scim.error.ScimException;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked", "rawtypes"})
class ScimFilterParserTest {

    private final UUID workspaceId = UUID.randomUUID();
    private Root<ScimUser> root;
    private CriteriaQuery<?> query;
    private CriteriaBuilder cb;
    private Path path;

    @BeforeEach
    void setUp() {
        root = Mockito.mock(Root.class);
        query = Mockito.mock(CriteriaQuery.class);
        cb = Mockito.mock(CriteriaBuilder.class);
        path = Mockito.mock(Path.class);

        when(root.get(anyString())).thenReturn(path);
        when(path.get(anyString())).thenReturn(path);

        Predicate dummyPredicate = Mockito.mock(Predicate.class);
        when(cb.equal(any(), any())).thenReturn(dummyPredicate);
        when(cb.notEqual(any(), any())).thenReturn(dummyPredicate);
        when(cb.like(any(Expression.class), anyString())).thenReturn(dummyPredicate);
        when(cb.greaterThan(any(Expression.class), any(Comparable.class))).thenReturn(dummyPredicate);
        when(cb.greaterThanOrEqualTo(any(Expression.class), any(Comparable.class))).thenReturn(dummyPredicate);
        when(cb.lessThan(any(Expression.class), any(Comparable.class))).thenReturn(dummyPredicate);
        when(cb.lessThanOrEqualTo(any(Expression.class), any(Comparable.class))).thenReturn(dummyPredicate);
        when(cb.isNotNull(any())).thenReturn(dummyPredicate);
        when(cb.and(any(), any())).thenReturn(dummyPredicate);
        when(cb.or(any(), any())).thenReturn(dummyPredicate);
        when(cb.lower(any())).thenReturn((Expression) path);
    }

    @Test
    void testParseUserFilter_Empty() {
        Specification<ScimUser> spec = ScimFilterParser.parseUserFilter(null, workspaceId);
        assertNotNull(spec);
        spec.toPredicate(root, query, cb);
        verify(cb).equal(any(), eq(workspaceId));
    }

    @Test
    void testParseUserFilter_ComplexAndOrNot_Grouping() {
        String filter = "(userName eq \"test\" or name.familyName sw \"Smith\") and (meta.created gt \"2023-01-01T00:00:00Z\") and not (title pr)";
        Specification<ScimUser> spec = ScimFilterParser.parseUserFilter(filter, workspaceId);
        // By calling toPredicate, we fire all lambda branches
        assertNotNull(spec);
        spec.toPredicate(root, query, cb);
    }
    
    @Test
    void testOperators() {
        String[] ops = {"eq", "ne", "co", "sw", "ew", "gt", "ge", "lt", "le"};
        for (String op : ops) {
            Specification<ScimUser> spec = ScimFilterParser.parseUserFilter("userName " + op + " \"test\"", workspaceId);
            assertNotNull(spec);
            spec.toPredicate(root, query, cb);
        }
    }

    @Test
    void testUserAttributes() {
        String[] attrs = {
            "meta.created", "meta.lastModified", "name.familyName", "name.givenName", 
            "name.formatted", "name.middleName", "name.honorificPrefix", "name.honorificSuffix", 
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:employeeNumber", 
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:costCenter", 
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:organization", 
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:division", 
            "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:department", 
            "id", "userName", "externalId", "displayName", "nickName", "title", "userType", 
            "profileUrl", "preferredLanguage", "locale", "timezone", "active"
        };
        for (String attr : attrs) {
            Specification<ScimUser> spec = ScimFilterParser.parseUserFilter(attr + " pr", workspaceId);
            assertNotNull(spec);
            spec.toPredicate(root, query, cb);
        }
    }

    @Test
    void testGroupAttributes() {
        String[] attrs = {"meta.created", "meta.lastModified", "id", "displayName", "externalId"};
        for (String attr : attrs) {
            Specification<ScimGroup> spec = ScimFilterParser.parseGroupFilter(attr + " eq \"test\"", workspaceId);
            assertNotNull(spec);
            spec.toPredicate((Root) root, query, cb);
        }
    }

    @Test
    void testBooleanEquals() {
        Specification<ScimUser> spec = ScimFilterParser.parseUserFilter("active eq true", workspaceId);
        assertNotNull(spec);
        spec.toPredicate(root, query, cb);
    }

    @Test
    void testInvalid() {
        Specification<ScimUser> specOp = ScimFilterParser.parseUserFilter("userName unknownOp \"test\"", workspaceId);
        assertThrows(ScimException.class, () -> specOp.toPredicate(root, query, cb));

        assertThrows(ScimException.class, () -> ScimFilterParser.parseUserFilter("(userName eq \"test\"", workspaceId));
        
        // Ensure that exceptions inside toPredicate are triggered correctly
        Specification<ScimUser> specAttr = ScimFilterParser.parseUserFilter("meta.unknown pr", workspaceId);
        assertThrows(ScimException.class, () -> specAttr.toPredicate(root, query, cb));
        
        Specification<ScimUser> specAttr2 = ScimFilterParser.parseUserFilter("name.unknown pr", workspaceId);
        assertThrows(ScimException.class, () -> specAttr2.toPredicate(root, query, cb));
        
        Specification<ScimUser> specAttr3 = ScimFilterParser.parseUserFilter("urn:ietf:params:scim:schemas:extension:enterprise:2.0:User:unknown pr", workspaceId);
        assertThrows(ScimException.class, () -> specAttr3.toPredicate(root, query, cb));
        
        Specification<ScimUser> specAttr4 = ScimFilterParser.parseUserFilter("unknownAttr pr", workspaceId);
        assertThrows(ScimException.class, () -> specAttr4.toPredicate(root, query, cb));
    }

    @Test
    void testResolveSort() {
        assertEquals("userName", ScimFilterParser.resolveUserSortAttribute(null));
        assertEquals("userName", ScimFilterParser.resolveUserSortAttribute("userName"));
        assertEquals("nameFamilyName", ScimFilterParser.resolveUserSortAttribute("name.familyName"));
        assertEquals("nameGivenName", ScimFilterParser.resolveUserSortAttribute("name.givenName"));
        assertEquals("displayName", ScimFilterParser.resolveUserSortAttribute("displayName"));
        assertEquals("title", ScimFilterParser.resolveUserSortAttribute("title"));
        assertEquals("externalId", ScimFilterParser.resolveUserSortAttribute("externalId"));
        assertEquals("createdAt", ScimFilterParser.resolveUserSortAttribute("meta.created"));
        assertEquals("lastModified", ScimFilterParser.resolveUserSortAttribute("meta.lastModified"));
        assertEquals("id", ScimFilterParser.resolveUserSortAttribute("id"));

        assertEquals("displayName", ScimFilterParser.resolveGroupSortAttribute(null));
        assertEquals("displayName", ScimFilterParser.resolveGroupSortAttribute("displayName"));
        assertEquals("externalId", ScimFilterParser.resolveGroupSortAttribute("externalId"));
        assertEquals("createdAt", ScimFilterParser.resolveGroupSortAttribute("meta.created"));
        assertEquals("lastModified", ScimFilterParser.resolveGroupSortAttribute("meta.lastModified"));
        assertEquals("id", ScimFilterParser.resolveGroupSortAttribute("id"));
    }
}
