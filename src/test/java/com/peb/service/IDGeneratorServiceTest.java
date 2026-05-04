package com.peb.service;

import com.peb.repository.DesignRepository;
import com.peb.util.ObjectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class IDGeneratorServiceTest {

    @Mock
    private DesignRepository designRepository;

    @InjectMocks
    private IDGeneratorService idGeneratorService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGenerateDesignId() {
        when(designRepository.findMaxSequence()).thenReturn(5L);
        
        String id = idGeneratorService.generateId(ObjectType.DESIGN);
        
        assertEquals("DS00000006", id);
    }

    @Test
    void testGenerateFirstId() {
        when(designRepository.findMaxSequence()).thenReturn(null);
        
        String id = idGeneratorService.generateId(ObjectType.DESIGN);
        
        assertEquals("DS00000001", id);
    }
}