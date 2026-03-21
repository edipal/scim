package de.palsoftware.scim.server.common.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.util.UUID;

@Entity
@Table(name = "scim_user_addresses", indexes = {
    @Index(name = "idx_user_addresses_workspace_user_id", columnList = "workspace_id, user_id")
})
public class ScimUserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ScimUser user;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "formatted")
    private String formatted;

    @Column(name = "street_address")
    private String streetAddress;

    @Column(name = "locality")
    private String locality;

    @Column(name = "region")
    private String region;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "country")
    private String country;

    @Column(name = "type")
    private String type;

    @Column(name = "primary_flag", nullable = false)
    private boolean primaryFlag = false;

    @PrePersist
    @PreUpdate
    protected void syncWorkspaceId() {
        workspaceId = user != null && user.getWorkspace() != null ? user.getWorkspace().getId() : null;
        if (workspaceId == null) {
            throw new IllegalStateException("User address requires a workspace id");
        }
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ScimUser getUser() {
        return user;
    }

    public void setUser(ScimUser user) {
        this.user = user;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getFormatted() {
        return formatted;
    }

    public void setFormatted(String formatted) {
        this.formatted = formatted;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isPrimaryFlag() {
        return primaryFlag;
    }

    public void setPrimaryFlag(boolean primaryFlag) {
        this.primaryFlag = primaryFlag;
    }
}
