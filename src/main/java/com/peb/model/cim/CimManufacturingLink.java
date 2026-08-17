package com.peb.model.cim;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cim_mfg_links")
public class CimManufacturingLink {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cim_mfg_link_seq")
    @SequenceGenerator(name = "cim_mfg_link_seq", sequenceName = "CIM_MFG_LINK_SEQ", allocationSize = 1)
    @Column(name = "link_id")
    private Long linkId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_cim_revision_id", nullable = false)
    private CimRevision sourceCimRevision;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40)
    private CimManufacturingTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @Column(name = "configuration_id")
    private Long configurationId;

    @Column(name = "configuration_name", length = 255)
    private String configurationName;

    @Column(name = "selected_option_tokens", length = 2000)
    private String selectedOptionTokens;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_mode", nullable = false, length = 20)
    private CimImportMode importMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_result", nullable = false, length = 20)
    private CimImportResult importResult;

    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    @Column(name = "imported_by", length = 100)
    private String importedBy;

    @Column(name = "message", length = 1000)
    private String message;
}
