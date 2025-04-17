package org.mpisws.jmc.util;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

public class JmcRandom extends java.util.Random {
    public JmcRandom() {
    }

    public JmcRandom(long seed) {
        // Ignore the seed
        super();
    }

    @Override
    public int next(int bits) {
        Integer val = JmcRuntime.<Integer>updateEventAndYield(
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.REACTIVE_EVENT_RANDOM_VALUE)
                        .taskId(JmcRuntime.currentTask())
                        .param("bits", bits)
                        .build()
        );
        if (val == null) {
            return super.next(bits);
        } else {
            return val;
        }
    }
}
