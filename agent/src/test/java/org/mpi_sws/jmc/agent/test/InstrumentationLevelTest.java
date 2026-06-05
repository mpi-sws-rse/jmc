package org.mpi_sws.jmc.agent.test;

import org.junit.jupiter.api.Test;
import org.mpi_sws.jmc.agent.AgentArgs;
import org.mpi_sws.jmc.agent.InstrumentationLevel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the InstrumentationLevel enum and its integration with AgentArgs parsing.
 */
class InstrumentationLevelTest {

    @Test
    void fromString_full() {
        assertEquals(InstrumentationLevel.FULL, InstrumentationLevel.fromString("full"));
        assertEquals(InstrumentationLevel.FULL, InstrumentationLevel.fromString("FULL"));
        assertEquals(InstrumentationLevel.FULL, InstrumentationLevel.fromString("Full"));
    }

    @Test
    void fromString_syncPointsOnly() {
        assertEquals(InstrumentationLevel.SYNC_POINTS_ONLY, InstrumentationLevel.fromString("sync_points_only"));
        assertEquals(InstrumentationLevel.SYNC_POINTS_ONLY, InstrumentationLevel.fromString("SYNC_POINTS_ONLY"));
        assertEquals(InstrumentationLevel.SYNC_POINTS_ONLY, InstrumentationLevel.fromString("sync-points-only"));
        assertEquals(InstrumentationLevel.SYNC_POINTS_ONLY, InstrumentationLevel.fromString("syncpointsonly"));
        assertEquals(InstrumentationLevel.SYNC_POINTS_ONLY, InstrumentationLevel.fromString("sync"));
    }

    @Test
    void fromString_null_defaultsToFull() {
        assertEquals(InstrumentationLevel.FULL, InstrumentationLevel.fromString(null));
    }

    @Test
    void fromString_unknown_defaultsToFull() {
        assertEquals(InstrumentationLevel.FULL, InstrumentationLevel.fromString("garbage"));
        assertEquals(InstrumentationLevel.FULL, InstrumentationLevel.fromString(""));
    }

    @Test
    void agentArgs_defaultLevel_isFull() {
        AgentArgs args = new AgentArgs("debug,instrumentingPackages=com.example");
        assertEquals(InstrumentationLevel.FULL, args.getInstrumentationLevel());
    }

    @Test
    void agentArgs_parsesInstrumentationLevel_syncPointsOnly() {
        AgentArgs args = new AgentArgs("instrumentingPackages=com.example,instrumentationLevel=sync_points_only");
        assertEquals(InstrumentationLevel.SYNC_POINTS_ONLY, args.getInstrumentationLevel());
    }

    @Test
    void agentArgs_parsesInstrumentationLevel_full() {
        AgentArgs args = new AgentArgs("instrumentingPackages=com.example,instrumentationLevel=full");
        assertEquals(InstrumentationLevel.FULL, args.getInstrumentationLevel());
    }

    @Test
    void agentArgs_parsesInstrumentationLevel_caseInsensitive() {
        AgentArgs args = new AgentArgs("instrumentationLevel=SYNC_POINTS_ONLY");
        assertEquals(InstrumentationLevel.SYNC_POINTS_ONLY, args.getInstrumentationLevel());
    }
}
