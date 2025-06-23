package org.mpisws.jmc.api.util.statements;

import org.mpisws.jmc.runtime.HaltTaskException;
import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

public class JmcAssume {

    public static void assume(boolean condition) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.ASSUME_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("result", condition)
                        .build();
        JmcRuntime.updateEventAndYield(event);

        if (!condition) {
            throw new HaltTaskException(JmcRuntime.currentTask(), "Assumption failed");
        }
    }
}
