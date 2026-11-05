package com.peb.model.planning;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "planning_project")
@Getter
@Setter
public class PlanningProject {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(length = 36)
    private String id;
    @Version
    private Long version;
    @Column(nullable = false, length = 255)
    private String name;
    @Column(nullable = false)
    private int currentRevision = 1;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false, length = 100)
    private String createdBy;
    @Column(nullable = false)
    private LocalDateTime modifiedAt;
    @Column(nullable = false, length = 100)
    private String modifiedBy;
}