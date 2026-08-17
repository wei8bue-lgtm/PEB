package com.peb.repository.product;

import com.peb.model.product.ProductBOMStructureItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductBOMStructureItemRepository extends JpaRepository<ProductBOMStructureItem, Long> {

    void deleteByProductBOMId(Long productBOMId);

    List<ProductBOMStructureItem> findByProductBOMIdAndParentItemIsNullOrderBySortOrderAscIdAsc(Long productBOMId);
}
