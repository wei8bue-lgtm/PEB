// DATEI: src/main/java/com/peb/service/product/ProductDataService.java
// PACKAGE MUSS EXAKT SO SEIN:
package com.peb.service.product;

import com.peb.model.product.ProductData;
import com.peb.repository.product.ProductDataRepository;
import com.peb.service.IDGeneratorService;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductDataService {

    private final ProductDataRepository repository;
    private final IDGeneratorService    idGeneratorService;

    @Transactional
    public ProductData create(ProductData pd) {
        String newId = idGeneratorService.generateId(
                ObjectType.PRODUCT_DATA);
        pd.setId(newId);
        log.info("Erstelle ProductData: {} – '{}'",
                newId, pd.getName());
        return repository.save(pd);
    }

    @Transactional
    public ProductData update(String id, ProductData form) {
        ProductData existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ProductData nicht gefunden: " + id));
        existing.setName(form.getName());
        existing.setDepartment(form.getDepartment());
        existing.setProductType(form.getProductType());
        existing.setDescription(form.getDescription());
        existing.setStatus(form.getStatus());
        log.info("Aktualisiere ProductData: {}", id);
        return repository.save(existing);
    }

    @Transactional(readOnly = true)
    public Optional<ProductData> findById(String id) {
        return repository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<ProductData> findAll() {
        return repository.findAll(
                Sort.by(Sort.Direction.ASC, "id"));
    }

    @Transactional(readOnly = true)
    public List<ProductData> search(String query) {
        if (query == null || query.isBlank()) {
            return findAll();
        }
        String q = query.replace("*", "").trim();
        return repository.searchByQuery(q);
    }

    @Transactional
    public void delete(String id) {
        log.info("Lösche ProductData: {}", id);
        repository.deleteById(id);
    }
}