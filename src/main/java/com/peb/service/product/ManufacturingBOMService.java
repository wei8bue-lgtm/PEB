package com.peb.service.product;

import com.peb.model.product.ManufacturingBOM;
import com.peb.model.product.ManufacturingOperation;           // ← NEU
import com.peb.repository.product.ManufacturingBOMRepository;
import com.peb.repository.product.ManufacturingOperationRepository; // ← NEU
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;                                         // ← NEU
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManufacturingBOMService {

    private final ManufacturingBOMRepository       mfgRepo;
    private final ManufacturingOperationRepository opRepo;   // ← jetzt bekannt

    @Transactional(readOnly = true)
    public ManufacturingBOM findByIdWithAllDetails(Long id) {

        ManufacturingBOM bom = mfgRepo.findByIdWithProductBOMs(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ManufacturingBOM nicht gefunden: " + id));

        List<ManufacturingOperation> operations =
                opRepo.findByManufacturingBOMIdOrderBySequenceNumber(id);
				
		log.info("=== DEBUG BOM ID={} ===", id);
		log.info("Anzahl Operations aus DB: {}", operations.size());
		operations.forEach(op ->
				log.info("  -> OP id={}, name={}, seq={}, bomId={}",
						op.getId(),
						op.getName(),
						op.getSequenceNumber(),
						op.getManufacturingBOM() != null
								? op.getManufacturingBOM().getId()
								: "NULL"));

/*         log.debug("BOM {} geladen: {} ProductBOMs, {} Operations",
                id,
                bom.getProductBOMs().size(),
                operations.size()); */

        bom.setOperations(operations);
        return bom;
    }

    @Transactional(readOnly = true)
    public Optional<ManufacturingBOM> findByIdWithOperations(Long id) {
        return mfgRepo.findByIdWithOperations(id);
    }
}