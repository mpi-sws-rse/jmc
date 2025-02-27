package org.mpisws.jmc.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import org.mpisws.jmc.agent.visitors.JmcFutureVisitor;
import org.mpisws.jmc.agent.visitors.JmcReadWriteVisitor;
import org.mpisws.jmc.agent.visitors.JmcReentrantLockVisitor;
import org.mpisws.jmc.agent.visitors.JmcThreadVisitor;

import java.lang.instrument.Instrumentation;

public class InstrumentationAgent {

    /** The AgentArgs class is used to parse the agent arguments. */
    private static class AgentArgs {
        private static final String DEBUG_FLAG = "debug";
        private static final String DEBUG_PATH_FLAG = "debugSavePath";
        private boolean debug = false;
        private String debugSavePath = "build/generated/instrumented";

        public AgentArgs(String agentArgs) {
            if (agentArgs != null) {
                String[] args = agentArgs.split(",");
                for (String arg : args) {
                    String[] parts = arg.split("=");
                    if (parts.length == 2) {
                        if (parts[0].equals(DEBUG_FLAG)) {
                            debug = Boolean.parseBoolean(parts[1]);
                        } else if (parts[0].equals(DEBUG_PATH_FLAG)) {
                            debugSavePath = parts[1];
                        }
                    } else {
                        if (arg.equals(DEBUG_FLAG)) {
                            debug = true;
                        }
                    }
                }
            }
        }

        public boolean isDebug() {
            return debug;
        }

        public String getDebugSavePath() {
            return debugSavePath;
        }
    }

    /**
     * The premain method is called before the application's main method is called. It is used to
     * set up the instrumentation agent.
     *
     * @param agentArgs the agent arguments
     * @param inst the instrumentation object
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        AgentArgs args = new AgentArgs(agentArgs);
        AgentBuilder agentBuilder = new AgentBuilder.Default();
        if (args.isDebug()) {
            agentBuilder = agentBuilder.with(new DebugListener(args.getDebugSavePath()));
        }
        agentBuilder
                .type(new JmcMatcher())
                .transform(
                        (builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(new JmcThreadVisitor())
                                        .visit(new JmcReadWriteVisitor())
                                        .visit(new JmcReentrantLockVisitor())
                                        .visit(new JmcFutureVisitor()))
                .installOn(inst);
    }
}
