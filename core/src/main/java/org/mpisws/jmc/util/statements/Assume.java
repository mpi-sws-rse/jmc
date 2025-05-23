package org.mpisws.jmc.util.statements;

import org.mpisws.jmc.runtime.JmcRuntime;
import org.mpisws.jmc.runtime.RuntimeEvent;

public class Assume {

    public static void assume(boolean condition) {
        RuntimeEvent event =
                new RuntimeEvent.Builder()
                        .type(RuntimeEvent.Type.ASSUME_EVENT)
                        .taskId(JmcRuntime.currentTask())
                        .param("result", condition)
                        .build();
        JmcRuntime.updateEventAndYield(event);
    }

}
