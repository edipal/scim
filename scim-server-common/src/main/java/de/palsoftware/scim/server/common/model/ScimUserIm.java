package de.palsoftware.scim.server.common.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.util.UUID;

@Entity
@Table(name = "scim_user_ims", indexes = {
    @Index(name = "idx_user_ims_workspace_user_id", columnList = "workspace_id, user_id")
})
public class ScimUserIm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ScimUser user;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "attr_value")
    private String value;

    @Column(name = "type")
    private String type;

    @Column(name = "display")
    private String display;

    @Column(name = "primary_flag", nullable = false)
    private boolean primaryFlag = false;

    @PrePersist
    @PreUpdate
    protected void syncWorkspaceId() {
        workspaceId = user != null && user.getWorkspace() != null ? user.getWorkspace().getId() : null;
        if (workspaceId == null) {
            throw new IllegalStateException("User IM requires a workspace id");
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

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public boolean isPrimaryFlag() {
        return primaryFlag;
    }

    public void setPrimaryFlag(boolean primaryFlag) {
        this.primaryFlag = primaryFlag;
    }
}
