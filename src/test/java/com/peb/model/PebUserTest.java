package com.peb.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PebUserTest {

    @Test
    void derivesDepartmentFromDisplayNameOnConstruction() {
        PebUser user = new PebUser(
                "mmustermann",
                "Mustermann Max (Abteilung ABC)",
                "max.mustermann@bosch.com",
                "ROLE_USER");

        assertEquals("Abteilung ABC", user.getDepartment());
    }

    @Test
    void updatesDepartmentWhenDisplayNameChanges() {
        PebUser user = new PebUser();

        user.setDisplayName("Mustermann Max (Abteilung Entwicklung)");

        assertEquals("Abteilung Entwicklung", user.getDepartment());
    }

    @Test
    void clearsDepartmentWhenDisplayNameHasNoParentheses() {
        PebUser user = new PebUser();

        user.setDisplayName("Mustermann Max");

        assertNull(user.getDepartment());
    }
}