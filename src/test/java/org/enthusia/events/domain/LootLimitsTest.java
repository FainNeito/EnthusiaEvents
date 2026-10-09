package org.enthusia.events.domain;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LootLimitsTest {
    @Test void limitsExcessArmorWithoutRemovingUsefulBlocks() {
        var limits = new LootLimits(2, 2, 12);
        assertFalse(limits.permits(2, 1, 4, true, false));
        assertTrue(limits.permits(2, 1, 4, false, false));
        assertFalse(limits.permits(1, 2, 4, false, true));
        assertFalse(limits.permits(1, 1, 12, false, false));
    }
    @Test void rejectsNonFiniteAndInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> LootLimits.validateEntry(1, 2, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> LootLimits.validateEntry(3, 1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> LootLimits.validateEntry(1, 65, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new LootLimits(-1, 0, 0));
    }
    @Test void zeroLimitsPreserveLegacyUnlimitedLoot() {
        assertTrue(new LootLimits(0, 0, 0).permits(10, 10, 40, true, true));
    }
}
