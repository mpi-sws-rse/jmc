package org.mpisws.instrumentation.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import org.mpisws.instrumentation.agent.visitors.JmcFutureVisitor;
import org.mpisws.instrumentation.agent.visitors.JmcReadWriteVisitor;
import org.mpisws.instrumentation.agent.visitors.JmcReentrantLockVisitor;
import org.mpisws.instrumentation.agent.visitors.JmcThreadVisitor;

import java.lang.instrument.Instrumentation;

public class InstrumentationAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        new AgentBuilder.Default()
                .type(AgentBuilder.RawMatcher.Trivial.MATCHING)
                .transform(
                        (builder, typeDescription, classLoader, module, protectionDomain) ->
                                builder.visit(new JmcThreadVisitor())
                                        .visit(new JmcReadWriteVisitor())
                                        .visit(new JmcReentrantLockVisitor())
                                        .visit(new JmcFutureVisitor()))
                .installOn(inst);
    }
}
