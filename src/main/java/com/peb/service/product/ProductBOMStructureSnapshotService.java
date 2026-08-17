package com.peb.service.product;

import com.peb.model.product.ProductBOM;
import com.peb.model.product.ProductBOMStructureItem;
import com.peb.model.structure.StructureNode;
import com.peb.repository.product.ProductBOMStructureItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class ProductBOMStructureSnapshotService {

    private final ProductBOMStructureItemRepository itemRepository;

    @Transactional
    public void replaceSnapshot(ProductBOM productBOM, StructureNode rootNode) {
        itemRepository.deleteByProductBOMId(productBOM.getId());
        if (rootNode == null) {
            return;
        }

        AtomicInteger sortOrder = new AtomicInteger(10);
        persistNode(productBOM, null, rootNode, sortOrder);
    }

    @Transactional(readOnly = true)
    public StructureNode getSnapshotTree(Long productBOMId) {
        List<ProductBOMStructureItem> roots = itemRepository
                .findByProductBOMIdAndParentItemIsNullOrderBySortOrderAscIdAsc(productBOMId);
        if (roots.isEmpty()) {
            return null;
        }
        return toStructureNode(roots.get(0));
    }

    private void persistNode(ProductBOM productBOM,
                             ProductBOMStructureItem parentItem,
                             StructureNode node,
                             AtomicInteger sortOrder) {
        ProductBOMStructureItem item = new ProductBOMStructureItem();
        item.setProductBOM(productBOM);
        item.setParentItem(parentItem);
        item.setSortOrder(sortOrder.getAndAdd(10));
        item.setObjectId(node.getObjectId());
        item.setObjectType(node.getObjectType());
        item.setName(node.getName());
        item.setRevision(node.getRevision());
        item.setReleaseStatus(node.getReleaseStatus());
        item.setQuantity(node.getQuantity());
        item.setPositionNumber(node.getPositionNumber());
        item.setReferenceDesignator(node.getReferenceDesignator());
        item.setBmk(node.getBmk());
        item.setRelationType(node.getRelationType());
        item.setConfigRule(node.getConfigRule());
        item.setAlternativeGroup(node.getAlternativeGroup());

        ProductBOMStructureItem saved = itemRepository.save(item);
        for (StructureNode child : node.getChildren()) {
            persistNode(productBOM, saved, child, sortOrder);
        }
    }

    private StructureNode toStructureNode(ProductBOMStructureItem item) {
        StructureNode node = new StructureNode();
        node.setObjectId(item.getObjectId());
        node.setName(item.getName());
        node.setObjectType(item.getObjectType());
        node.setRevision(item.getRevision());
        node.setReleaseStatus(item.getReleaseStatus());
        node.setQuantity(item.getQuantity());
        node.setPositionNumber(item.getPositionNumber());
        node.setReferenceDesignator(item.getReferenceDesignator());
        node.setBmk(item.getBmk());
        node.setRelationType(item.getRelationType());
        node.setConfigRule(item.getConfigRule());
        node.setAlternativeGroup(item.getAlternativeGroup());

        if (item.getChildren() != null) {
            item.getChildren().stream()
                    .sorted((left, right) -> {
                        int leftSort = left.getSortOrder() != null ? left.getSortOrder() : Integer.MAX_VALUE;
                        int rightSort = right.getSortOrder() != null ? right.getSortOrder() : Integer.MAX_VALUE;
                        int compare = Integer.compare(leftSort, rightSort);
                        if (compare != 0) {
                            return compare;
                        }
                        long leftId = left.getId() != null ? left.getId() : Long.MAX_VALUE;
                        long rightId = right.getId() != null ? right.getId() : Long.MAX_VALUE;
                        return Long.compare(leftId, rightId);
                    })
                    .forEach(child -> node.getChildren().add(toStructureNode(child)));
        }

        return node;
    }
}
