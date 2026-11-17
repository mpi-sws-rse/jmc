package org.mpi_sws.jmc.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mpi_sws.jmc.checker.exceptions.JmcCheckerException;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.solver.SolverResult;
import org.mpi_sws.jmc.runtime.scheduling.MpDeliveryValue;
import org.mpi_sws.jmc.runtime.scheduling.ObjectValue;
import org.mpi_sws.jmc.runtime.scheduling.PrimitiveValue;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A schedule written by {@link FileUtil#storeTaskSchedule} reads back, by {@link FileUtil#readTaskSchedule},
 * as the same choices: the same task ids and kinds, and for a choice that carries a value, a value with the
 * same type and the same JSON.
 */
class FileUtilScheduleRoundTripTest {

    record Msg(int v) implements MpMessage {}

    @Test
    void valueBearingChoices_roundTrip(@TempDir Path dir) throws Exception {
        List<SchedulingChoice<?>> written = List.of(
                SchedulingChoice.task(1L),
                SchedulingChoice.task(2L, new PrimitiveValue(7)),
                SchedulingChoice.task(2L, new PrimitiveValue("seven")),
                SchedulingChoice.task(3L, MpDeliveryValue.bottom()),
                SchedulingChoice.task(3L, MpDeliveryValue.of(Tid.of(2L), 0L, new Msg(5))),
                SchedulingChoice.task(5L, new ObjectValue(new SolverResult(true, false))),
                SchedulingChoice.blockTask(4L),
                SchedulingChoice.blockExecution());
        String file = dir.resolve("schedule.json").toString();
        FileUtil.storeTaskSchedule(file, written);
        List<SchedulingChoice<?>> read = FileUtil.readTaskSchedule(file);
        assertEquals(written.size(), read.size());
        for (int i = 0; i < written.size(); i++) {
            SchedulingChoice<?> w = written.get(i);
            SchedulingChoice<?> r = read.get(i);
            assertEquals(w.getTaskId(), r.getTaskId(), "task id of choice " + i);
            assertEquals(w.isBlockTask(), r.isBlockTask(), "block-task flag of choice " + i);
            assertEquals(w.isBlockExecution(), r.isBlockExecution(), "block-execution flag of choice " + i);
            if (w.getValue() == null) {
                assertNull(r.getValue(), "choice " + i + " carries no value");
            } else {
                assertEquals(w.getValue().type(), r.getValue().type(), "value type of choice " + i);
                assertEquals(w.getValue().toJson(), r.getValue().toJson(), "value of choice " + i);
            }
        }
    }

    @Test
    void valueWithoutContent_refusedWithAClearError(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("schedule.json");
        Files.writeString(file, "{\"schedule\":[{\"taskId\":2,\"isBlockTask\":false,"
                + "\"isBlockExecution\":false,\"value\":{\"type\":\"primitive\"}}]}");
        JmcCheckerException e = assertThrows(JmcCheckerException.class,
                () -> FileUtil.readTaskSchedule(file.toString()));
        assertTrue(e.getMessage().contains("has no content"), e.getMessage());
    }
}
