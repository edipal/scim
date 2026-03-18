package com.scimplayground.server.model;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.util.UUID;

@Entity
@Table(name = "scim_group_memberships", indexes = {
    @Index(name = "idx_membership_member_value", columnList = "member_value"),
    @Index(name = "idx_membership_group_id", columnList = "group_id")
})
public class ScimGroupMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ScimGroup group;

    @Column(name = "member_value", nullable = false)
    private UUID memberValue;

    @Column(name = "member_type", nullable = false)
    private String memberType;

    @Column(name = "display")
    private String display;

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ScimGroup getGroup() {
        return group;
    }

    public void setGroup(ScimGroup group) {
        this.group = group;
    }

    public UUID getMemberValue() {
        return memberValue;
    }

    public void setMemberValue(UUID memberValue) {
        this.memberValue = memberValue;
    }

    public String getMemberType() {
        return memberType;
    }

    public void setMemberType(String memberType) {
        this.memberType = memberType;
    }

    public String getDisplay() {
        return display;
    }

    public void setDisplay(String display) {
        this.display = display;
    }
}
