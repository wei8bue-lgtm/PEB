package com.peb.service;

import com.peb.repository.*;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for generating unique object IDs
 */
@Service
@RequiredArgsConstructor
public class IDGeneratorService {

    private final DesignRepository designRepository;
    private final DrawingRepository drawingRepository;
    private final MachineRepository machineRepository;
    private final SystemRepository systemRepository;
    private final SubsystemRepository subsystemRepository;
    private final EBOMModuleRepository ebomModuleRepository;
    private final SubmoduleRepository submoduleRepository;
    private final PartRepository partRepository;
    private final AuxiliaryRepository auxiliaryRepository;

    @Transactional
    public synchronized String generateId(ObjectType objectType) {
        long sequence = getNextSequence(objectType);
        return objectType.formatId(sequence);
    }

    private long getNextSequence(ObjectType objectType) {
        Long maxSequence = switch (objectType) {
            case DESIGN -> designRepository.findMaxSequence();
            case DRAWING -> drawingRepository.findMaxSequence();
            case MACHINE -> machineRepository.findMaxSequence();
            case SYSTEM -> systemRepository.findMaxSequence();
            case SUBSYSTEM -> subsystemRepository.findMaxSequence();
            case MODULE -> ebomModuleRepository.findMaxSequence();
            case SUBMODULE -> submoduleRepository.findMaxSequence();
            case PART -> partRepository.findMaxSequence();
            case AUXILIARY -> auxiliaryRepository.findMaxSequence();
        };
        return (maxSequence != null ? maxSequence : 0L) + 1;
    }
}