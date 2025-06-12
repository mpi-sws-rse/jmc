package org.mpisws.jmc.api.util;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;
import org.mpisws.jmc.runtime.scheduling.PrimitiveValue;

public class JmcRandom extends java.util.Random {
    public JmcRandom() {
    }

    public JmcRandom(long seed) {
        // Ignore the seed
        super();
    }

    @Override
    public int next(int bits) {
        PrimitiveValue val = JmcRuntime.<PrimitiveValue>updateEventAndYield(
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.REACTIVE_EVENT_RANDOM_VALUE)
                        .taskId(JmcRuntime.currentTask())
                        .param("bits", bits)
                        .build()
        );
        if (val == null) {
            return super.next(bits);
        } else {
            return val.asInteger();
        }
    }
}
