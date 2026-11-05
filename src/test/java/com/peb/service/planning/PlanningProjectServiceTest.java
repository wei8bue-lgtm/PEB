package com.peb.service.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peb.model.planning.*;
import com.peb.model.product.ManufacturingStatus;
import com.peb.repository.planning.*;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

class PlanningProjectServiceTest {
    @Test
    void versionsReusableProcessesWithLocalAndSelectedSharedChanges() {
    var configuration = new Configuration().addAnnotatedClass(PlanningProject.class).addAnnotatedClass(PlanningRevision.class);
    configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
    configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:planning_process;MODE=Oracle;DB_CLOSE_DELAY=-1");
    configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("planner", "unused", "READ", "WRITE", "DELETE", "EXECUTE"));
    try (var factory = configuration.buildSessionFactory(); var manager = factory.createEntityManager()) {
        var repositories = new JpaRepositoryFactory(manager);
        var service = new PlanningProjectService(repositories.getRepository(PlanningProjectRepository.class),
            repositories.getRepository(PlanningRevisionRepository.class), new PlanningExcelService(), new ObjectMapper().findAndRegisterModules(), new PlanningValidationService());
        var project = transaction(manager, () -> service.create("Prozessvorlagen"));
        var grouped = transaction(manager, () -> service.createProcessGroup(project.project().id(), project.project().version(), "Montage"));
        var templated = transaction(manager, () -> service.createProcessTemplate(project.project().id(), grouped.project().version(), "Verschrauben"));
        var templateId = templated.data().processTemplates.get(0).templateId();
        var useOne = transaction(manager, () -> service.createProcessUse(project.project().id(), templated.project().version(),
            grouped.data().processStepGroups.get(0).id(), templateId, "", ""));
        String firstUse = useOne.data().processUses.get(0).id();
        var useTwo = transaction(manager, () -> service.createProcessUse(project.project().id(), useOne.project().version(),
            grouped.data().processStepGroups.get(0).id(), templateId, "", ""));
        String secondUse = useTwo.data().processUses.stream().filter(use -> !use.id().equals(firstUse)).findFirst().orElseThrow().id();
            assertThat(useTwo.processPlan().templates()).singleElement().satisfies(template -> assertThat(template.usageCount()).isEqualTo(2));

        var resource = new PlanningData.ResourceRequirement("resource-1", PlanningData.ResourceType.TOOL,
            "TOOL-1", "A", "Drehmomentschluessel", java.math.BigDecimal.ONE, "Stk");
        var step = new PlanningData.OperationStep("step-1", "Schraube anziehen", "Sollmoment beachten", 10, 2,
            java.math.BigDecimal.valueOf(12), java.math.BigDecimal.valueOf(8), "MTM", "MTM-1", null, "", List.of(resource));
        var local = transaction(manager, () -> service.reviseProcessTemplate(project.project().id(), useTwo.project().version(),
            templateId, 1, "LOCAL", "Verschrauben", List.of(step), List.of(firstUse)));
        var localUse = local.data().processUses.stream().filter(use -> use.id().equals(firstUse)).findFirst().orElseThrow();
        var sharedUseBeforeChange = local.data().processUses.stream().filter(use -> use.id().equals(secondUse)).findFirst().orElseThrow();
        assertThat(localUse.templateId()).isNotEqualTo(templateId);
        assertThat(sharedUseBeforeChange.templateId()).isEqualTo(templateId);
            assertThat(local.processPlan().templates().stream().filter(template -> template.templateId().equals(templateId)).findFirst().orElseThrow().usageCount()).isEqualTo(1);

        var shared = transaction(manager, () -> service.reviseProcessTemplate(project.project().id(), local.project().version(),
            templateId, 1, "SHARED", "Verschrauben standardisiert", List.of(step), List.of(secondUse)));
        assertThat(shared.data().processUses).filteredOn(use -> use.id().equals(secondUse)).singleElement()
            .satisfies(use -> { assertThat(use.templateId()).isEqualTo(templateId); assertThat(use.templateRevision()).isEqualTo(2); });
        assertThat(shared.data().processUses).filteredOn(use -> use.id().equals(firstUse)).singleElement()
            .satisfies(use -> assertThat(use.templateId()).isNotEqualTo(templateId));
        var savedTemplate = shared.data().processTemplates.stream().filter(template -> template.templateId().equals(localUse.templateId())).findFirst().orElseThrow();
        assertThat(savedTemplate.operations()).singleElement().satisfies(operation -> {
        assertThat(operation.executionsPerUnit()).isEqualTo(2);
        assertThat(operation.resources()).hasSize(1);
        });
    } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void startsWithoutBomAndDuplicatesMastersZonesWorkplacesAndPlan() throws Exception {
        var configuration = new Configuration().addAnnotatedClass(PlanningProject.class).addAnnotatedClass(PlanningRevision.class);
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:planning_copy;MODE=Oracle;DB_CLOSE_DELAY=-1");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("planner", "unused", "READ", "WRITE", "DELETE", "EXECUTE"));
        try (var factory = configuration.buildSessionFactory(); var manager = factory.createEntityManager()) {
            var repositories = new JpaRepositoryFactory(manager);
            var service = new PlanningProjectService(repositories.getRepository(PlanningProjectRepository.class),
                    repositories.getRepository(PlanningRevisionRepository.class), new PlanningExcelService(), new ObjectMapper().findAndRegisterModules(), new PlanningValidationService());
            var project = transaction(manager, () -> service.create("Vorplanung ohne BOM"));
            var article = PlanningData.emptyRow(7);
            article.put("A", "001234"); article.put("B", "A"); article.put("C", "50001234");
            article.put("D", "01"); article.put("E", "Manueller Artikel");
            var withArticle = transaction(manager, () -> service.saveTable(project.project().id(), project.project().version(), "articles", List.of(article), false));
            assertThat(withArticle.data().structure).isEmpty();
            var zone = PlanningData.emptyRow(36);
            zone.put("A", "1"); zone.put("B", "Montage"); zone.put("D", "001234"); zone.put("G", "A"); zone.put("J", "2");
            var withZone = transaction(manager, () -> service.saveTable(project.project().id(), withArticle.project().version(), "zones", List.of(zone), false));
            var workplace = PlanningData.emptyRow(5);
            workplace.put("A", "1"); workplace.put("B", "AP-01"); workplace.put("C", "Montageplatz"); workplace.put("D", "12");
            var planned = transaction(manager, () -> service.saveTable(project.project().id(), withZone.project().version(), "workplaces", List.of(workplace), false));
            var generated = transaction(manager, () -> service.generate(project.project().id(), planned.project().version(), false));
            assertThat(generated.data().output).hasSize(1);
            var copy = transaction(manager, () -> service.duplicate(project.project().id(), "Vorplanung Kopie"));
            assertThat(copy.project().id()).isNotEqualTo(project.project().id());
            assertThat(copy.data().structure).isEmpty();
            assertThat(copy.data().articleMasters).singleElement().satisfies(master -> {
                assertThat(master.customerErpNumber()).isEqualTo("001234");
                assertThat(master.boschSapNumber()).isEqualTo("50001234");
            });
            assertThat(copy.data().zones).hasSize(1);
            assertThat(copy.data().workplaces).singleElement().satisfies(saved -> assertThat(saved.zoneNumber()).isEqualTo("1"));
            assertThat(copy.data().output).hasSize(1);
        } finally { SecurityContextHolder.clearContext(); }
    }

        @Test
        void importsBomAsNewWorkingRevisionAndComparesRows() throws Exception {
            var configuration = new Configuration().addAnnotatedClass(PlanningProject.class).addAnnotatedClass(PlanningRevision.class);
            configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
            configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:planning_bom_revision;MODE=Oracle;DB_CLOSE_DELAY=-1");
            configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("planner", "unused", "READ", "WRITE", "DELETE", "EXECUTE"));
            try (var factory = configuration.buildSessionFactory(); var manager = factory.createEntityManager()) {
                var repositories = new JpaRepositoryFactory(manager);
                var service = new PlanningProjectService(repositories.getRepository(PlanningProjectRepository.class),
                        repositories.getRepository(PlanningRevisionRepository.class), new PlanningExcelService(), new ObjectMapper().findAndRegisterModules(), new PlanningValidationService());
                var created = transaction(manager, () -> service.create("Musterstückliste"));
                var first = transaction(manager, () -> {
                    try { return service.importStructure(created.project().id(), created.project().version(), "BOM.xlsx",
                            new ByteArrayInputStream(bomWorkbook("200", "Teil alt")), false); }
                    catch (Exception exception) { throw new IllegalStateException(exception); }
                });
                var branch = transaction(manager, () -> service.lifecycle(first.project().id(), first.project().version(), "revise"));
                assertThat(branch.project().revision()).isEqualTo(2);
                assertThat(branch.project().status()).isEqualTo(ManufacturingStatus.WORKING);
                var imported = transaction(manager, () -> {
                    try { return service.importStructure(branch.project().id(), branch.project().version(), "BOM-neu.xlsx",
                            new ByteArrayInputStream(bomWorkbook("300", "Teil neu")), false, true); }
                    catch (Exception exception) { throw new IllegalStateException(exception); }
                });
                assertThat(imported.project().revision()).isEqualTo(3);
                assertThat(imported.project().status()).isEqualTo(ManufacturingStatus.WORKING);
                    assertThatThrownBy(() -> transaction(manager, () -> service.releasedWorkSequence(created.project().id(), 3)))
                        .isInstanceOf(ResponseStatusException.class).hasMessageContaining("nur aus einem freigegebenen Stand");
                assertThat(transaction(manager, () -> service.get(created.project().id(), 1)).data().structure)
                        .extracting(row -> row.cells().get("J")).contains("200");
                var comparison = transaction(manager, () -> service.compareStructure(created.project().id(), 2, 3));
                    assertThat(comparison.rows()).extracting(PlanningData.StructureChangeRow::change).containsExactly("REMOVED", "CHANGED", "ADDED");
            } finally { SecurityContextHolder.clearContext(); }
        }

            @Test
            void copiesTargetActivitiesAndReplacesOnlyDestinationAssignments() {
            var configuration = new Configuration().addAnnotatedClass(PlanningProject.class).addAnnotatedClass(PlanningRevision.class);
            configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
            configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:planning_copy_process;MODE=Oracle;DB_CLOSE_DELAY=-1");
            configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("planner", "unused", "READ", "WRITE", "DELETE", "EXECUTE"));
            try (var factory = configuration.buildSessionFactory(); var manager = factory.createEntityManager()) {
                var repositories = new JpaRepositoryFactory(manager);
                var service = new PlanningProjectService(repositories.getRepository(PlanningProjectRepository.class),
                    repositories.getRepository(PlanningRevisionRepository.class), new PlanningExcelService(), new ObjectMapper().findAndRegisterModules(), new PlanningValidationService());
                var project = transaction(manager, () -> service.create("Taetigkeitskopie"));
                var source = PlanningData.emptyRow(7); source.put("A", "100"); source.put("B", "A"); source.put("C", "SAP100"); source.put("D", "01"); source.put("E", "Quelle");
                var target = PlanningData.emptyRow(7); target.put("A", "200"); target.put("B", "B"); target.put("C", "SAP200"); target.put("D", "01"); target.put("E", "Ziel");
                var articles = transaction(manager, () -> service.saveTable(project.project().id(), project.project().version(), "articles", List.of(source, target), false));
                var group = transaction(manager, () -> service.createProcessGroup(project.project().id(), articles.project().version(), "Montage"));
                var template = transaction(manager, () -> service.createProcessTemplate(project.project().id(), group.project().version(), "Verschrauben"));
                String groupId = group.data().processStepGroups.get(0).id();
                String templateId = template.data().processTemplates.get(0).templateId();
                var sourceUse = transaction(manager, () -> service.createProcessUse(project.project().id(), template.project().version(), groupId, templateId, "100", "A"));
                var destinationUse = transaction(manager, () -> service.createProcessUse(project.project().id(), sourceUse.project().version(), groupId, templateId, "200", "B"));
                String oldDestinationUseId = destinationUse.data().processUses.stream().filter(use -> use.targetErpNumber().equals("200"))
                    .findFirst().orElseThrow().id();
                var copied = transaction(manager, () -> service.copyProcessPlan(project.project().id(), destinationUse.project().version(), "100", "A", "200", "B"));
                assertThat(copied.data().processUses).hasSize(2);
                assertThat(copied.data().processUses).filteredOn(use -> use.targetErpNumber().equals("100")).singleElement()
                    .satisfies(use -> assertThat(use.id()).isEqualTo(sourceUse.data().processUses.get(0).id()));
                assertThat(copied.data().processUses).filteredOn(use -> use.targetErpNumber().equals("200")).singleElement()
                    .satisfies(use -> { assertThat(use.id()).isNotEqualTo(oldDestinationUseId); assertThat(use.templateId()).isEqualTo(templateId); });
            } finally { SecurityContextHolder.clearContext(); }
            }

        private byte[] bomWorkbook(String childNumber, String childName) throws Exception {
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
                var sheet = workbook.createSheet("BOM");
                var header = sheet.createRow(0); header.createCell(0).setCellValue("Ebene 0"); header.createCell(1).setCellValue("Ebene 1");
                header.createCell(9).setCellValue("ERP_Nr"); header.createCell(10).setCellValue("Beschreibung");
                header.createCell(12).setCellValue("Revision"); header.createCell(13).setCellValue("Menge");
                var root = sheet.createRow(1); root.createCell(0).setCellValue("Assembly"); root.createCell(9).setCellValue("100");
                root.createCell(10).setCellValue("Musterbaugruppe"); root.createCell(12).setCellValue("A"); root.createCell(13).setCellValue("1");
                var child = sheet.createRow(2); child.createCell(1).setCellValue("Part"); child.createCell(9).setCellValue(childNumber);
                child.createCell(10).setCellValue(childName); child.createCell(12).setCellValue("A"); child.createCell(13).setCellValue("2");
                var changed = sheet.createRow(3); changed.createCell(1).setCellValue("Part"); changed.createCell(9).setCellValue("400");
                changed.createCell(10).setCellValue(childName.equals("Teil alt") ? "Drehteil alt" : "Drehteil neu");
                changed.createCell(12).setCellValue("B"); changed.createCell(13).setCellValue(childName.equals("Teil alt") ? "1" : "3");
                workbook.write(bytes); return bytes.toByteArray();
            }
        }

    @Test
    void persistsCompleteLifecycleWithImmutableSnapshotsAndConflictProtection() throws Exception {
        Path sample = Path.of("docs/fertigungsplanung/Input.xlsx");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(sample));
        var configuration = new Configuration().addAnnotatedClass(PlanningProject.class).addAnnotatedClass(PlanningRevision.class);
        configuration.setProperty("hibernate.connection.driver_class", "org.h2.Driver");
        configuration.setProperty("hibernate.connection.url", "jdbc:h2:mem:planning;MODE=Oracle;DB_CLOSE_DELAY=-1");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("planner", "unused", "READ", "WRITE", "DELETE", "EXECUTE"));
        try (var factory = configuration.buildSessionFactory(); var manager = factory.createEntityManager()) {
            var repositories = new JpaRepositoryFactory(manager);
            var service = new PlanningProjectService(repositories.getRepository(PlanningProjectRepository.class),
                    repositories.getRepository(PlanningRevisionRepository.class), new PlanningExcelService(), new ObjectMapper().findAndRegisterModules(), new PlanningValidationService());
            var created = transaction(manager, () -> service.create("Robot Planung"));
            String id = created.project().id();
            PlanningProjectService.View imported;
            try (var stream = Files.newInputStream(sample)) {
                imported = transaction(manager, () -> {
                    try { return service.importStructure(id, created.project().version(), "Input.xlsx", stream, false); }
                    catch (Exception exception) { throw new IllegalStateException(exception); }
                });
            }
            assertThat(imported.data().sourceSha256).hasSize(64);
            var zone = PlanningData.emptyRow(36);
            zone.put("A", "1"); zone.put("B", "Endmontage"); zone.put("D", "010031000010");
                var configured = transaction(manager, () -> service.saveTable(id, imported.project().version(), "zones", List.of(zone), false));
                    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("planner", "unused", "READ", "WRITE", "EXECUTE"));
                    assertThatThrownBy(() -> transaction(manager, () -> service.saveTable(id, configured.project().version(), "zones", List.of(zone), false)))
                        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("planner", "unused", "READ", "WRITE", "DELETE", "EXECUTE"));
                var generated = transaction(manager, () -> service.generate(id, configured.project().version(), false));
            assertThat(generated.data().output).isNotEmpty();
                assertThatThrownBy(() -> transaction(manager, () -> service.generate(id, generated.project().version(), false)))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("bestaetigt");
            var released = transaction(manager, () -> service.lifecycle(id, generated.project().version(), "release"));
            assertThat(released.project().status()).isEqualTo(ManufacturingStatus.RELEASED);
            assertThatThrownBy(() -> transaction(manager, () -> service.generate(id, released.project().version(), true)))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("schreibgeschuetzt");
            var revised = transaction(manager, () -> service.lifecycle(id, released.project().version(), "revise"));
            assertThat(revised.project().revision()).isEqualTo(2);
            assertThat(revised.project().status()).isEqualTo(ManufacturingStatus.WORKING);
            assertThatThrownBy(() -> transaction(manager, () -> service.generate(id, released.project().version(), true)))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("zwischenzeitlich");
            var changedZone = PlanningData.emptyRow(36);
            changedZone.putAll(revised.data().zones.get(0)); changedZone.put("B", "Neue Endmontage");
                assertThatThrownBy(() -> transaction(manager, () -> service.saveTable(id, revised.project().version(), "zones", List.of(changedZone), false)))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("bestaetigt");
                var changed = transaction(manager, () -> service.saveTable(id, revised.project().version(), "zones", List.of(changedZone), true));
            assertThat(changed.data().output).isEmpty();
            var historical = transaction(manager, () -> service.get(id, 1));
            assertThat(historical.data().zones.get(0).get("B")).isEqualTo("Endmontage");
            assertThat(historical.data().output).isNotEmpty();
            var nextOutput = transaction(manager, () -> service.generate(id, changed.project().version(), false));
            var nextRelease = transaction(manager, () -> service.lifecycle(id, nextOutput.project().version(), "release"));
            var workSequence = transaction(manager, () -> service.releasedWorkSequence(id, nextRelease.project().revision()));
            assertThat(workSequence.revision()).isEqualTo(nextRelease.project().revision());
            assertThat(nextRelease.revisions()).anySatisfy(revision -> {
                assertThat(revision.number()).isEqualTo(1); assertThat(revision.status()).isEqualTo(ManufacturingStatus.OBSOLETE);
            });
        } finally { SecurityContextHolder.clearContext(); }
    }

    private <Result> Result transaction(jakarta.persistence.EntityManager manager, Supplier<Result> action) {
        manager.getTransaction().begin();
        try { Result result = action.get(); manager.getTransaction().commit(); manager.clear(); return result; }
        catch (RuntimeException exception) { manager.getTransaction().rollback(); manager.clear(); throw exception; }
    }
}