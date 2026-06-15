package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RetrievalSettingsTest {

    @Test
    void acceptsLayeredRetrievalSettings() {
        assertDoesNotThrow(() -> new RetrievalSettings(30, 6, 0.70));
    }

    @Test
    void rejectsInvalidLimitsAndThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new RetrievalSettings(0, 1, 0.70));
        assertThrows(IllegalArgumentException.class, () -> new RetrievalSettings(5, 0, 0.70));
        assertThrows(IllegalArgumentException.class, () -> new RetrievalSettings(5, 6, 0.70));
        assertThrows(IllegalArgumentException.class, () -> new RetrievalSettings(5, 3, -0.01));
        assertThrows(IllegalArgumentException.class, () -> new RetrievalSettings(5, 3, 1.01));
    }
}
