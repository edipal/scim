package com.scimplayground.mgmt.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "mgmt_users")
public class MgmtUser {

    @Id
    @Column(length = 500, nullable = false)
    private String id;

    @Column(length = 500)
    private String email;

    @Column(name = "last_login_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = false)
    private OffsetDateTime lastLoginAt;

    public MgmtUser() {}

    public MgmtUser(String id, String email, OffsetDateTime lastLoginAt) {
        this.id = id;
        this.email = email;
        this.lastLoginAt = lastLoginAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public OffsetDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(OffsetDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
