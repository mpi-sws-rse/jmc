package org.mpisws.jmc.checker;

import org.junit.jupiter.api.Test;
import org.mpisws.jmc.annotations.JmcCheckConfiguration;
import org.mpisws.jmc.checker.exceptions.JmcCheckerException;
import org.mpisws.jmc.runtime.SchedulingChoice;
import org.mpisws.jmc.strategies.TrackActiveTasksStrategy;
import org.mpisws.jmc.util.concurrent.JmcThread;

import java.util.Iterator;
import java.util.Set;

public class JmcBlockingTest {

    void testProgram() {
        JmcThread t= new JmcThread(() -> {
            // Your test code here
            assert false;
        });

        t.start();
        try {
            t.join1();
        } catch (InterruptedException e) {
            // Handle exception
        }
    }

    private class BlockingStrategy extends TrackActiveTasksStrategy {
        @Override
        public SchedulingChoice<?> nextTask() {
            Set<Long> tasks = getActiveTasks();
            if (tasks.isEmpty()) {
                return null;
            }
            if (tasks.size() == 1) {
                return SchedulingChoice.task(tasks.iterator().next());
            }
            Iterator<Long> taskIterator = tasks.iterator();
            taskIterator.next();
            Long blockingTask = taskIterator.next();
            return SchedulingChoice.blockTask(blockingTask);
        }
    }

    @Test
    void testBlocking() throws JmcCheckerException {
        JmcCheckerConfiguration config = new JmcCheckerConfiguration.Builder()
                .numIterations(10)
                .strategyConstructor((sConfig) -> new BlockingStrategy())
                .build();
        JmcModelChecker jmc = new JmcModelChecker(config);

        jmc.check(new JmcFunctionalTestTarget(
                "BlockingProgram",
                this::testProgram
        ));
    }
}
