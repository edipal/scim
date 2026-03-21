CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_by_username VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_workspaces_created_by_username ON workspaces (created_by_username);
CREATE INDEX idx_workspaces_updated_at ON workspaces (updated_at);
CREATE INDEX idx_workspaces_created_at ON workspaces (created_at DESC);

CREATE TABLE mgmt_users (
    id VARCHAR(500) PRIMARY KEY,
    email VARCHAR(500),
    last_login_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE scim_users (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    external_id VARCHAR(255),
    name_formatted VARCHAR(255),
    name_family_name VARCHAR(255),
    name_given_name VARCHAR(255),
    name_middle_name VARCHAR(255),
    name_honorific_prefix VARCHAR(255),
    name_honorific_suffix VARCHAR(255),
    display_name VARCHAR(255),
    nick_name VARCHAR(255),
    profile_url VARCHAR(255),
    title VARCHAR(255),
    user_type VARCHAR(255),
    preferred_language VARCHAR(255),
    locale VARCHAR(255),
    timezone VARCHAR(255),
    active BOOLEAN NOT NULL,
    password VARCHAR(255),
    enterprise_employee_number VARCHAR(255),
    enterprise_cost_center VARCHAR(255),
    enterprise_organization VARCHAR(255),
    enterprise_division VARCHAR(255),
    enterprise_department VARCHAR(255),
    enterprise_manager_value VARCHAR(255),
    enterprise_manager_ref VARCHAR(255),
    enterprise_manager_display VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT,
    CONSTRAINT fk_scim_users_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT uk_scim_users_workspace_user_name UNIQUE (workspace_id, user_name),
    CONSTRAINT uk_scim_users_id_workspace UNIQUE (id, workspace_id)
);

CREATE INDEX idx_user_external_id ON scim_users (workspace_id, external_id);

CREATE TABLE scim_groups (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    external_id VARCHAR(255),
    display_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_modified TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT,
    CONSTRAINT fk_scim_groups_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE,
    CONSTRAINT uk_scim_groups_workspace_display_name UNIQUE (workspace_id, display_name),
    CONSTRAINT uk_scim_groups_id_workspace UNIQUE (id, workspace_id)
);

CREATE INDEX idx_group_external_id ON scim_groups (workspace_id, external_id);

CREATE TABLE scim_user_emails (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    attr_value VARCHAR(255),
    type VARCHAR(255),
    display VARCHAR(255),
    primary_flag BOOLEAN NOT NULL,
    CONSTRAINT fk_scim_user_emails_user FOREIGN KEY (user_id, workspace_id)
        REFERENCES scim_users (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_emails_workspace_user_id ON scim_user_emails (workspace_id, user_id);

CREATE TABLE scim_user_phone_numbers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    attr_value VARCHAR(255),
    type VARCHAR(255),
    display VARCHAR(255),
    primary_flag BOOLEAN NOT NULL,
    CONSTRAINT fk_scim_user_phone_numbers_user FOREIGN KEY (user_id, workspace_id)
        REFERENCES scim_users (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_phone_numbers_workspace_user_id ON scim_user_phone_numbers (workspace_id, user_id);

CREATE TABLE scim_user_addresses (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    formatted VARCHAR(255),
    street_address VARCHAR(255),
    locality VARCHAR(255),
    region VARCHAR(255),
    postal_code VARCHAR(255),
    country VARCHAR(255),
    type VARCHAR(255),
    primary_flag BOOLEAN NOT NULL,
    CONSTRAINT fk_scim_user_addresses_user FOREIGN KEY (user_id, workspace_id)
        REFERENCES scim_users (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_addresses_workspace_user_id ON scim_user_addresses (workspace_id, user_id);

CREATE TABLE scim_user_entitlements (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    attr_value VARCHAR(255),
    type VARCHAR(255),
    display VARCHAR(255),
    primary_flag BOOLEAN NOT NULL,
    CONSTRAINT fk_scim_user_entitlements_user FOREIGN KEY (user_id, workspace_id)
        REFERENCES scim_users (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_entitlements_workspace_user_id ON scim_user_entitlements (workspace_id, user_id);

CREATE TABLE scim_user_roles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    attr_value VARCHAR(255),
    type VARCHAR(255),
    display VARCHAR(255),
    primary_flag BOOLEAN NOT NULL,
    CONSTRAINT fk_scim_user_roles_user FOREIGN KEY (user_id, workspace_id)
        REFERENCES scim_users (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_workspace_user_id ON scim_user_roles (workspace_id, user_id);

CREATE TABLE scim_user_ims (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    attr_value VARCHAR(255),
    type VARCHAR(255),
    display VARCHAR(255),
    primary_flag BOOLEAN NOT NULL,
    CONSTRAINT fk_scim_user_ims_user FOREIGN KEY (user_id, workspace_id)
        REFERENCES scim_users (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_ims_workspace_user_id ON scim_user_ims (workspace_id, user_id);

CREATE TABLE scim_user_photos (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    attr_value VARCHAR(255),
    type VARCHAR(255),
    display VARCHAR(255),
    primary_flag BOOLEAN NOT NULL,
    CONSTRAINT fk_scim_user_photos_user FOREIGN KEY (user_id, workspace_id)
        REFERENCES scim_users (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_photos_workspace_user_id ON scim_user_photos (workspace_id, user_id);

CREATE TABLE scim_user_x509_certificates (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    attr_value TEXT,
    type VARCHAR(255),
    display VARCHAR(255),
    primary_flag BOOLEAN NOT NULL,
    CONSTRAINT fk_scim_user_x509_certificates_user FOREIGN KEY (user_id, workspace_id)
        REFERENCES scim_users (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_x509_certificates_workspace_user_id ON scim_user_x509_certificates (workspace_id, user_id);

CREATE TABLE scim_group_memberships (
    id UUID PRIMARY KEY,
    group_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    member_value UUID NOT NULL,
    member_type VARCHAR(255) NOT NULL,
    display VARCHAR(255),
    CONSTRAINT fk_scim_group_memberships_group FOREIGN KEY (group_id, workspace_id)
        REFERENCES scim_groups (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX idx_group_memberships_workspace_group_id ON scim_group_memberships (workspace_id, group_id);
CREATE INDEX idx_membership_member_value ON scim_group_memberships (member_value);
CREATE INDEX idx_membership_group_id ON scim_group_memberships (group_id);

CREATE TABLE workspace_tokens (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    expires_at TIMESTAMP WITH TIME ZONE,
    revoked BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_workspace_tokens_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE
);

CREATE INDEX idx_workspace_tokens_workspace_id_name ON workspace_tokens (workspace_id, name);

CREATE TABLE scim_request_logs (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    http_method VARCHAR(255) NOT NULL,
    request_path VARCHAR(255) NOT NULL,
    http_status INTEGER,
    request_body TEXT,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_scim_request_logs_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id) ON DELETE CASCADE
);

CREATE INDEX idx_scim_request_logs_workspace_created_at ON scim_request_logs (workspace_id, created_at DESC);