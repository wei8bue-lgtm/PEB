package com.peb.model.user;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Permission entity for fine-grained authorization
 */
@Data
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "permission_seq")
    @SequenceGenerator(name = "permission_seq", sequenceName = "PERMISSION_SEQ", allocationSize = 1)
    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "permission_name", unique = true, nullable = false)
    private String permissionName;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_type")
    private PermissionType permissionType;
}

enum PermissionType {
    READ,
    WRITE,
    DELETE,
    EXECUTE
}