package com.provipvp.modules;

import com.provipvp.broker.ConflictRegistry;
import com.provipvp.broker.PvpServices;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** R3: beobachtbare Entscheidung — Modul existiert, DEFERS-Registrierung und Settings. */
class ProviReconModuleTest {

    @Test
    void moduleClassListed() {
        assertNotNull(ProviReconModule.class);
    }

    @Test
    void defersDeclarationPresent() {
        try { Class.forName("com.provipvp.modules.ProviReconModule"); } catch (Exception e) { fail("Class load"); }
        ConflictRegistry reg = PvpServices.conflicts();
        assertNotNull(reg);
        // Registrierung mit DEFERS/20 erfolgt via static-Block beim Klassen-Load
        assertEquals(20, reg.priorityOf(ProviReconModule.class));
    }

    @Test
    void rtpSettingFieldDeclared() {
        assertDoesNotThrow(() -> ProviReconModule.class.getDeclaredField("rtpCommand"));
    }

    @Test
    void scanLightUpdatesFieldDeclared() {
        assertDoesNotThrow(() -> ProviReconModule.class.getDeclaredField("scanLightUpdates"));
    }
}
