package org.mpisws.strategies.trust;

import org.mpisws.strategies.TrackActiveTasksStrategy;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Strategy extends TrackActiveTasksStrategy {
    public Strategy() {
        super(List.of(new TrackTasks()));
        ThreadLocalRandom.current().nextInt(0, 100);
    }

    @Override
    public Long nextTask() {
        return 0L;
    }
}
