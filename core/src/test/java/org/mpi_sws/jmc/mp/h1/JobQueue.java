package org.mpi_sws.jmc.mp.h1;

import org.mpi_sws.jmc.api.mp.Channel;
import org.mpi_sws.jmc.api.mp.MpProcess;
import org.mpi_sws.jmc.api.mp.Nondet;
import org.mpi_sws.jmc.mp.events.CommunicationModel;
import org.mpi_sws.jmc.mp.events.MpMessage;
import org.mpi_sws.jmc.mp.events.Tid;

import java.util.ArrayList;
import java.util.List;

/**
 * TraceForge {@code tests/job_queue.rs}, its three job-queue models (FIFO, default config):
 * {@code push} (every submit picks a worker by {@code (0..W).nondet()}), {@code push-thread}
 * (submits go to a queue thread that picks the worker), {@code pull} (workers send a
 * {@code WorkRequest}, the queue thread pairs it with a submitted job). One top-level job, which
 * creates two leaf jobs. Workers and the queue thread are daemons, as in Rust ({@code spawn_daemon}:
 * infinite loops, never joined): {@link MpProcess#spawnDaemon} (J2c), so their final blocked receive
 * does not make the execution blocked, in JMC as in TraceForge.
 *
 * <p>Tags → classes: {@code INIT_TAG} messages are {@link PushQ}, {@link PwtQ}, {@link Workers},
 * {@link PullQ} (one per receiver kind); {@code REQUEST_TAG} is {@link WorkRequest};
 * {@code SUBMIT_TAG} is a {@link Job}. Untagged receives are unguarded.
 *
 * <p>Arguments: {@code <push|push-thread|pull>,W}.
 */
public final class JobQueue implements Runnable {

    /** {@code Job}. */
    sealed interface Job extends MpMessage permits TopLevelJob, LeafJob {}

    /** {@code Job::TopLevelJob(String)}. */
    record TopLevelJob(String name) implements Job {}

    /** {@code Job::LeafJob(String)}. */
    record LeafJob(String name) implements Job {}

    /** {@code PushJobQueue { workers }}, sent with INIT_TAG. */
    record PushQ(List<Tid> workers) implements MpMessage {}

    /** {@code PushWithThreadJobQueue { queue_thread, workers }}, sent with INIT_TAG. */
    record PwtQ(Tid queueThread, List<Tid> workers) implements MpMessage {}

    /** The queue thread's {@code Vec<ThreadId>}, sent with INIT_TAG. */
    record Workers(List<Tid> workers) implements MpMessage {}

    /** {@code PullJobQueue { queue_thread }}, sent with INIT_TAG. */
    record PullQ(Tid queueThread) implements MpMessage {}

    /** {@code WorkRequest { requester }}, sent with REQUEST_TAG. */
    record WorkRequest(Tid requester) implements MpMessage {}

    private static final CommunicationModel M = CommunicationModel.P2P;

    private final String kind;
    private final int w;

    /**
     * @param args model and number of workers
     */
    public JobQueue(String... args) {
        this.kind = args[0].trim();
        this.w = Integer.parseInt(args[1].trim());
        if (!List.of("push", "push-thread", "pull").contains(kind)) {
            throw new IllegalArgumentException("JobQueue takes <push|push-thread|pull>,W");
        }
    }

    @Override
    public void run() {
        H1Support.Spawner sp = new H1Support.Spawner();
        switch (kind) {
            case "push" -> {
                List<Tid> workers = new ArrayList<>();
                for (int i = 0; i < w; i++) {
                    workers.add(MpProcess.spawnDaemon(() -> pushWorker(PushQ.class)).tid());
                }
                PushQ q = new PushQ(List.copyOf(workers));
                for (Tid t : workers) {
                    Channel.send(M, t, q);
                }
                submit(q, new TopLevelJob("a"));
            }
            case "push-thread" -> {
                List<Tid> ws = new ArrayList<>();
                for (int i = 0; i < w; i++) {
                    ws.add(MpProcess.spawnDaemon(() -> pushWorker(PwtQ.class)).tid());
                }
                List<Tid> workers = List.copyOf(ws);
                Tid queue = MpProcess.spawnDaemon(() -> {
                    List<Tid> wk = Channel.recv(M, Workers.class).workers();
                    while (true) {
                        Job msg = (Job) Channel.recv(M);
                        int index = Nondet.chooseIndex(wk.size());
                        Channel.send(M, wk.get(index), msg);
                    }
                }).tid();
                Channel.send(M, queue, new Workers(workers));
                PwtQ q = new PwtQ(queue, workers);
                for (Tid t : workers) {
                    Channel.send(M, t, q);
                }
                submit(q, new TopLevelJob("a"));
            }
            case "pull" -> {
                List<Tid> ws = new ArrayList<>();
                for (int i = 0; i < w; i++) {
                    ws.add(MpProcess.spawnDaemon(this::pullWorker).tid());
                }
                Tid queue = MpProcess.spawnDaemon(() -> {
                    while (true) {
                        Tid requester = Channel.recv(M, WorkRequest.class).requester();
                        Job job = Channel.recv(M, Job.class);
                        Channel.send(M, requester, job);
                    }
                }).tid();
                PullQ q = new PullQ(queue);
                for (Tid t : ws) {
                    Channel.send(M, t, q);
                }
                submit(q, new TopLevelJob("a"));
            }
            default -> throw new IllegalStateException(kind);
        }
        sp.awaitAll();
    }

    /** {@code JobQueue::submit_job} for each queue kind. */
    private static void submit(MpMessage q, Job job) {
        if (q instanceof PushQ p) {
            int index = Nondet.chooseIndex(p.workers().size());
            Channel.send(M, p.workers().get(index), job);
        } else if (q instanceof PwtQ p) {
            Channel.send(M, p.queueThread(), job);
        } else {
            Channel.send(M, ((PullQ) q).queueThread(), job);
        }
    }

    /** {@code push_worker_thread::<T>()}. */
    private static void pushWorker(Class<? extends MpMessage> queueType) {
        MpMessage q = Channel.recv(M, queueType);
        while (true) {
            Job job = (Job) Channel.recv(M);
            if (job instanceof TopLevelJob t) {
                submit(q, new LeafJob(t.name() + "1"));
                submit(q, new LeafJob(t.name() + "2"));
            }
        }
    }

    /** {@code pull_worker_thread()}. */
    private void pullWorker() {
        PullQ q = Channel.recv(M, PullQ.class);
        while (true) {
            Channel.send(M, q.queueThread(), new WorkRequest(MpProcess.self()));
            Job job = (Job) Channel.recv(M);
            if (job instanceof TopLevelJob t) {
                submit(q, new LeafJob(t.name() + "1"));
                submit(q, new LeafJob(t.name() + "2"));
            }
        }
    }
}
