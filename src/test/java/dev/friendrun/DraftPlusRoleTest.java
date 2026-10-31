package dev.friendrun;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.junit.jupiter.api.Test;

final class DraftPlusRoleTest {
    @Test
    void exactlySixApprovedRolesExist() {
        assertEquals(6, DraftPlusRole.values().length);
    }

    @Test
    void roleIdsAreUniqueAndRoundTrip() {
        Set<String> ids = new HashSet<>();
        for (DraftPlusRole role : DraftPlusRole.values()) {
            assertTrue(ids.add(role.id()));
            assertSame(role, DraftPlusRole.byId(role.id()));
        }
    }

    @Test
    void unknownOrEmptyRoleIdsAreRejected() {
        assertNull(DraftPlusRole.byId(null));
        assertNull(DraftPlusRole.byId(""));
        assertNull(DraftPlusRole.byId("unknown"));
    }

    @Test
    void approvedBonusValuesAndOperationsAreStable() {
        assertEquals(0.10, DraftPlusRole.SPEEDSTER.amount());
        assertEquals(0.15, DraftPlusRole.MINER.amount());
        assertEquals(4.0, DraftPlusRole.GUARD.amount());
        assertEquals(1.0, DraftPlusRole.FIGHTER.amount());
        assertEquals(0.15, DraftPlusRole.ACROBAT.amount());
        assertEquals(0.20, DraftPlusRole.SWIMMER.amount());

        assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, DraftPlusRole.SPEEDSTER.operation());
        assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, DraftPlusRole.MINER.operation());
        assertEquals(AttributeModifier.Operation.ADD_VALUE, DraftPlusRole.GUARD.operation());
        assertEquals(AttributeModifier.Operation.ADD_VALUE, DraftPlusRole.FIGHTER.operation());
        assertEquals(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, DraftPlusRole.ACROBAT.operation());
        assertEquals(AttributeModifier.Operation.ADD_VALUE, DraftPlusRole.SWIMMER.operation());
    }
}
