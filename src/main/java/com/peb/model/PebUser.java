// src/main/java/com/peb/model/PebUser.java
package com.peb.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "PEB_USERS")
public class PebUser {

    public enum Status   { ACTIVE, INACTIVE, PENDING }
    public enum AuthType { AD, LOCAL }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
                    generator  = "peb_users_seq")
    @SequenceGenerator(name           = "peb_users_seq",
                       sequenceName   = "PEB_USERS_SEQ",
                       allocationSize = 1)
    private Long id;

    @Column(name = "USERNAME", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "DISPLAY_NAME", length = 200)
    private String displayName;

    @Column(name = "DEPARTMENT", length = 200)
    private String department;

    @Column(name = "EMAIL", length = 200)
    private String email;

    @Column(name = "ROLE", nullable = false, length = 50)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "AUTH_TYPE", nullable = false, length = 10)
    private AuthType authType = AuthType.AD;

    @Column(name = "PASSWORD_HASH", length = 100)
    private String passwordHash;

    @Column(name = "IS_SERVICE_ACCOUNT", nullable = false)
    private boolean serviceAccount = false;

    @Column(name = "API_TOKEN", length = 100, unique = true)
    private String apiToken;

    @Column(name = "API_TOKEN_EXPIRES")
    private LocalDateTime apiTokenExpires;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Column(name = "LAST_LOGIN")
    private LocalDateTime lastLogin;

    @Column(name = "NOTES", length = 500)
    private String notes;

    // ── Konstruktoren ─────────────────────────────────────────
    public PebUser() {}

    // AD-User
    public PebUser(String username, String displayName,
                   String email, String role) {
        this.username    = username;
        this.displayName = displayName;
        this.department  = extractDepartment(displayName);
        this.email       = email;
        this.role        = role;
        this.authType    = AuthType.AD;
        this.createdAt   = LocalDateTime.now();
    }

    // Lokaler Service-User
    public PebUser(String username, String displayName,
                   String email, String role, String passwordHash) {
        this.username     = username;
        this.displayName  = displayName;
        this.department   = extractDepartment(displayName);
        this.email        = email;
        this.role         = role;
        this.authType     = AuthType.LOCAL;
        this.passwordHash = passwordHash;
        this.status       = Status.ACTIVE;
        this.createdAt    = LocalDateTime.now();
    }

    // ── Getter & Setter ───────────────────────────────────────
    public Long getId()                             { return id; }
    public void setId(Long id)                      { this.id = id; }

    public String getUsername()                     { return username; }
    public void setUsername(String u)               { this.username = u; }

    public String getDisplayName()                  { return displayName; }
    public void setDisplayName(String n)            {
        this.displayName = n;
        this.department  = extractDepartment(n);
    }

    public String getDepartment()                   { return department; }
    public void setDepartment(String d)              { this.department = d; }

    public String getEmail()                        { return email; }
    public void setEmail(String e)                  { this.email = e; }

    public String getRole()                         { return role; }
    public void setRole(String r)                   { this.role = r; }

    public Status getStatus()                       { return status; }
    public void setStatus(Status s)                 { this.status = s; }

    public AuthType getAuthType()                   { return authType; }
    public void setAuthType(AuthType t)             { this.authType = t; }

    public String getPasswordHash()                 { return passwordHash; }
    public void setPasswordHash(String h)           { this.passwordHash = h; }

    public boolean isServiceAccount()               { return serviceAccount; }
    public void setServiceAccount(boolean b)        { this.serviceAccount = b; }

    // ── API-Token Getter/Setter (fehlten!) ────────────────────
    public String getApiToken()                     { return apiToken; }
    public void setApiToken(String t)               { this.apiToken = t; }

    public LocalDateTime getApiTokenExpires()       { return apiTokenExpires; }
    public void setApiTokenExpires(LocalDateTime t) { this.apiTokenExpires = t; }

    public LocalDateTime getCreatedAt()             { return createdAt; }
    public void setCreatedAt(LocalDateTime t)       { this.createdAt = t; }

    public String getCreatedBy()                    { return createdBy; }
    public void setCreatedBy(String b)              { this.createdBy = b; }

    public LocalDateTime getLastLogin()             { return lastLogin; }
    public void setLastLogin(LocalDateTime t)       { this.lastLogin = t; }

    public String getNotes()                        { return notes; }
    public void setNotes(String n)                  { this.notes = n; }

    // ── Hilfsmethoden ─────────────────────────────────────────
    public boolean isAdUser()    { return AuthType.AD.equals(authType); }
    public boolean isLocalUser() { return AuthType.LOCAL.equals(authType); }

    private String extractDepartment(String sourceDisplayName) {
        if (sourceDisplayName == null) {
            return null;
        }

        String trimmed = sourceDisplayName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        int start = trimmed.lastIndexOf('(');
        int end   = trimmed.lastIndexOf(')');
        if (start >= 0 && end > start) {
            String value = trimmed.substring(start + 1, end).trim();
            return value.isEmpty() ? null : value;
        }

        return null;
    }
}