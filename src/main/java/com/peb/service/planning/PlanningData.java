package com.peb.service.planning;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

public class PlanningData {
    public List<String> inputHeaders = new ArrayList<>();
    public List<StructureRow> structure = new ArrayList<>();
    public List<ArticleMaster> articleMasters = new ArrayList<>();
    public List<AssemblyDefinition> assemblyDefinitions = new ArrayList<>();
    public List<Map<String, String>> zones = new ArrayList<>();
    public List<ZoneDelivery> deliveries = new ArrayList<>();
    public List<Workplace> workplaces = new ArrayList<>();
    public List<ProcessStepGroup> processStepGroups = new ArrayList<>();
    public List<ProcessTemplateRevision> processTemplates = new ArrayList<>();
    public List<ProcessUse> processUses = new ArrayList<>();
    public List<Map<String, String>> output = new ArrayList<>();
    public String sourceFilename;
    public String sourceSha256;

    public record StructureRow(int sourceRow, int depth, Integer parentRow,
                               Map<String, String> cells) {}

    public record ArticleMaster(String customerErpNumber, String customerRevision,
                                 String boschSapNumber, String boschSapRevision,
                                 String description, Map<String, String> attributes,
                                 List<Integer> sourceRows, boolean missingMasterData) {
        public String identity() { return customerErpNumber + "|" + customerRevision; }
    }

    public record Component(String customerErpNumber, String customerRevision,
                            String quantity, int sourceRow) {}

    public record AssemblyDefinition(String customerErpNumber, String customerRevision,
                                     List<Component> components, List<Integer> usageRows,
                                     boolean conflictingBreakdown) {}

    public record Workplace(String id, String zoneNumber, String code, String name,
                            String taktMinutes) {}

    public record ZoneDelivery(String sourceZone, String destinationZone, String customerErpNumber,
                               String customerRevision, String quantity) {}

    public record ProcessStepGroup(String id, String name, int sequence) {}

    public record ProcessTemplateRevision(String templateId, int revision, String name,
                                          List<OperationStep> operations) {}

    public record ProcessUse(String id, String stepGroupId, String templateId, int templateRevision,
                             String targetErpNumber, String targetRevision) {}

    public record OperationStep(String id, String name, String description, int sequence,
                                int executionsPerUnit, BigDecimal estimatedSeconds,
                                BigDecimal timeStudySeconds, String timeStudySource,
                                String timeStudyReference, BigDecimal actualSeconds,
                                String workplaceId, List<ResourceRequirement> resources,
                                List<OperationDocument> documents) {
        public OperationStep(String id, String name, String description, int sequence,
                             int executionsPerUnit, BigDecimal estimatedSeconds,
                             BigDecimal timeStudySeconds, String timeStudySource,
                             String timeStudyReference, BigDecimal actualSeconds,
                             String workplaceId, List<ResourceRequirement> resources) {
            this(id, name, description, sequence, executionsPerUnit, estimatedSeconds,
                    timeStudySeconds, timeStudySource, timeStudyReference, actualSeconds,
                    workplaceId, resources, List.of());
        }
    }

    public record OperationDocument(String id, String type, String title, String uri,
                                    String note, boolean external) {}

    public record StructureChangeRow(String change, int depth, String articleNumber,
                                     String articleRevision, String description,
                                     String previousQuantity, String currentQuantity,
                                     Integer previousRow, Integer currentRow) {}

    public record StructureComparison(int previousRevision, int currentRevision,
                                      List<StructureChangeRow> rows) {}

    public record ResourceRequirement(String id, ResourceType type, String articleNumber,
                                      String articleRevision, String description,
                                      BigDecimal quantity, String unit, String specialNumber,
                                      String containerType, BigDecimal unitsPerContainer, String packagingUnit,
                                      String containerAssignment) {
        public ResourceRequirement(String id, ResourceType type, String articleNumber, String articleRevision,
                                   String description, BigDecimal quantity, String unit) {
            this(id, type, articleNumber, articleRevision, description, quantity, unit, "", "", null, "", "");
        }
    }

    public enum ResourceType { MATERIAL, TOOL, AID }

    public record WorkplaceLoad(String workplaceId, String workplaceName,
                                String targetErpNumber, String targetRevision,
                                BigDecimal workContentSeconds, BigDecimal targetSeconds,
                                BigDecimal loadPercent, List<OperationLoad> operations) {}

    public record OperationLoad(String operationId, String operationName, String processName,
                                int sequence, BigDecimal effectiveSeconds,
                                String effectiveTimeSource, int executionsPerUnit) {}

    public record MaterialValidationRow(String targetErpNumber, String targetRevision,
                                        String articleNumber, String articleRevision, String description,
                                        BigDecimal expectedQuantity, BigDecimal actualQuantity,
                                        BigDecimal variance, String status) {}

    public record MaterialValidationReport(String projectName, int revision, String generatedAt,
                                           String zoneNumber, String workplaceId,
                                           List<MaterialValidationRow> rows) {}

    public record ValidationSummary(String status, long matchedRows, long shortageRows, long excessRows,
                                    String projectName, int revision, String generatedAt) {}

    public record NumberValidationRow(String customerErpNumber, String customerRevision, String boschSapNumber,
                                      String boschSapRevision, String description, String status, String details) {}

    public record NumberValidationReport(String projectName, int revision, String generatedAt,
                                         List<NumberValidationRow> rows) {}

    public record ZoneSupplyValidationRow(String sourceZone, String sourceZoneName,
                                          String destinationZone, String destinationZoneName,
                                          String articleNumber, String articleRevision,
                                          BigDecimal suppliedPerHour, BigDecimal requiredPerHour,
                                          BigDecimal variancePerHour, BigDecimal sourceTaktSeconds,
                                          BigDecimal destinationTaktSeconds, String status) {}

    public record ZoneSupplyValidationReport(String projectName, int revision, String generatedAt,
                                             List<ZoneSupplyValidationRow> rows) {}

    public record ResourceReportRow(String resourceType, String articleNumber, String articleRevision,
                                    String specialNumber, String description, String processName,
                                    String operationId, String operationName, String containerAssignment, String workplaceId,
                                    String workplaceName, String targetErpNumber, String targetRevision,
                                    String unit, BigDecimal requiredQuantityPerUnit, String containerType,
                                    BigDecimal unitsPerContainer, BigDecimal containersPerUnit,
                                    String packagingUnit) {}

    public record ResourceReport(String projectName, int revision, String generatedAt, String kind,
                                 String zoneNumber, String workplaceId, List<ResourceReportRow> rows) {}

    public static String column(int index) {
        return org.apache.poi.ss.util.CellReference.convertNumToColString(index);
    }

    public static Map<String, String> emptyRow(int width) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int index = 0; index < width; index++) row.put(column(index), "");
        return row;
    }
}