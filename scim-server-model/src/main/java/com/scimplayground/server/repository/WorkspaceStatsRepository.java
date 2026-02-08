package com.scimplayground.server.repository;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class WorkspaceStatsRepository {

    private static final String WORKSPACE_STATS_SQL = """
            WITH counts AS (
                SELECT
                    (SELECT COUNT(*) FROM scim_users u WHERE u.workspace_id = :workspaceId) AS workspace_users,
                    (SELECT COUNT(*) FROM scim_users) AS total_users,
                    (SELECT COUNT(*) FROM scim_groups g WHERE g.workspace_id = :workspaceId) AS workspace_groups,
                    (SELECT COUNT(*) FROM scim_groups) AS total_groups,
                    (SELECT COUNT(*) FROM workspace_tokens t WHERE t.workspace_id = :workspaceId) AS workspace_tokens,
                    (SELECT COUNT(*) FROM workspace_tokens) AS total_tokens,
                    (SELECT COUNT(*) FROM scim_request_logs l WHERE l.workspace_id = :workspaceId) AS workspace_logs,
                    (SELECT COUNT(*) FROM scim_request_logs) AS total_logs,
                    (SELECT COUNT(*) FROM scim_user_emails e JOIN scim_users u ON u.id = e.user_id WHERE u.workspace_id = :workspaceId) AS workspace_emails,
                    (SELECT COUNT(*) FROM scim_user_emails) AS total_emails,
                    (SELECT COUNT(*) FROM scim_user_phone_numbers p JOIN scim_users u ON u.id = p.user_id WHERE u.workspace_id = :workspaceId) AS workspace_phone_numbers,
                    (SELECT COUNT(*) FROM scim_user_phone_numbers) AS total_phone_numbers,
                    (SELECT COUNT(*) FROM scim_user_addresses a JOIN scim_users u ON u.id = a.user_id WHERE u.workspace_id = :workspaceId) AS workspace_addresses,
                    (SELECT COUNT(*) FROM scim_user_addresses) AS total_addresses,
                    (SELECT COUNT(*) FROM scim_user_entitlements e JOIN scim_users u ON u.id = e.user_id WHERE u.workspace_id = :workspaceId) AS workspace_entitlements,
                    (SELECT COUNT(*) FROM scim_user_entitlements) AS total_entitlements,
                    (SELECT COUNT(*) FROM scim_user_roles r JOIN scim_users u ON u.id = r.user_id WHERE u.workspace_id = :workspaceId) AS workspace_roles,
                    (SELECT COUNT(*) FROM scim_user_roles) AS total_roles,
                    (SELECT COUNT(*) FROM scim_user_ims i JOIN scim_users u ON u.id = i.user_id WHERE u.workspace_id = :workspaceId) AS workspace_ims,
                    (SELECT COUNT(*) FROM scim_user_ims) AS total_ims,
                    (SELECT COUNT(*) FROM scim_user_photos p JOIN scim_users u ON u.id = p.user_id WHERE u.workspace_id = :workspaceId) AS workspace_photos,
                    (SELECT COUNT(*) FROM scim_user_photos) AS total_photos,
                    (SELECT COUNT(*) FROM scim_user_x509_certificates c JOIN scim_users u ON u.id = c.user_id WHERE u.workspace_id = :workspaceId) AS workspace_x509_certificates,
                    (SELECT COUNT(*) FROM scim_user_x509_certificates) AS total_x509_certificates,
                    (SELECT COUNT(*) FROM scim_group_memberships m JOIN scim_groups g ON g.id = m.group_id WHERE g.workspace_id = :workspaceId) AS workspace_group_memberships,
                    (SELECT COUNT(*) FROM scim_group_memberships) AS total_group_memberships
            )
            SELECT
                workspace_users,
                workspace_groups,
                workspace_tokens,
                workspace_logs,
                workspace_emails,
                workspace_phone_numbers,
                workspace_addresses,
                workspace_entitlements,
                workspace_roles,
                workspace_ims,
                workspace_photos,
                workspace_x509_certificates,
                workspace_group_memberships,
                (
                    COALESCE(pg_total_relation_size('scim_users'::regclass)::numeric * workspace_users / NULLIF(total_users, 0), 0)
                    + COALESCE(pg_total_relation_size('scim_groups'::regclass)::numeric * workspace_groups / NULLIF(total_groups, 0), 0)
                    + COALESCE(pg_total_relation_size('workspace_tokens'::regclass)::numeric * workspace_tokens / NULLIF(total_tokens, 0), 0)
                    + COALESCE(pg_total_relation_size('scim_request_logs'::regclass)::numeric * workspace_logs / NULLIF(total_logs, 0), 0)
                    + COALESCE(pg_total_relation_size('scim_user_emails'::regclass)::numeric * workspace_emails / NULLIF(total_emails, 0), 0)
                    + COALESCE(pg_total_relation_size('scim_user_phone_numbers'::regclass)::numeric * workspace_phone_numbers / NULLIF(total_phone_numbers, 0), 0)
                    + COALESCE(pg_total_relation_size('scim_user_addresses'::regclass)::numeric * workspace_addresses / NULLIF(total_addresses, 0), 0)
                    + COALESCE(pg_total_relation_size('scim_user_entitlements'::regclass)::numeric * workspace_entitlements / NULLIF(total_entitlements, 0), 0)
                    + COALESCE(pg_total_relation_size('scim_user_roles'::regclass)::numeric * workspace_roles / NULLIF(total_roles, 0), 0)
                    + COALESCE(pg_total_relation_size('scim_user_ims'::regclass)::numeric * workspace_ims / NULLIF(total_ims, 0), 0)
                    + COALESCE(pg_total_relation_size('scim_user_photos'::regclass)::numeric * workspace_photos / NULLIF(total_photos, 0), 0)
                    + COALESCE(pg_total_relation_size('scim_user_x509_certificates'::regclass)::numeric * workspace_x509_certificates / NULLIF(total_x509_certificates, 0), 0)
                    + COALESCE(pg_total_relation_size('scim_group_memberships'::regclass)::numeric * workspace_group_memberships / NULLIF(total_group_memberships, 0), 0)
                )::bigint
            FROM counts
            """;

    private final EntityManager entityManager;

    public WorkspaceStatsRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public WorkspaceDataStats fetchWorkspaceDataStats(UUID workspaceId) {
        Object[] row = (Object[]) entityManager.createNativeQuery(WORKSPACE_STATS_SQL)
                .setParameter("workspaceId", workspaceId)
                .getSingleResult();

        return new WorkspaceDataStats(
                toLong(row[0]),
                toLong(row[1]),
                toLong(row[2]),
                toLong(row[3]),
                toLong(row[4]),
                toLong(row[5]),
                toLong(row[6]),
                toLong(row[7]),
                toLong(row[8]),
                toLong(row[9]),
                toLong(row[10]),
                toLong(row[11]),
                toLong(row[12]),
                toLong(row[13]));
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}