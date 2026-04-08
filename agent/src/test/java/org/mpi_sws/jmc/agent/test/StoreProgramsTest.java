package org.mpi_sws.jmc.agent.test;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

public class StoreProgramsTest {

    @Test
    public void testStorePrograms() {

        HashMap<String, String> programsToTranslate = new HashMap<>();

        programsToTranslate.put(
                "build/classes/java/test/org/mpi_sws/jmc/agent/test/test_programs/ChannelWaitNotify.class",
                "build/generated/instrumented/org/mpi_sws/jmc/agent/test/test_programs/ChannelWaitNotify.class");

        for (String programPath : programsToTranslate.keySet()) {
            try {
                AgentTestUtil.translateAndStore(programPath, programsToTranslate.get(programPath));
            } catch (Exception e) {
                System.err.println("Error translating file: " + e);
                e.printStackTrace();
            }
        }
    }
}
