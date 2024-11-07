package org.mpisws.concurrent.programs.det.stack;

import java.util.concurrent.ThreadLocalRandom;

public class Backoff {

    public final int minDelay, maxDelay;
    int limit;

    public Backoff(int min, int max) {
        minDelay = min;
        maxDelay = max;
        limit = minDelay;
    }

    public void backoff() {
        int delay = ThreadLocalRandom.current().nextInt(limit);
        limit = Math.min(maxDelay, 2 * limit);
        // Thread.sleep(delay);
    }
}
