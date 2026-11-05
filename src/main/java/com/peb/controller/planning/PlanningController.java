package com.peb.controller.planning;

import com.peb.service.planning.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

@Controller
@RequestMapping("/manufacturing-planning")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('READ')")
public class PlanningController {
    private final PlanningProjectService service;
    private final PlanningExcelService excel;
    public record CreateRequest(String name) {}
    public record CopyRequest(String name) {}
    public record VersionRequest(Long version, boolean confirmReplace) {}
    public record TableRequest(Long version, List<Map<String, String>> rows, boolean confirmOutputClear) {}
    public record ProcessGroupRequest(Long version, String name) {}
    public record ProcessTemplateRequest(Long version, String name) {}
    public record ProcessGroupUpdateRequest(Long version, String name) {}
    public record ProcessGroupMoveRequest(Long version, String direction) {}
    public record ProcessUseRequest(Long version, String stepGroupId, String templateId,
                                    String targetErpNumber, String targetRevision) {}
    public record CopyProcessPlanRequest(Long version, String sourceErpNumber, String sourceRevision,
                                        String targetErpNumber, String targetRevision) {}
    public record TemplateRevisionRequest(Long version, Integer expectedRevision, String changeScope, String name,
                                          List<com.peb.service.planning.PlanningData.OperationStep> operations,
                                          List<String> affectedUseIds) {}

    @GetMapping
    public String list(Model model) { model.addAttribute("projects", service.list()); return "manufacturing-planning/list"; }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) { model.addAttribute("projectId", service.get(id, null).project().id()); return "manufacturing-planning/detail"; }

    @GetMapping("/{id}/data")
    @ResponseBody
    public PlanningProjectService.View data(@PathVariable String id, @RequestParam(required = false) Integer revision) { return service.get(id, revision); }

    @GetMapping("/{id}/structure-comparison")
    @ResponseBody
    public com.peb.service.planning.PlanningData.StructureComparison compareStructure(@PathVariable String id,
            @RequestParam int previousRevision, @RequestParam int currentRevision) {
        return service.compareStructure(id, previousRevision, currentRevision);
    }

    @GetMapping("/{id}/work-sequence.xlsx")
    public ResponseEntity<byte[]> workSequence(@PathVariable String id, @RequestParam(required = false) Integer revision) throws IOException {
        var sequence = service.releasedWorkSequence(id, revision);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        excel.exportWorkSequence(sequence.data(), sequence.projectName(), sequence.revision(), stream);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Arbeitsfolgekarte_" + id
                + "_R" + sequence.revision() + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(stream.toByteArray());
    }

    @GetMapping("/{id}/process-plan")
    @ResponseBody
    public PlanningProjectService.ProcessPlan processPlan(@PathVariable String id) { return service.get(id, null).processPlan(); }

    @GetMapping("/{id}/reports")
    @ResponseBody
    public Object report(@PathVariable String id, @RequestParam(defaultValue = "materials") String kind,
                         @RequestParam(required = false) String zone, @RequestParam(required = false) String workplace,
                         @RequestParam(required = false) String target, @RequestParam(required = false) String targetRevision,
                         @RequestParam(required = false) Integer revision, @RequestParam(required = false) String state) {
        return service.report(id, new PlanningProjectService.ReportQuery(kind, zone, workplace, target, targetRevision, revision, state));
    }

    @GetMapping("/{id}/reports.xlsx")
    public ResponseEntity<byte[]> reportWorkbook(@PathVariable String id, @RequestParam(defaultValue = "materials") String kind,
                         @RequestParam(required = false) String zone, @RequestParam(required = false) String workplace,
                         @RequestParam(required = false) String target, @RequestParam(required = false) String targetRevision,
                         @RequestParam(required = false) Integer revision, @RequestParam(required = false) String state) throws IOException {
        var report = service.report(id, new PlanningProjectService.ReportQuery(kind, zone, workplace, target, targetRevision, revision, state));
        ByteArrayOutputStream stream = new ByteArrayOutputStream(); excel.exportReport(report, stream);
        String safeKind = kind.replaceAll("[^A-Za-z0-9_-]", "_");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Fertigungsplanung_" + id + "_R"
                + (revision == null ? service.get(id, null).project().revision() : revision) + "_" + safeKind + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(stream.toByteArray());
    }

    @PostMapping("/{id}/process-groups")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public PlanningProjectService.View createProcessGroup(@PathVariable String id, @RequestBody ProcessGroupRequest request) {
        return service.createProcessGroup(id, request.version(), request.name());
    }

    @PutMapping("/{id}/process-groups/{groupId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public PlanningProjectService.View renameProcessGroup(@PathVariable String id, @PathVariable String groupId, @RequestBody ProcessGroupUpdateRequest request) {
        return service.renameProcessGroup(id, request.version(), groupId, request.name());
    }

    @PostMapping("/{id}/process-groups/{groupId}/move")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public PlanningProjectService.View moveProcessGroup(@PathVariable String id, @PathVariable String groupId, @RequestBody ProcessGroupMoveRequest request) {
        return service.moveProcessGroup(id, request.version(), groupId, request.direction());
    }

    @DeleteMapping("/{id}/process-groups/{groupId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE') and hasAuthority('DELETE')")
    public PlanningProjectService.View deleteProcessGroup(@PathVariable String id, @PathVariable String groupId, @RequestParam Long version) {
        return service.deleteProcessGroup(id, version, groupId);
    }

    @PostMapping("/{id}/process-templates")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public PlanningProjectService.View createProcessTemplate(@PathVariable String id, @RequestBody ProcessTemplateRequest request) {
        return service.createProcessTemplate(id, request.version(), request.name());
    }

    @DeleteMapping("/{id}/process-templates/{templateId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE') and hasAuthority('DELETE')")
    public PlanningProjectService.View deleteProcessTemplate(@PathVariable String id, @PathVariable String templateId, @RequestParam Long version) {
        return service.deleteProcessTemplate(id, version, templateId);
    }

    @PostMapping("/{id}/process-uses")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public PlanningProjectService.View createProcessUse(@PathVariable String id, @RequestBody ProcessUseRequest request) {
        return service.createProcessUse(id, request.version(), request.stepGroupId(), request.templateId(), request.targetErpNumber(), request.targetRevision());
    }

    @PostMapping("/{id}/process-uses/copy")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public PlanningProjectService.View copyProcessPlan(@PathVariable String id, @RequestBody CopyProcessPlanRequest request) {
        return service.copyProcessPlan(id, request.version(), request.sourceErpNumber(), request.sourceRevision(),
                request.targetErpNumber(), request.targetRevision());
    }

    @PutMapping("/{id}/process-templates/{templateId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public PlanningProjectService.View reviseProcessTemplate(@PathVariable String id, @PathVariable String templateId,
                                                             @RequestBody TemplateRevisionRequest request) {
        return service.reviseProcessTemplate(id, request.version(), templateId, request.expectedRevision(), request.changeScope(),
                request.name(), request.operations(), request.affectedUseIds());
    }

    @DeleteMapping("/{id}/process-uses/{useId}")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE') and hasAuthority('DELETE')")
    public PlanningProjectService.View deleteProcessUse(@PathVariable String id, @PathVariable String useId, @RequestParam Long version) {
        return service.deleteProcessUse(id, version, useId);
    }

    @PostMapping
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public PlanningProjectService.View create(@RequestBody CreateRequest request) { return service.create(request.name()); }

    @PostMapping("/{id}/copy")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public PlanningProjectService.View duplicate(@PathVariable String id, @RequestBody CopyRequest request) {
        return service.duplicate(id, request.name());
    }

    @PostMapping("/{id}/import")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public PlanningProjectService.View importFile(@PathVariable String id, @RequestParam Long version, @RequestParam MultipartFile file,
                                                  @RequestParam(defaultValue = "false") boolean confirmReplace,
                                                  @RequestParam(defaultValue = "false") boolean newVersion) throws IOException {
        String filename = file.getOriginalFilename();
        if (file.isEmpty() || filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw new IllegalArgumentException("Bitte eine nichtleere .xlsx-Strukturdatei auswaehlen.");
        if (file.getSize() > 20 * 1024 * 1024) throw new IllegalArgumentException("Maximale Dateigroesse: 20 MB.");
        try (InputStream stream = file.getInputStream()) { return service.importStructure(id, version, filename, stream, confirmReplace, newVersion); }
    }

    @PutMapping("/{id}/tables/{table}")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public PlanningProjectService.View save(@PathVariable String id, @PathVariable String table, @RequestBody TableRequest request) {
        return service.saveTable(id, request.version(), table, request.rows(), request.confirmOutputClear());
    }

    @PostMapping("/{id}/generate")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE')")
    public PlanningProjectService.View generate(@PathVariable String id, @RequestBody VersionRequest request) { return service.generate(id, request.version(), request.confirmReplace()); }

    @PostMapping("/{id}/lifecycle/{action}")
    @ResponseBody
    @PreAuthorize("hasAuthority('WRITE') and hasAuthority('EXECUTE')")
    public PlanningProjectService.View lifecycle(@PathVariable String id, @PathVariable String action, @RequestBody VersionRequest request) {
        return service.lifecycle(id, request.version(), action);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable String id, @RequestParam(required = false) Integer revision,
                                         @RequestParam(defaultValue = "both") String numbers) throws IOException {
        var view = service.get(id, revision);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        excel.export(view.data(), stream, numbers);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Fertigungsplanung_" + id + "_R" + view.project().revision() + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(stream.toByteArray());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage() == null ? "Ungueltige Excel-Datei oder Eingabe." : exception.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> invalidFile(IOException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", "Excel-Datei konnte nicht verarbeitet werden."));
    }
}