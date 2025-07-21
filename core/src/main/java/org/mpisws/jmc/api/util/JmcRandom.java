package org.mpisws.jmc.api.util;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.JmcRuntimeEvent;
import org.mpisws.jmc.runtime.scheduling.PrimitiveValue;

/**
 * A JMC-specific implementation of java.util.Random that allows for model checking. This class
 * overrides the next method to yield control to the JMC runtime, allowing for reactive event
 * handling and model checking.
 */
public class JmcRandom extends java.util.Random {

    /** Default constructor for JmcRandom. Ignores the seed */
    public JmcRandom() {}

    /**
     * To ensure compatibility with the java.util.Random API, this constructor is provided. Ignores
     * the seed.
     */
    public JmcRandom(long seed) {
        // Ignore the seed
        super();
    }

    /**
     * This method is overridden to yield control to the JMC runtime. It allows the JMC model
     * checker to handle reactive events and return a random value.
     *
     * @param bits the number of bits to generate
     * @return a random integer value based on the specified number of bits
     */
    @Override
    public int next(int bits) {
        PrimitiveValue val =
                JmcRuntime.<PrimitiveValue>updateEventAndYield(
                        new JmcRuntimeEvent.Builder()
                                .type(JmcRuntimeEvent.Type.REACTIVE_EVENT_RANDOM_VALUE)
                                .taskId(JmcRuntime.currentTask())
                                .param("bits", bits)
                                .build());
        if (val == null) {
            return super.next(bits);
        } else {
            return val.asInteger();
        }
    }
}
