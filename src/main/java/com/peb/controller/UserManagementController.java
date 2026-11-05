// src/main/java/com/peb/controller/UserManagementController.java
package com.peb.controller;

import com.peb.model.PebUser;
import com.peb.service.UserManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Steuert die Benutzerverwaltungs-UI (/admin/users) und
 * die REST-API (/api/admin/users) für programmatischen Zugriff.
 */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class UserManagementController {

    private static final List<String> AVAILABLE_ROLES = List.of(
            "ROLE_ADMIN", "ROLE_USER", "ROLE_VIEWER");

    private final UserManagementService userService;

    public UserManagementController(UserManagementService userService) {
        this.userService = userService;
    }

    // ════════════════════════════════════════════════════════
    //  UI-Endpoints (Thymeleaf)
    // ════════════════════════════════════════════════════════

    /** Hauptseite Benutzerverwaltung */
    @GetMapping("/admin/users")
    public String userList(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            Model model) {

        List<PebUser> users;

        if (search != null && !search.isBlank()) {
            users = userService.searchUsers(search);
        } else if ("PENDING".equals(status)) {
            users = userService.getPendingUsers();
        } else {
            users = userService.getAllUsers();
        }

        model.addAttribute("users",          users);
        model.addAttribute("search",         search);
        model.addAttribute("filterStatus",   status);
        model.addAttribute("availableRoles", AVAILABLE_ROLES);
        model.addAttribute("pendingCount",
                userService.countByStatus(PebUser.Status.PENDING));
        model.addAttribute("activeCount",
                userService.countByStatus(PebUser.Status.ACTIVE));
        model.addAttribute("inactiveCount",
                userService.countByStatus(PebUser.Status.INACTIVE));

        return "admin/users";
    }

    /** Formular: neuen Benutzer anlegen */
    @GetMapping("/admin/users/new")
    public String newUserForm(Model model) {
        model.addAttribute("user",           new PebUser());
        model.addAttribute("availableRoles", AVAILABLE_ROLES);
        model.addAttribute("isNew",          true);
        return "admin/user-form";
    }

    /** Formular: Benutzer bearbeiten */
    @GetMapping("/admin/users/{id}/edit")
    public String editUserForm(@PathVariable Long id, Model model) {
        PebUser user = userService.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        model.addAttribute("user",           user);
        model.addAttribute("availableRoles", AVAILABLE_ROLES);
        model.addAttribute("isNew",          false);
        return "admin/user-form";
    }

    /** Speichern (neu + bearbeiten) */
    @PostMapping("/admin/users/save")
    public String saveUser(
            @RequestParam(required = false) Long    id,
            @RequestParam                   String  username,
            @RequestParam                   String  displayName,
            @RequestParam                   String  email,
            @RequestParam                   String  role,
            @RequestParam                   String  status,
            @RequestParam(required = false)  String  notes,
            Principal admin,
            RedirectAttributes redirectAttrs) {

        try {
            if (id == null) {
                // Neu anlegen
                userService.createUser(username, displayName, email,
                                       role, admin.getName());
                redirectAttrs.addFlashAttribute("successMessage",
                        "Benutzer '" + username + "' erfolgreich angelegt.");
            } else {
                // Bearbeiten
                userService.updateUser(id, displayName, email, role,
                        PebUser.Status.valueOf(status), notes);
                redirectAttrs.addFlashAttribute("successMessage",
                        "Benutzer erfolgreich aktualisiert.");
            }
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/users";
    }

    /** Benutzer aktivieren (Quick-Action) */
    @PostMapping("/admin/users/{id}/activate")
    public String activate(@PathVariable Long id,
                           Principal admin,
                           RedirectAttributes redirectAttrs) {
        PebUser user = userService.activateUser(id, admin.getName());
        redirectAttrs.addFlashAttribute("successMessage",
                "Benutzer '" + user.getUsername() + "' wurde aktiviert.");
        return "redirect:/admin/users";
    }

    /** Benutzer deaktivieren (Quick-Action) */
    @PostMapping("/admin/users/{id}/deactivate")
    public String deactivate(@PathVariable Long id,
                             Principal admin,
                             RedirectAttributes redirectAttrs) {
        PebUser user = userService.deactivateUser(id, admin.getName());
        redirectAttrs.addFlashAttribute("successMessage",
                "Benutzer '" + user.getUsername() + "' wurde deaktiviert.");
        return "redirect:/admin/users";
    }

    /** Benutzer löschen */
    @PostMapping("/admin/users/{id}/delete")
    public String delete(@PathVariable Long id,
                         Principal admin,
                         RedirectAttributes redirectAttrs) {
        try {
            userService.deleteUser(id, admin.getName());
            redirectAttrs.addFlashAttribute("successMessage",
                    "Benutzer wurde gelöscht.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }
	
	@GetMapping("/api/admin/users/pending-count")
	@ResponseBody
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Map<String, Long>> getPendingCount() {
		return ResponseEntity.ok(Map.of(
				"count", userService.countByStatus(PebUser.Status.PENDING)));
	}

    // ════════════════════════════════════════════════════════
    //  REST-API (für programmatische Nutzung)
    // ════════════════════════════════════════════════════════

    @GetMapping("/api/admin/users")
    @ResponseBody
    public ResponseEntity<List<PebUser>> apiGetUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/api/admin/users/{id}/department")
    @ResponseBody
    public ResponseEntity<?> apiGetUserDepartment(@PathVariable Long id) {
        return userService.findById(id)
                .<ResponseEntity<?>>map(user -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("id", user.getId());
                    payload.put("username", user.getUsername());
                    payload.put("department", user.getDepartment());
                    return ResponseEntity.ok(payload);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/api/admin/users/{id}/role")
    @ResponseBody
    public ResponseEntity<?> apiUpdateRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Principal admin) {
        try {
            PebUser user = userService.updateRole(
                    id, body.get("role"), admin.getName());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "username", user.getUsername(),
                    "newRole", user.getRole()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}