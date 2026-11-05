package com.peb.controller;

import com.peb.model.PebUser;
import com.peb.service.UserManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserManagementControllerTest {

    private static final String ADMIN_NAME = "DE\\admin";
    private UserManagementService userService;
    private MockMvc mockMvc;
    private Principal admin;

    @BeforeEach
    void setUp() {
        userService = mock(UserManagementService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new UserManagementController(userService)).build();
        admin = UsernamePasswordAuthenticationToken.authenticated(
                ADMIN_NAME, null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
    }

    @Test
    void createsUserWithStringPrincipal() throws Exception {
        createUser(admin);
    }

    @Test
    void createsUserWithUserDetailsPrincipal() throws Exception {
        Principal userDetailsAdmin = UsernamePasswordAuthenticationToken.authenticated(
                User.withUsername(ADMIN_NAME).password("").roles("ADMIN").build(),
                null, AuthorityUtils.createAuthorityList("ROLE_ADMIN"));
        createUser(userDetailsAdmin);
    }

    @Test
    void activatesUserWithStringPrincipal() throws Exception {
        when(userService.activateUser(1L, ADMIN_NAME)).thenReturn(testUser());

        mockMvc.perform(post("/admin/users/1/activate").principal(admin))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService).activateUser(1L, ADMIN_NAME);
    }

    @Test
    void deactivatesUserWithStringPrincipal() throws Exception {
        when(userService.deactivateUser(1L, ADMIN_NAME)).thenReturn(testUser());

        mockMvc.perform(post("/admin/users/1/deactivate").principal(admin))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService).deactivateUser(1L, ADMIN_NAME);
    }

    @Test
    void deletesUserWithStringPrincipal() throws Exception {
        mockMvc.perform(post("/admin/users/1/delete").principal(admin))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService).deleteUser(1L, ADMIN_NAME);
    }

    @Test
    void updatesRoleWithStringPrincipal() throws Exception {
        when(userService.updateRole(1L, "ROLE_USER", ADMIN_NAME)).thenReturn(testUser());

        mockMvc.perform(patch("/api/admin/users/1/role")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ROLE_USER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.newRole").value("ROLE_USER"));

        verify(userService).updateRole(1L, "ROLE_USER", ADMIN_NAME);
    }

    private void createUser(Principal principal) throws Exception {
        mockMvc.perform(post("/admin/users/save")
                        .principal(principal)
                        .param("username", "APAC\\newuser")
                        .param("displayName", "New User")
                        .param("email", "newuser@example.com")
                        .param("role", "ROLE_USER")
                        .param("status", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attributeExists("successMessage"));

        verify(userService).createUser("APAC\\newuser", "New User",
                "newuser@example.com", "ROLE_USER", ADMIN_NAME);
    }

    private PebUser testUser() {
        return new PebUser("APAC\\newuser", "New User",
                "newuser@example.com", "ROLE_USER");
    }
}