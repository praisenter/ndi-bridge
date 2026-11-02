package org.praisenter.ndibridge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NdiBridgeTest {
    @Test
    void classInitializationDoesNotLoadNatives() {
        assertFalse(NdiBridge.isLoaded());
        assertTrue(NdiBridge.getLastLoadError().isEmpty());
        assertThrows(IllegalStateException.class, NdiBridge::getNDIVersion);
        assertThrows(IllegalStateException.class, NdiBridge::isSupportedCpu);
    }
}