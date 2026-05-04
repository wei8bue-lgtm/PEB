package com.peb.service;

import com.peb.exception.WorkflowException;
import com.peb.model.cad.Design;
import com.peb.model.cad.DesignRevision;
import com.peb.repository.DesignRepository;
import com.peb.repository.DesignRevisionRepository;
import com.peb.util.ReleaseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RevisionServiceTest {

    @Mock
    private DesignRepository designRepository;

    @Mock
    private DesignRevisionRepository designRevisionRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private RevisionService revisionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testReviseDesign_Success() {
        Design design = new Design();
        design.setId("DS00000001");
        
        DesignRevision releasedRevision = new DesignRevision();
        releasedRevision.setRevision("01");
        releasedRevision.setReleaseStatus(ReleaseStatus.RELEASED);
        releasedRevision.setDesign(design);
        
        design.getRevisions().add(releasedRevision);

        when(designRepository.findById("DS00000001")).thenReturn(Optional.of(design));
        when(designRevisionRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);

        DesignRevision newRevision = revisionService.reviseDesign("DS00000001", "testuser");

        assertNotNull(newRevision);
        assertEquals("02", newRevision.getRevision());
        assertEquals(ReleaseStatus.WORKING, newRevision.getReleaseStatus());
        verify(emailService).sendRevisionSuccessEmail("testuser", "DS00000001", "02");
    }

    @Test
    void testReviseDesign_NoReleasedRevision() {
        Design design = new Design();
        design.setId("DS00000001");

        when(designRepository.findById("DS00000001")).thenReturn(Optional.of(design));

        assertThrows(WorkflowException.class, () -> {
            revisionService.reviseDesign("DS00000001", "testuser");
        });
    }
}