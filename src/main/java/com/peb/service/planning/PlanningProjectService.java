package com.peb.service.planning;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peb.model.planning.*;
import com.peb.model.product.ManufacturingStatus;
import com.peb.repository.planning.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanningProjectService {
    private final PlanningProjectRepository projects;
    private final PlanningRevisionRepository revisions;
    private final PlanningExcelService excel;
    private final ObjectMapper mapper;
    private final PlanningValidationService validationService;

    public record RevisionInfo(int number, ManufacturingStatus status, LocalDateTime createdAt, String createdBy,
                               LocalDateTime releasedAt, String releasedBy) {}
    public record ProjectInfo(String id, String name, Long version, int revision, ManufacturingStatus status,
                              LocalDateTime modifiedAt, String modifiedBy) {}
    public record View(ProjectInfo project, PlanningData data, List<RevisionInfo> revisions,
                       List<PlanningRules.Part> parts, List<PlanningRules.ArticleOption> articles,
                       List<Map<String, String>> articleRows, List<Map<String, String>> workplaceRows,
                       List<String> articleHeaders, List<String> workplaceHeaders,
                       List<String> zoneHeaders, List<String> outputHeaders, ProcessPlan processPlan) {}
    public record ProcessTemplateSummary(String templateId, int revision, int latestRevision, String name, int usageCount,
                                         List<PlanningData.OperationStep> operations) {}
    public record ProcessPlan(List<PlanningData.ProcessStepGroup> groups, List<ProcessTemplateSummary> templates,
                              List<PlanningData.ProcessUse> uses, List<PlanningData.WorkplaceLoad> balance,
                              List<String> consistencyIssues) {}
    public record ReportQuery(String kind, String zoneNumber, String workplaceId, String targetErpNumber,
                              String targetRevision, Integer revision, String state) {}
    public record WorkSequence(String projectName, int revision, PlanningData data) {}

    public WorkSequence releasedWorkSequence(String id, Integer number) {
        PlanningProject project = find(id);
        PlanningRevision source = revision(project, number == null ? project.getCurrentRevision() : number);
        if (source.getStatus() != ManufacturingStatus.RELEASED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Arbeitsfolgekarte kann nur aus einem freigegebenen Stand ausgegeben werden.");
        }
        PlanningData data = decode(source);
        ProcessPlanningRules.validateForRelease(data);
        return new WorkSequence(project.getName(), source.getNumber(), data);
    }

    public Object report(String id, ReportQuery query) {
        PlanningProject project = find(id);
        PlanningRevision source = revision(project, query.revision() == null ? project.getCurrentRevision() : query.revision());
        PlanningData data = decode(source);
        String kind = query.kind() == null ? "materials" : query.kind().toLowerCase(Locale.ROOT);
        Object report = switch (kind) {
            case "validation", "material-validation" -> validationService.materialUsage(data, project.getName(), source.getNumber(), query.zoneNumber(), query.workplaceId());
            case "zone-supply", "supply-validation" -> validationService.zoneSupply(data, project.getName(), source.getNumber());
            case "numbers", "number-validation" -> validationService.numberMappings(data, project.getName(), source.getNumber());
            case "materials", "material", "tools", "tool", "aids", "aid", "all", "resources" ->
                    validationService.resources(data, project.getName(), source.getNumber(), kind, query.zoneNumber(), query.workplaceId(),
                            query.targetErpNumber(), query.targetRevision());
            default -> throw new IllegalArgumentException("Reporttyp muss validation, zone-supply, materials, tools, aids oder all sein.");
        };
            return filterReportState(report, query.state());
    }

    public PlanningData.StructureComparison compareStructure(String id, int previousNumber, int currentNumber) {
        PlanningProject project = find(id);
        var previous = decode(revision(project, previousNumber));
        var current = decode(revision(project, currentNumber));
        Map<String, PlanningData.StructureRow> previousRows = comparisonRows(previous.structure);
        Map<String, PlanningData.StructureRow> currentRows = comparisonRows(current.structure);
        Set<String> keys = new LinkedHashSet<>(previousRows.keySet());
        keys.addAll(currentRows.keySet());
        List<PlanningData.StructureChangeRow> changes = new ArrayList<>();
        for (String key : keys) {
            var oldRow = previousRows.get(key);
            var newRow = currentRows.get(key);
            if (oldRow != null && newRow != null && Objects.equals(oldRow.cells().get("N"), newRow.cells().get("N"))
                    && Objects.equals(oldRow.cells().get("K"), newRow.cells().get("K"))) continue;
            var row = newRow == null ? oldRow : newRow;
            String change = oldRow == null ? "ADDED" : newRow == null ? "REMOVED" : "CHANGED";
            changes.add(new PlanningData.StructureChangeRow(change, row.depth(), row.cells().get("J"), row.cells().get("M"),
                    row.cells().get("K"), oldRow == null ? "" : oldRow.cells().get("N"),
                    newRow == null ? "" : newRow.cells().get("N"), oldRow == null ? null : oldRow.sourceRow(),
                    newRow == null ? null : newRow.sourceRow()));
        }
        return new PlanningData.StructureComparison(previousNumber, currentNumber, List.copyOf(changes));
    }

    private Map<String, PlanningData.StructureRow> comparisonRows(List<PlanningData.StructureRow> rows) {
        Map<Integer, String> paths = new HashMap<>();
        Map<String, Integer> occurrences = new HashMap<>();
        Map<String, PlanningData.StructureRow> result = new LinkedHashMap<>();
        for (var row : rows) {
            String parentPath = row.parentRow() == null ? "" : paths.getOrDefault(row.parentRow(), "?");
            String item = row.cells().get("J") + "\u0000" + row.cells().get("M");
            String siblingKey = parentPath + "\u0001" + item;
            int occurrence = occurrences.merge(siblingKey, 1, Integer::sum);
            String path = siblingKey + "\u0001" + occurrence;
            paths.put(row.sourceRow(), path);
            result.put(path, row);
        }
        return result;
    }

            private Object filterReportState(Object report, String state) {
            if (state == null || state.isBlank()) return report;
            String expected = state.toUpperCase(Locale.ROOT);
                if (report instanceof PlanningData.MaterialValidationReport result) {
                    requireReportState(expected, Set.of("MATCH", "SHORTAGE", "EXCESS"));
                    return new PlanningData.MaterialValidationReport(
                result.projectName(), result.revision(), result.generatedAt(), result.zoneNumber(), result.workplaceId(),
                        result.rows().stream().filter(row -> row.status().equals(expected)).toList());
                }
                if (report instanceof PlanningData.ZoneSupplyValidationReport result) {
                    requireReportState(expected, Set.of("MATCH", "SHORTAGE", "EXCESS"));
                    return new PlanningData.ZoneSupplyValidationReport(
                        result.projectName(), result.revision(), result.generatedAt(), result.rows().stream().filter(row -> row.status().equals(expected)).toList());
                }
                if (report instanceof PlanningData.NumberValidationReport result) {
                    requireReportState(expected, Set.of("MAPPED", "INCOMPLETE_MAPPING"));
                    return new PlanningData.NumberValidationReport(
                        result.projectName(), result.revision(), result.generatedAt(), result.rows().stream().filter(row -> row.status().equals(expected)).toList());
                }
            throw new IllegalArgumentException("Statusfilter wird fuer diesen Ressourcentyp nicht unterstuetzt.");
            }

            private void requireReportState(String state, Set<String> allowed) {
                if (!allowed.contains(state)) throw new IllegalArgumentException("Unbekannter Statusfilter: " + state);
            }

    public List<ProjectInfo> list() {
        return projects.findAll(org.springframework.data.domain.Sort.by("modifiedAt").descending()).stream()
                .map(project -> info(project, revision(project, project.getCurrentRevision()))).toList();
    }

    public View get(String id, Integer number) {
        PlanningProject project = find(id);
        PlanningRevision revision = revision(project, number == null ? project.getCurrentRevision() : number);
        return view(project, revision);
    }

    @Transactional
    public View create(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 255) throw new IllegalArgumentException("Projektname ist erforderlich (maximal 255 Zeichen).");
        PlanningProject project = new PlanningProject();
        project.setName(name.trim()); project.setCreatedAt(LocalDateTime.now()); project.setCreatedBy(user());
        touch(project);
        projects.saveAndFlush(project);
        PlanningRevision revision = new PlanningRevision();
        revision.setProject(project); revision.setNumber(1); revision.setCreatedAt(LocalDateTime.now()); revision.setCreatedBy(user());
        revision.setSnapshot(encode(new PlanningData()));
        revisions.saveAndFlush(revision);
        return view(project, revision);
    }

    @Transactional
    public View importStructure(String id, Long version, String filename, InputStream stream, boolean confirmReplace) throws IOException {
        return importStructure(id, version, filename, stream, confirmReplace, false);
    }

    @Transactional
    public View importStructure(String id, Long version, String filename, InputStream stream,
                                boolean confirmReplace, boolean newVersion) throws IOException {
        byte[] bytes = stream.readNBytes(20 * 1024 * 1024 + 1);
        if (bytes.length > 20 * 1024 * 1024) throw new IllegalArgumentException("Maximale Dateigroesse: 20 MB.");
        PlanningData imported = excel.readStructure(new ByteArrayInputStream(bytes));
        PlanningProject project = lock(id, version);
        PlanningRevision revision;
        PlanningData base;
        if (newVersion) {
            PlanningRevision current = revision(project, project.getCurrentRevision());
            base = decode(current);
            revision = createWorkingRevision(project, current);
        } else {
            revision = working(project);
            base = decode(revision);
            requireConfirmation(!base.structure.isEmpty(), confirmReplace);
        }
        PlanningData data = newVersion ? base : imported;
        data.inputHeaders = imported.inputHeaders;
        data.structure = imported.structure;
        data.articleMasters = imported.articleMasters;
        data.assemblyDefinitions = imported.assemblyDefinitions;
        if (newVersion) data.output.clear();
        data.sourceFilename = filename == null ? "Input.xlsx" : filename.replace('\\', '/').substring(filename.replace('\\', '/').lastIndexOf('/') + 1);
        try { data.sourceSha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        return persist(project, revision, data);
    }

    @Transactional
    public View saveTable(String id, Long version, String table, List<Map<String, String>> rows, boolean confirmOutputClear) {
        PlanningProject project = lock(id, version);
        PlanningRevision revision = working(project);
        PlanningData data = decode(revision);
        if (table.equals("zones")) {
            requireDeleteIfRemoving(data.zones, rows);
            var normalized = PlanningRules.normalizeZones(data, rows);
            if (!data.zones.equals(normalized)) {
                requireConfirmation(!data.output.isEmpty(), confirmOutputClear);
                data.output.clear();
            }
            data.zones = normalized;
                data.workplaces = PlanningRules.normalizeWorkplaces(workplaceRows(data), data.zones).stream()
                    .map(row -> new PlanningData.Workplace(row.get("_rowId"), row.get("A"), row.get("B"), row.get("C"), row.get("D"))).toList();
            } else if (table.equals("articles")) {
                requireDeleteIfRemoving(articleRows(data), rows);
                List<Map<String, String>> normalized = PlanningRules.normalizeArticles(data, rows);
                Set<String> retainedKeys = new HashSet<>();
                normalized.forEach(row -> retainedKeys.add(row.get("A") + "\u0000" + row.get("B")));
                for (var structureRow : data.structure) {
                    String key = structureRow.cells().get("J") + "\u0000" + structureRow.cells().get("M");
                    if (!retainedKeys.contains(key)) throw new IllegalArgumentException("Importierte Artikelidentitaeten mit Strukturverwendungen koennen nicht entfernt oder umbenannt werden; ERP-Nummer/Revision im Quellsystem korrigieren und erneut importieren.");
                }
                data.articleMasters = normalized.stream().map(row -> {
                PlanningData.ArticleMaster previous = data.articleMasters.stream().filter(article -> article.customerErpNumber().equals(row.get("A"))
                    && article.customerRevision().equals(row.get("B"))).findFirst().orElse(null);
                Map<String, String> attributes = previous == null ? new LinkedHashMap<>() : new LinkedHashMap<>(previous.attributes());
                return new PlanningData.ArticleMaster(row.get("A"), row.get("B"), row.get("C"), row.get("D"), row.get("E"), attributes,
                    previous == null ? List.of() : previous.sourceRows(), row.get("F").equals("FEHLENDE_STAMMDATEN"));
                }).toList();
            if (!data.zones.isEmpty()) data.zones = PlanningRules.normalizeZones(data, data.zones);
            if (!data.output.isEmpty()) data.output = PlanningRules.normalizeOutput(data, data.output);
            } else if (table.equals("workplaces")) {
                requireDeleteIfRemoving(workplaceRows(data), rows);
                data.workplaces = PlanningRules.normalizeWorkplaces(rows, data.zones).stream()
                    .map(row -> new PlanningData.Workplace(row.get("_rowId"), row.get("A"), row.get("B"), row.get("C"), row.get("D"))).toList();
        } else if (table.equals("output")) {
            requireDeleteIfRemoving(data.output, rows);
            data.output = PlanningRules.normalizeOutput(data, rows);
        } else throw new IllegalArgumentException("Unbekannte Tabelle.");
        return persist(project, revision, data);
    }

    @Transactional
    public View generate(String id, Long version, boolean confirmReplace) {
        PlanningProject project = lock(id, version);
        PlanningRevision revision = working(project);
        PlanningData data = decode(revision);
        requireConfirmation(!data.output.isEmpty(), confirmReplace);
        data.zones = PlanningRules.normalizeZones(data, data.zones);
        data.output = PlanningRules.generate(data);
        return persist(project, revision, data);
    }

    @Transactional
    public View lifecycle(String id, Long version, String action) {
        PlanningProject project = lock(id, version);
        PlanningRevision current = revision(project, project.getCurrentRevision());
        if (action.equals("release")) {
            working(project);
            PlanningData data = decode(current);
            if (data.structure.isEmpty() || data.zones.isEmpty() || data.output.isEmpty()) throw new IllegalArgumentException("Freigabe erfordert Struktur, Zonen und eine Fertigungsliste.");
            if (data.assemblyDefinitions.stream().anyMatch(PlanningData.AssemblyDefinition::conflictingBreakdown)) {
                throw new IllegalArgumentException("Abweichende Unterstrukturen wiederverwendeter Baugruppen muessen vor der Freigabe geklaert werden.");
            }
            ProcessPlanningRules.validateForRelease(data);
            data.zones = PlanningRules.normalizeZones(data, data.zones);
            data.output = PlanningRules.normalizeOutput(data, data.output);
            for (var previous : revisions.findByProjectIdOrderByNumberDesc(id)) {
                if (previous.getStatus() == ManufacturingStatus.RELEASED) previous.setStatus(ManufacturingStatus.OBSOLETE);
            }
            current.setStatus(ManufacturingStatus.RELEASED);
            current.setReleasedAt(LocalDateTime.now()); current.setReleasedBy(user());
        } else if (action.equals("revise")) {
            current = createWorkingRevision(project, current);
        } else throw new IllegalArgumentException("Unbekannte Lebenszyklusaktion.");
        touch(project); projects.flush(); revisions.flush();
        return view(project, current);
    }

    @Transactional
    public View duplicate(String id, String name) {
        PlanningProject source = find(id);
        PlanningRevision sourceRevision = revision(source, source.getCurrentRevision());
        PlanningProject copy = new PlanningProject();
        String copyName = name == null || name.isBlank() ? source.getName() + " (Kopie)" : name.trim();
        if (copyName.length() > 255) throw new IllegalArgumentException("Projektname darf maximal 255 Zeichen lang sein.");
        copy.setName(copyName); copy.setCreatedAt(LocalDateTime.now()); copy.setCreatedBy(user()); touch(copy);
        projects.saveAndFlush(copy);
        PlanningRevision initial = new PlanningRevision();
        initial.setProject(copy); initial.setNumber(1); initial.setStatus(ManufacturingStatus.WORKING);
        initial.setSnapshot(sourceRevision.getSnapshot()); initial.setCreatedAt(LocalDateTime.now()); initial.setCreatedBy(user());
        revisions.saveAndFlush(initial);
        return view(copy, initial);
    }

    @Transactional
    public View createProcessGroup(String id, Long version, String name) {
        PlanningProject project = lock(id, version);
        PlanningRevision revision = working(project);
        PlanningData data = decode(revision);
        if (name == null || name.isBlank() || name.trim().length() > 255) throw new IllegalArgumentException("Schrittgruppe benoetigt einen Namen.");
        data.processStepGroups.add(new PlanningData.ProcessStepGroup(UUID.randomUUID().toString(), name.trim(),
                data.processStepGroups.stream().mapToInt(PlanningData.ProcessStepGroup::sequence).max().orElse(0) + 10));
        return persist(project, revision, data);
    }

    @Transactional
    public View renameProcessGroup(String id, Long version, String groupId, String name) {
        PlanningProject project = lock(id, version); PlanningRevision revision = working(project); PlanningData data = decode(revision);
        String updatedName = requireName(name, "Schrittgruppe");
        if (data.processStepGroups.stream().noneMatch(group -> group.id().equals(groupId))) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schrittgruppe nicht gefunden.");
        data.processStepGroups = data.processStepGroups.stream().map(group -> group.id().equals(groupId)
                ? new PlanningData.ProcessStepGroup(group.id(), updatedName, group.sequence()) : group).toList();
        return persist(project, revision, data);
    }

    @Transactional
    public View moveProcessGroup(String id, Long version, String groupId, String direction) {
        PlanningProject project = lock(id, version); PlanningRevision revision = working(project); PlanningData data = decode(revision);
        if (!Set.of("up", "down").contains(direction)) throw new IllegalArgumentException("Richtung muss up oder down sein.");
        List<PlanningData.ProcessStepGroup> ordered = new ArrayList<>(data.processStepGroups.stream()
                .sorted(Comparator.comparingInt(PlanningData.ProcessStepGroup::sequence)).toList());
        int index = java.util.stream.IntStream.range(0, ordered.size()).filter(position -> ordered.get(position).id().equals(groupId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schrittgruppe nicht gefunden."));
        int other = direction.equals("up") ? index - 1 : index + 1;
        if (other >= 0 && other < ordered.size()) Collections.swap(ordered, index, other);
        data.processStepGroups = java.util.stream.IntStream.range(0, ordered.size()).mapToObj(position -> new PlanningData.ProcessStepGroup(
                ordered.get(position).id(), ordered.get(position).name(), (position + 1) * 10)).toList();
        return persist(project, revision, data);
    }

    @Transactional
    public View deleteProcessGroup(String id, Long version, String groupId) {
        PlanningProject project = lock(id, version); PlanningRevision revision = working(project); PlanningData data = decode(revision);
        if (data.processUses.stream().anyMatch(use -> use.stepGroupId().equals(groupId))) throw new IllegalArgumentException("Schrittgruppe hat noch Prozessverwendungen und kann nicht geloescht werden.");
        data.processStepGroups = data.processStepGroups.stream().filter(group -> !group.id().equals(groupId)).toList();
        return persist(project, revision, data);
    }

    @Transactional
    public View createProcessTemplate(String id, Long version, String name) {
        PlanningProject project = lock(id, version);
        PlanningRevision revision = working(project);
        PlanningData data = decode(revision);
        String templateName = requireName(name, "Prozessname");
        String templateId = UUID.randomUUID().toString();
        data.processTemplates.add(new PlanningData.ProcessTemplateRevision(templateId, 1, templateName, List.of()));
        return persist(project, revision, data);
    }

    @Transactional
    public View deleteProcessTemplate(String id, Long version, String templateId) {
        PlanningProject project = lock(id, version); PlanningRevision revision = working(project); PlanningData data = decode(revision);
        if (data.processUses.stream().anyMatch(use -> use.templateId().equals(templateId))) {
            throw new IllegalArgumentException("Prozessvorlage wird noch verwendet. Zuerst alle Verwendungen entfernen.");
        }
        data.processTemplates = data.processTemplates.stream().filter(template -> !template.templateId().equals(templateId)).toList();
        return persist(project, revision, data);
    }

    @Transactional
    public View createProcessUse(String id, Long version, String stepGroupId, String templateId,
                                 String targetErpNumber, String targetRevision) {
        PlanningProject project = lock(id, version);
        PlanningRevision revision = working(project);
        PlanningData data = decode(revision);
        int templateRevision = latestTemplate(data, templateId).revision();
        data.processUses.add(new PlanningData.ProcessUse(UUID.randomUUID().toString(), stepGroupId, templateId,
                templateRevision, value(targetErpNumber), value(targetRevision)));
        ProcessPlanningRules.validate(data);
        return persist(project, revision, data);
    }

    @Transactional
    public View copyProcessPlan(String id, Long version, String sourceErpNumber, String sourceRevision,
                                String targetErpNumber, String targetRevision) {
        PlanningProject project = lock(id, version);
        PlanningRevision revision = working(project);
        PlanningData data = decode(revision);
        String sourceNumber = value(sourceErpNumber);
        String sourceRev = value(sourceRevision);
        String targetNumber = value(targetErpNumber);
        String targetRev = value(targetRevision);
        if (sourceNumber.isBlank() || targetNumber.isBlank() || (sourceNumber.equals(targetNumber) && sourceRev.equals(targetRev))) {
            throw new IllegalArgumentException("Quell- und Zielbaugruppe muessen verschieden und vollstaendig ausgewaehlt sein.");
        }
        Set<String> articleIds = PlanningRules.articleOptions(data).stream()
                .map(article -> article.number() + "\u0000" + article.revision()).collect(java.util.stream.Collectors.toSet());
        if (!articleIds.contains(sourceNumber + "\u0000" + sourceRev) || !articleIds.contains(targetNumber + "\u0000" + targetRev)) {
            throw new IllegalArgumentException("Quell- und Zielbaugruppe muessen im Artikelstamm vorhanden sein.");
        }
        List<PlanningData.ProcessUse> sourceUses = data.processUses.stream()
                .filter(use -> use.targetErpNumber().equals(sourceNumber) && use.targetRevision().equals(sourceRev)).toList();
        if (sourceUses.isEmpty()) throw new IllegalArgumentException("Fuer die Quellbaugruppe sind keine Taetigkeiten zugeordnet.");
        data.processUses = new ArrayList<>(data.processUses.stream()
            .filter(use -> !use.targetErpNumber().equals(targetNumber) || !use.targetRevision().equals(targetRev)).toList());
        for (var use : sourceUses) {
            data.processUses.add(new PlanningData.ProcessUse(UUID.randomUUID().toString(), use.stepGroupId(), use.templateId(),
                    use.templateRevision(), targetNumber, targetRev));
        }
        ProcessPlanningRules.validate(data);
        return persist(project, revision, data);
    }

    @Transactional
    public View reviseProcessTemplate(String id, Long version, String templateId, Integer expectedRevision,
                                      String changeScope, String name, List<PlanningData.OperationStep> operations,
                                      List<String> affectedUseIds) {
        PlanningProject project = lock(id, version);
        PlanningRevision revision = working(project);
        PlanningData data = decode(revision);
        PlanningData.ProcessTemplateRevision current = data.processTemplates.stream().filter(template -> template.templateId().equals(templateId)
            && expectedRevision != null && template.revision() == expectedRevision).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Prozessvorlagenrevision wurde zwischenzeitlich geaendert. Bitte neu laden."));
        if (expectedRevision == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Prozessvorlage wurde zwischenzeitlich geaendert. Bitte neu laden.");
        }
        ProcessPlanningRules.validateOperations(operations);
        Set<String> selected = new HashSet<>(affectedUseIds == null ? List.of() : affectedUseIds);
        List<PlanningData.ProcessUse> uses = data.processUses.stream().filter(use -> use.templateId().equals(templateId)
            && use.templateRevision() == current.revision()).toList();
        boolean unusedSharedTemplate = uses.isEmpty() && selected.isEmpty() && "SHARED".equals(changeScope);
        if (!uses.stream().map(PlanningData.ProcessUse::id).collect(java.util.stream.Collectors.toSet()).containsAll(selected)
            || (selected.isEmpty() && !unusedSharedTemplate)) {
            throw new IllegalArgumentException("Mindestens eine betroffene Prozessverwendung muss ausgewaehlt werden.");
        }
        String processName = requireName(name, "Prozessname");
        if ("LOCAL".equals(changeScope)) {
            if (selected.size() != 1) throw new IllegalArgumentException("Eine lokale Aenderung betrifft genau eine Verwendungsstelle.");
            String localId = UUID.randomUUID().toString();
            data.processTemplates.add(new PlanningData.ProcessTemplateRevision(localId, 1, processName + " (lokal)", List.copyOf(operations)));
            data.processUses = data.processUses.stream().map(use -> selected.contains(use.id())
                    ? new PlanningData.ProcessUse(use.id(), use.stepGroupId(), localId, 1, use.targetErpNumber(), use.targetRevision()) : use).toList();
        } else if ("SHARED".equals(changeScope)) {
                int nextRevision = data.processTemplates.stream().filter(template -> template.templateId().equals(templateId))
                    .mapToInt(PlanningData.ProcessTemplateRevision::revision).max().orElse(current.revision()) + 1;
            data.processTemplates.add(new PlanningData.ProcessTemplateRevision(templateId, nextRevision, processName, List.copyOf(operations)));
            data.processUses = data.processUses.stream().map(use -> selected.contains(use.id())
                    ? new PlanningData.ProcessUse(use.id(), use.stepGroupId(), templateId, nextRevision, use.targetErpNumber(), use.targetRevision()) : use).toList();
        } else throw new IllegalArgumentException("Aenderungsumfang muss LOCAL oder SHARED sein.");
        ProcessPlanningRules.validate(data);
        return persist(project, revision, data);
    }

    @Transactional
    public View deleteProcessUse(String id, Long version, String useId) {
        PlanningProject project = lock(id, version);
        PlanningRevision revision = working(project);
        PlanningData data = decode(revision);
        data.processUses = data.processUses.stream().filter(use -> !use.id().equals(useId)).toList();
        return persist(project, revision, data);
    }

    private void requireDeleteIfRemoving(List<Map<String, String>> previous, List<Map<String, String>> rows) {
        if (rows == null) return;
        Set<String> incomingIds = new HashSet<>();
        rows.stream().filter(Objects::nonNull).forEach(row -> incomingIds.add(row.get("_rowId")));
        boolean removing = rows.size() < previous.size() || previous.stream()
                .anyMatch(row -> row.get("_rowId") != null && !incomingIds.contains(row.get("_rowId")));
        if (removing && SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().noneMatch(authority -> authority.getAuthority().equals("DELETE"))) {
            throw new AccessDeniedException("DELETE-Berechtigung zum Entfernen von Zeilen erforderlich.");
        }
    }

    private void requireConfirmation(boolean existing, boolean confirmed) {
        if (existing && !confirmed) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Das Ersetzen vorhandener Planungsdaten muss ausdruecklich bestaetigt werden.");
    }

    private View persist(PlanningProject project, PlanningRevision revision, PlanningData data) {
        revision.setSnapshot(encode(data)); touch(project); projects.flush(); revisions.flush();
        return view(project, revision);
    }

    private PlanningProject lock(String id, Long version) {
        PlanningProject project = projects.findLocked(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (version == null || !version.equals(project.getVersion())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Projekt wurde zwischenzeitlich geaendert. Bitte neu laden.");
        return project;
    }

    private PlanningProject find(String id) {
        return projects.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private PlanningRevision revision(PlanningProject project, int number) {
        return revisions.findByProjectIdAndNumber(project.getId(), number).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private PlanningRevision working(PlanningProject project) {
        PlanningRevision revision = revision(project, project.getCurrentRevision());
        if (revision.getStatus() != ManufacturingStatus.WORKING) throw new ResponseStatusException(HttpStatus.CONFLICT, "Freigegebene Staende sind schreibgeschuetzt. Neue Revision erstellen.");
        return revision;
    }

    private PlanningRevision createWorkingRevision(PlanningProject project, PlanningRevision source) {
        PlanningRevision next = new PlanningRevision();
        next.setProject(project); next.setNumber(source.getNumber() + 1); next.setSnapshot(source.getSnapshot());
        next.setStatus(ManufacturingStatus.WORKING); next.setCreatedAt(LocalDateTime.now()); next.setCreatedBy(user());
        revisions.saveAndFlush(next);
        project.setCurrentRevision(next.getNumber());
        touch(project);
        return next;
    }

    private View view(PlanningProject project, PlanningRevision revision) {
        PlanningData data = decode(revision);
        List<Map<String, String>> articleRows = articleRows(data);
        List<Map<String, String>> workplaces = workplaceRows(data);
        return new View(info(project, revision), data, revisions.findByProjectIdOrderByNumberDesc(project.getId()).stream()
                .map(item -> new RevisionInfo(item.getNumber(), item.getStatus(), item.getCreatedAt(), item.getCreatedBy(), item.getReleasedAt(), item.getReleasedBy())).toList(),
                PlanningRules.parts(data), PlanningRules.articleOptions(data), articleRows, workplaces,
                PlanningRules.ARTICLE_HEADERS, PlanningRules.WORKPLACE_HEADERS, PlanningRules.ZONE_HEADERS, PlanningRules.OUTPUT_HEADERS,
                processPlan(data));
    }

    private ProcessPlan processPlan(PlanningData data) {
        Map<String, Long> usageCount = data.processUses.stream().collect(java.util.stream.Collectors.groupingBy(
            use -> use.templateId() + "\u0000" + use.templateRevision(), java.util.stream.Collectors.counting()));
        Map<String, Integer> latestRevisions = data.processTemplates.stream().collect(java.util.stream.Collectors.groupingBy(
            PlanningData.ProcessTemplateRevision::templateId, java.util.stream.Collectors.collectingAndThen(
                java.util.stream.Collectors.maxBy(Comparator.comparingInt(PlanningData.ProcessTemplateRevision::revision)),
                item -> item.orElseThrow().revision())));
        List<ProcessTemplateSummary> summaries = data.processTemplates.stream().sorted(Comparator.comparing(PlanningData.ProcessTemplateRevision::name)
            .thenComparingInt(PlanningData.ProcessTemplateRevision::revision)).map(template -> new ProcessTemplateSummary(
            template.templateId(), template.revision(), latestRevisions.get(template.templateId()), template.name(),
            Math.toIntExact(usageCount.getOrDefault(template.templateId() + "\u0000" + template.revision(), 0L)), template.operations())).toList();
        List<PlanningData.WorkplaceLoad> balance = ProcessPlanningRules.balance(data);
        return new ProcessPlan(data.processStepGroups, summaries, data.processUses, balance,
            ProcessPlanningRules.consistencyIssues(data));
    }

    private PlanningData.ProcessTemplateRevision latestTemplate(PlanningData data, String templateId) {
        return data.processTemplates.stream().filter(template -> template.templateId().equals(templateId))
                .max(Comparator.comparingInt(PlanningData.ProcessTemplateRevision::revision))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prozessvorlage nicht gefunden."));
    }

    private String requireName(String name, String field) {
        if (name == null || name.isBlank() || name.trim().length() > 255) throw new IllegalArgumentException(field + " benoetigt einen Namen (maximal 255 Zeichen).");
        return name.trim();
    }

    private String value(String value) { return value == null ? "" : value; }

    private List<Map<String, String>> articleRows(PlanningData data) {
        return data.articleMasters.stream().map(article -> {
            Map<String, String> row = PlanningData.emptyRow(PlanningRules.ARTICLE_HEADERS.size());
            row.put("_rowId", UUID.nameUUIDFromBytes((article.customerErpNumber() + "\u0000" + article.customerRevision()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
            row.put("A", article.customerErpNumber()); row.put("B", article.customerRevision());
            row.put("C", article.boschSapNumber()); row.put("D", article.boschSapRevision()); row.put("E", article.description());
            row.put("F", article.missingMasterData() ? "FEHLENDE_STAMMDATEN" : "VOLLSTAENDIG"); row.put("G", Integer.toString(article.sourceRows().size()));
            return row;
        }).toList();
    }

    private List<Map<String, String>> workplaceRows(PlanningData data) {
        return data.workplaces.stream().map(workplace -> {
            Map<String, String> row = PlanningData.emptyRow(PlanningRules.WORKPLACE_HEADERS.size());
            row.put("_rowId", workplace.id()); row.put("A", workplace.zoneNumber()); row.put("B", workplace.code());
            row.put("C", workplace.name()); row.put("D", workplace.taktMinutes());
            row.put("E", data.zones.stream().filter(zone -> zone.get("A").equals(workplace.zoneNumber())).map(zone -> zone.get("B")).findFirst().orElse(""));
            return row;
        }).toList();
    }

    private ProjectInfo info(PlanningProject project, PlanningRevision revision) {
        return new ProjectInfo(project.getId(), project.getName(), project.getVersion(), revision.getNumber(), revision.getStatus(), project.getModifiedAt(), project.getModifiedBy());
    }

    private void touch(PlanningProject project) { project.setModifiedAt(LocalDateTime.now()); project.setModifiedBy(user()); }
    private String user() { return SecurityContextHolder.getContext().getAuthentication().getName(); }
    private String encode(PlanningData data) {
        try { return mapper.writeValueAsString(data); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Planung konnte nicht gespeichert werden.", exception); }
    }
    private PlanningData decode(PlanningRevision revision) {
        try { return mapper.readValue(revision.getSnapshot(), PlanningData.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Planungsstand konnte nicht gelesen werden.", exception); }
    }
}