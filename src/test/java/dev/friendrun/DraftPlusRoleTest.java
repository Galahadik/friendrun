package dev.friendrun;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
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

        assertTrue(DraftPlusRole.SPEEDSTER.multiplicative());
        assertTrue(DraftPlusRole.MINER.multiplicative());
        assertFalse(DraftPlusRole.GUARD.multiplicative());
        assertFalse(DraftPlusRole.FIGHTER.multiplicative());
        assertTrue(DraftPlusRole.ACROBAT.multiplicative());
        assertFalse(DraftPlusRole.SWIMMER.multiplicative());
    }
}
