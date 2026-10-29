package org.mpi_sws.jmc.mp.programs;

import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;
import org.mpi_sws.jmc.runtime.HaltExecutionException;
import org.mpi_sws.jmc.runtime.HaltTaskException;
import org.mpi_sws.jmc.runtime.JmcRuntimeEvent;
import org.mpi_sws.jmc.runtime.scheduling.MpDeliveryValue;
import org.mpi_sws.jmc.runtime.scheduling.SchedulingChoice;
import org.mpi_sws.jmc.strategies.mp.MpRandomSchedulingStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Tester fixture (J0 round 01): {@code mp-random} unchanged, plus a per-iteration log of what the
 * runtime reported and what the strategy answered, so a test can check the shape of every
 * iteration independently of the program's own tally.
 *
 * <p>Each log entry is {@code "<task>:<what>"}, in the global order the strategy saw them:
 * {@code START(by=<p>)}, {@code FINISH}, {@code SEND(dst=<raw>/<role>,model=<m>,v=<value>)},
 * {@code RECV(model=<m>,blocking=<b>)}, {@code DELIVER(v=<value>)} (the answer handed to a
 * receive), {@code BOTTOM}, {@code BLOCK}, and {@code <TYPE>} for every other event type.
 */
public final class ObservingMpRandomStrategy extends MpRandomSchedulingStrategy {

    /** One finished iteration's log per element, in iteration order. */
    public final List<List<String>> iterations = new ArrayList<>();

    private List<String> current = new ArrayList<>();

    /**
     * @param seed the random seed
     * @param reportPath the report directory
     */
    public ObservingMpRandomStrategy(Long seed, String reportPath) {
        super(seed, reportPath);
    }

    private static String show(Object v) {
        if (v instanceof MpMessage m) {
            return m.toString();
        }
        return String.valueOf(v);
    }

    @Override
    public void updateEvent(JmcRuntimeEvent event) throws HaltTaskException, HaltExecutionException {
        long task = event.getTaskId();
        JmcRuntimeEvent.Type type = event.getType();
        String what;
        switch (type) {
            case START_EVENT -> what = "START(by=" + event.getParam("startedBy") + ")";
            case MP_SEND_EVENT -> {
                Tid dst = event.getParam("dst");
                what = "SEND(dst=" + dst.raw() + "/" + dst.role() + ",model=" + event.getParam("model")
                        + ",v=" + show(event.getParam("value")) + ")";
            }
            case MP_RECV_REQUEST_EVENT -> what = "RECV(model=" + event.getParam("model")
                    + ",blocking=" + event.getParam("blocking") + ")";
            default -> what = type.name();
        }
        current.add(task + ":" + what);
        super.updateEvent(event);
    }

    @Override
    public SchedulingChoice<?> nextTask() {
        SchedulingChoice<?> choice = super.nextTask();
        if (choice != null) {
            if (choice.isBlockTask()) {
                current.add(choice.getTaskId() + ":BLOCK");
            } else if (choice.getValue() instanceof MpDeliveryValue d) {
                current.add(choice.getTaskId() + ":" + (d.isBottom() ? "BOTTOM" : "DELIVER(v=" + show(d.value()) + ")"));
            }
        }
        return choice;
    }

    @Override
    public void resetIteration(int iteration) {
        iterations.add(current);
        current = new ArrayList<>();
        super.resetIteration(iteration);
    }
}
