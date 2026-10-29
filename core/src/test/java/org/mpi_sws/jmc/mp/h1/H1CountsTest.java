package org.mpi_sws.jmc.mp.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The H1 programs as regression tests (J2b round 02, owner's ruling Q3): one method per H1 row, each
 * running the program through the launcher's counting {@code must} mode in a fresh JVM
 * ({@code MpLauncher <class>:<args> must 0 1 <reportPath> - 60}) and asserting TraceForge's complete
 * and blocked execution counts. Every expected count is a constant taken from TraceForge f9baaba
 * ({@code plan/jmc-must/log/dev/H1/tf-ref.tsv}, columns complete and blocked). Rows covered: the five
 * reproducers and every row whose JMC count equals TraceForge's and which ran in under 10 s once
 * (tester's selection, J2b round 02); the larger rows stay in the counting-mode table. J2c round 01
 * adds the ten spawn_daemon rows (job queue push, push-thread, pull at W = 2, 3, 4; mpsc), whose ports
 * now spawn daemons where the Rust does: complete and blocked both asserted.
 */
class H1CountsTest {

    private static final String LAUNCHER = "org.mpi_sws.jmc.mp.launch.MpLauncher";
    private static final String PKG = "org.mpi_sws.jmc.mp.h1.";

    /** Runs the row and asserts TraceForge's counts and an exhausted stack. */
    static void check(String program, int complete, int blocked) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        cmd.add("-Xss4m");
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add(LAUNCHER);
        cmd.add(PKG + program);
        cmd.add("must");
        cmd.add("0");
        cmd.add("1");
        cmd.add("build/test-results/h1-tester/" + program.replace(':', '_').replace(',', '_'));
        cmd.add("-");
        cmd.add("60");
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Thread pump = new Thread(() -> {
            try (InputStream in = p.getInputStream()) {
                in.transferTo(buf);
            } catch (java.io.IOException e) {
                // the process ended
            }
        });
        pump.start();
        boolean done = p.waitFor(120, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
        }
        pump.join(5000);
        String out = buf.toString();
        System.out.println("H1 " + program + "\n" + out);
        assertTrue(done, "the launcher finished within 120 s");
        assertEquals(0, p.exitValue(), "exit status\n" + out);
        Matcher m = Pattern.compile("(?m)^graphs:\\s+(.*)$").matcher(out);
        assertTrue(m.find(), "a graphs: line\n" + out);
        assertEquals(complete + " distinct complete, " + blocked + " distinct blocked; stack exhausted",
                m.group(1).trim(), "TraceForge f9baaba: " + complete + " complete, " + blocked + " blocked");
    }

    @Test
    void row_bank_safe() throws Exception {
        check("Actors:bank", 3, 0);
    }

    @Test
    void row_bounded_channel_1() throws Exception {
        check("ActorChannels:bounded_channel,1", 8, 0);
    }

    @Test
    void row_bounded_channel_1_tfjoin() throws Exception {
        check("ActorChannels:bounded_channel,1,tfjoin", 8, 0);
    }

    @Test
    void row_bounded_channel_2() throws Exception {
        check("ActorChannels:bounded_channel,2", 9, 0);
    }

    @Test
    void row_bounded_channel_2_tfjoin() throws Exception {
        check("ActorChannels:bounded_channel,2,tfjoin", 9, 0);
    }

    @Test
    void row_bounded_channel_3() throws Exception {
        check("ActorChannels:bounded_channel,3", 9, 0);
    }

    @Test
    void row_bounded_channel_3_tfjoin() throws Exception {
        check("ActorChannels:bounded_channel,3,tfjoin", 9, 0);
    }

    @Test
    void row_bounded_channel_4() throws Exception {
        check("ActorChannels:bounded_channel,4", 9, 0);
    }

    @Test
    void row_cs_global_unique_channel() throws Exception {
        check("ChannelSemantics:global_unique_channel", 2, 0);
    }

    @Test
    void row_cs_spurious_timeout() throws Exception {
        check("ChannelSemantics:spurious_timeout", 2, 0);
    }

    @Test
    void row_eo_test_out_of_order_blocking_recv() throws Exception {
        check("ExecutionOrder:test_out_of_order_blocking_recv", 2, 0);
    }

    @Test
    void row_eo_test_out_of_order_blocking_recv2() throws Exception {
        check("ExecutionOrder:test_out_of_order_blocking_recv2", 2, 0);
    }

    @Test
    void row_eo_test_out_of_order_nonblocking_recv() throws Exception {
        check("ExecutionOrder:test_out_of_order_nonblocking_recv", 4, 0);
    }

    @Test
    void row_eo_test_out_of_order_nonblocking_recv2() throws Exception {
        check("ExecutionOrder:test_out_of_order_nonblocking_recv2", 4, 0);
    }

    @Test
    void row_eo_two_receives_two_sends() throws Exception {
        check("ExecutionOrder:two_receives_two_sends", 1, 0);
    }

    @Test
    void row_est_a_nstepsb_2() throws Exception {
        check("Estimation:a_nstepsb,2", 2, 0);
    }

    @Test
    void row_est_a_nstepsb_3() throws Exception {
        check("Estimation:a_nstepsb,3", 2, 0);
    }

    @Test
    void row_est_a_nstepsb_4() throws Exception {
        check("Estimation:a_nstepsb,4", 2, 0);
    }

    @Test
    void row_est_a_nstepsb_5() throws Exception {
        check("Estimation:a_nstepsb,5", 2, 0);
    }

    @Test
    void row_est_ns_nseqr_2() throws Exception {
        check("Estimation:ns_nseqr,2", 2, 0);
    }

    @Test
    void row_est_ns_nseqr_3() throws Exception {
        check("Estimation:ns_nseqr,3", 6, 0);
    }

    @Test
    void row_est_ns_nseqr_4() throws Exception {
        check("Estimation:ns_nseqr,4", 24, 0);
    }

    @Test
    void row_est_ns_nseqr_5() throws Exception {
        check("Estimation:ns_nseqr,5", 120, 0);
    }

    @Test
    void row_est_sr_ncopies_2() throws Exception {
        check("Estimation:sr_ncopies,2", 1, 0);
    }

    @Test
    void row_est_sr_ncopies_3() throws Exception {
        check("Estimation:sr_ncopies,3", 1, 0);
    }

    @Test
    void row_est_sr_ncopies_4() throws Exception {
        check("Estimation:sr_ncopies,4", 1, 0);
    }

    @Test
    void row_est_sr_ncopies_5() throws Exception {
        check("Estimation:sr_ncopies,5", 1, 0);
    }

    @Test
    void row_est_srs_rss_rr() throws Exception {
        check("Estimation:srs_rss_rr", 2, 0);
    }

    @Test
    void row_hard_to_find_10() throws Exception {
        check("WorkTerminate:hard_to_find,10", 0, 11);
    }

    @Test
    void row_hard_to_find_2() throws Exception {
        check("WorkTerminate:hard_to_find,2", 0, 3);
    }

    @Test
    void row_hard_to_find_3() throws Exception {
        check("WorkTerminate:hard_to_find,3", 0, 4);
    }

    @Test
    void row_hard_to_find_4() throws Exception {
        check("WorkTerminate:hard_to_find,4", 0, 5);
    }

    @Test
    void row_intro_race() throws Exception {
        check("WorkTerminate:intro_race", 2, 0);
    }

    @Test
    void row_lc_backward_revisit_bag() throws Exception {
        check("LitmusChannels:backward_revisit,bag", 2, 0);
    }

    @Test
    void row_lc_backward_revisit_fifo() throws Exception {
        check("LitmusChannels:backward_revisit,fifo", 2, 0);
    }

    @Test
    void row_lc_block() throws Exception {
        check("LitmusChannels:block", 0, 1);
    }

    @Test
    void row_lc_blocking_rev() throws Exception {
        check("LitmusChannels:blocking_rev", 6, 0);
    }

    @Test
    void row_lc_blocking_rev_fixed() throws Exception {
        check("LitmusChannels:blocking_rev", 6, 0);
    }

    @Test
    void row_lc_equal_channels() throws Exception {
        check("LitmusChannels:equal_channels", 2, 0);
    }

    @Test
    void row_lc_forward_revisit_bag() throws Exception {
        check("LitmusChannels:forward_revisit,bag", 3, 0);
    }

    @Test
    void row_lc_forward_revisit_fifo() throws Exception {
        check("LitmusChannels:forward_revisit,fifo", 2, 0);
    }

    @Test
    void row_lc_legacy_channel() throws Exception {
        check("LitmusChannels:legacy_channel", 4, 0);
    }

    @Test
    void row_lc_maximality1() throws Exception {
        check("LitmusChannels:maximality1", 3, 0);
    }

    @Test
    void row_lc_maximality2_bag() throws Exception {
        check("LitmusChannels:maximality2,bag", 7, 0);
    }

    @Test
    void row_lc_maximality2_fifo() throws Exception {
        check("LitmusChannels:maximality2,fifo", 4, 0);
    }

    @Test
    void row_lc_no_prefix_rev_cb() throws Exception {
        check("LitmusChannels:no_prefix_rev_cb", 1, 0);
    }

    @Test
    void row_lc_no_prefix_rev_ej() throws Exception {
        check("LitmusChannels:no_prefix_rev_ej", 1, 0);
    }

    @Test
    void row_lc_rrb_s() throws Exception {
        check("LitmusChannels:rrb_s", 1, 1);
    }

    @Test
    void row_lc_smoke() throws Exception {
        check("LitmusChannels:smoke", 1, 0);
    }

    @Test
    void row_lc_stress_max_bag_2() throws Exception {
        check("LitmusChannels:stress_max,bag,2", 7, 0);
    }

    @Test
    void row_lc_stress_max_bag_3() throws Exception {
        check("LitmusChannels:stress_max,bag,3", 13, 0);
    }

    @Test
    void row_lc_stress_max_bag_4() throws Exception {
        check("LitmusChannels:stress_max,bag,4", 21, 0);
    }

    @Test
    void row_lc_stress_max_fifo_2() throws Exception {
        check("LitmusChannels:stress_max,fifo,2", 4, 0);
    }

    @Test
    void row_lc_stress_max_fifo_3() throws Exception {
        check("LitmusChannels:stress_max,fifo,3", 8, 0);
    }

    @Test
    void row_lc_stress_max_fifo_4() throws Exception {
        check("LitmusChannels:stress_max,fifo,4", 16, 0);
    }

    @Test
    void row_litmus_nested_spawn() throws Exception {
        check("Litmus:nested_spawn", 1, 1);
    }

    @Test
    void row_litmus_nested_spawn2() throws Exception {
        check("Litmus:nested_spawn2", 2, 0);
    }

    @Test
    void row_litmus_nested_spawn3() throws Exception {
        check("Litmus:nested_spawn3", 2, 0);
    }

    @Test
    void row_litmus_ns_r_wb_2() throws Exception {
        check("Litmus:ns_r_wb,2", 2, 0);
    }

    @Test
    void row_litmus_ns_r_wb_3() throws Exception {
        check("Litmus:ns_r_wb,3", 3, 0);
    }

    @Test
    void row_litmus_ns_r_wb_4() throws Exception {
        check("Litmus:ns_r_wb,4", 4, 0);
    }

    @Test
    void row_litmus_r_choice_range() throws Exception {
        check("Litmus:r_choice_range", 8, 0);
    }

    @Test
    void row_litmus_r_choice_rangeinclusive() throws Exception {
        check("Litmus:r_choice_rangeinclusive", 10, 0);
    }

    @Test
    void row_litmus_revisit_opt() throws Exception {
        check("Litmus:revisit_opt", 7, 0);
    }

    @Test
    void row_litmus_revisit_opt_2() throws Exception {
        check("Litmus:revisit_opt_2", 2, 0);
    }

    @Test
    void row_litmus_rj_s() throws Exception {
        check("Litmus:rj_s", 1, 0);
    }

    @Test
    void row_litmus_r_nondet() throws Exception {
        check("Litmus:r_nondet", 4, 0);
    }

    @Test
    void row_litmus_r_ns_assm_2() throws Exception {
        check("Litmus:r_ns_assm,2", 2, 6);
    }

    @Test
    void row_litmus_r_ns_assm_3() throws Exception {
        check("Litmus:r_ns_assm,3", 3, 17);
    }

    @Test
    void row_litmus_r_ns_assm_4() throws Exception {
        check("Litmus:r_ns_assm,4", 4, 44);
    }

    @Test
    void row_litmus_r_ns_assm_7() throws Exception {
        check("Litmus:r_ns_assm,7", 7, 569);
    }

    @Test
    void row_litmus_r_rs_s_blk() throws Exception {
        check("Litmus:r_rs_s_blk", 1, 1);
    }

    @Test
    void row_litmus_r_s_blk() throws Exception {
        check("Litmus:r_s_blk", 1, 0);
    }

    @Test
    void row_litmus_r_s_r_ss() throws Exception {
        check("Litmus:r_s_r_ss", 2, 2);
    }

    @Test
    void row_litmus_r_s_r_ss_nonblocking() throws Exception {
        check("Litmus:r_s_r_ss_nonblocking", 3, 3);
    }

    @Test
    void row_litmus_r_ss() throws Exception {
        check("Litmus:r_ss", 3, 0);
    }

    @Test
    void row_litmus_r_ss_assm() throws Exception {
        check("Litmus:r_ss_assm", 1, 2);
    }

    @Test
    void row_litmus_r_s_ss() throws Exception {
        check("Litmus:r_s_ss", 2, 1);
    }

    @Test
    void row_litmus_sr_s() throws Exception {
        check("Litmus:sr_s", 2, 0);
    }

    @Test
    void row_litmus_s_rsrr_sss_coplacing() throws Exception {
        check("Litmus:s_rsrr_sss_coplacing", 12, 0);
    }

    @Test
    void row_litmus_srs_s_rs_nodangling() throws Exception {
        check("Litmus:srs_s_rs_nodangling", 4, 1);
    }

    @Test
    void row_litmus_ssrrr_s() throws Exception {
        check("Litmus:ssrrr_s", 3, 0);
    }

    @Test
    void row_litmus_ss_rr_ss_tag() throws Exception {
        check("Litmus:ss_rr_ss_tag", 2, 0);
    }

    @Test
    void row_litmus_ss_rr_tag() throws Exception {
        check("Litmus:ss_rr_tag", 1, 0);
    }

    @Test
    void row_litmus_ss_rr_tag2() throws Exception {
        check("Litmus:ss_rr_tag2", 1, 0);
    }

    @Test
    void row_litmus_ss_rr_tag3() throws Exception {
        check("Litmus:ss_rr_tag3", 1, 0);
    }

    @Test
    void row_litmus_ss_rr_tag_nb() throws Exception {
        check("Litmus:ss_rr_tag_nb", 4, 0);
    }

    @Test
    void row_litmus_s_s_s_wb() throws Exception {
        check("Litmus:s_s_s_wb", 1, 0);
    }

    @Test
    void row_litmus_test_named_thread() throws Exception {
        check("Litmus:test_named_thread", 4, 0);
    }

    @Test
    void row_litmus_weird_stamp_tiebreak() throws Exception {
        check("Litmus:weird_stamp_tiebreak", 1, 6);
    }

    @Test
    void row_locked_bank() throws Exception {
        check("ActorChannels:locked_bank", 4, 2);
    }

    @Test
    void row_locked_bank_tfjoin() throws Exception {
        check("ActorChannels:locked_bank,tfjoin", 4, 2);
    }

    @Test
    void row_micro_ns_nr_2() throws Exception {
        check("Micro:ns_nr,2", 2, 0);
    }

    @Test
    void row_micro_ns_nr_3() throws Exception {
        check("Micro:ns_nr,3", 6, 0);
    }

    @Test
    void row_micro_ns_nr_4() throws Exception {
        check("Micro:ns_nr,4", 24, 0);
    }

    @Test
    void row_micro_ns_rn_mk_2() throws Exception {
        check("Micro:ns_rn_mk,2", 2, 0);
    }

    @Test
    void row_micro_ns_rn_mk_3() throws Exception {
        check("Micro:ns_rn_mk,3", 6, 0);
    }

    @Test
    void row_micro_ns_rn_mk_4() throws Exception {
        check("Micro:ns_rn_mk,4", 24, 0);
    }

    @Test
    void row_micro_wakeup_stress_2() throws Exception {
        check("Micro:wakeup_stress,2", 4, 0);
    }

    @Test
    void row_micro_wakeup_stress_3() throws Exception {
        check("Micro:wakeup_stress,3", 12, 0);
    }

    @Test
    void row_micro_wakeup_stress_4() throws Exception {
        check("Micro:wakeup_stress,4", 48, 0);
    }

    @Test
    void row_minibus_nowait() throws Exception {
        check("Minibus:nowait", 13, 3);
    }

    @Test
    void row_minibus_wait() throws Exception {
        check("Minibus:wait", 9, 2);
    }

    @Test
    void row_repro_f2_nested_spawn() throws Exception {
        check("H1Repro:f2_nested_spawn", 2, 0);
    }

    @Test
    void row_repro_f34_server() throws Exception {
        check("H1Repro:f34_server", 1, 0);
    }

    @Test
    void row_repro_f3_join_edge() throws Exception {
        check("H1Repro:f3_join_edge", 1, 0);
    }

    @Test
    void row_repro_f3_spawn_edge() throws Exception {
        check("H1Repro:f3_spawn_edge", 1, 0);
    }

    @Test
    void row_repro_f4_join_blocked() throws Exception {
        check("H1Repro:f4_join_blocked", 0, 1);
    }

    @Test
    void row_rupaxos_1() throws Exception {
        check("RuPaxos:1", 1299, 201);
    }

    @Test
    void row_rupaxos_1_tfjoin() throws Exception {
        check("RuPaxos:1,tfjoin", 1299, 201);
    }

    @Test
    void row_sem_p1_bag() throws Exception {
        check("SemanticsComparison:p1,bag", 24, 0);
    }

    @Test
    void row_sem_p1_fifo() throws Exception {
        check("SemanticsComparison:p1,fifo", 12, 0);
    }

    @Test
    void row_sem_p2_bag() throws Exception {
        check("SemanticsComparison:p2,bag", 24, 0);
    }

    @Test
    void row_sem_p2_fifo() throws Exception {
        check("SemanticsComparison:p2,fifo", 12, 0);
    }

    @Test
    void row_sem_p3_bag() throws Exception {
        check("SemanticsComparison:p3,bag", 6, 0);
    }

    @Test
    void row_sem_p3_fifo() throws Exception {
        check("SemanticsComparison:p3,fifo", 1, 0);
    }

    @Test
    void row_sem_p4_bag() throws Exception {
        check("SemanticsComparison:p4,bag", 4, 0);
    }

    @Test
    void row_sem_p4_fifo() throws Exception {
        check("SemanticsComparison:p4,fifo", 4, 0);
    }

    @Test
    void row_small_cover_basic_1() throws Exception {
        check("SmallTests:cover_basic_1", 2, 0);
    }

    @Test
    void row_small_cover_basic_2() throws Exception {
        check("SmallTests:cover_basic_2", 8, 0);
    }

    @Test
    void row_small_test_nondet() throws Exception {
        check("SmallTests:test_nondet", 4, 0);
    }

    @Test
    void row_tags_nontrivial_tag() throws Exception {
        check("Tags:nontrivial_tag", 4, 0);
    }

    @Test
    void row_tags_tag_on_sender() throws Exception {
        check("Tags:tag_on_sender", 1, 0);
    }

    @Test
    void row_token_server() throws Exception {
        check("Actors:token_server", 4, 10);
    }

    @Test
    void row_two_pc_2() throws Exception {
        check("TwoPc:two_pc,2", 8, 0);
    }

    @Test
    void row_two_pc_3() throws Exception {
        check("TwoPc:two_pc,3", 48, 0);
    }

    @Test
    void row_two_pc_4() throws Exception {
        check("TwoPc:two_pc,4", 384, 0);
    }

    @Test
    void row_two_pc_demo_2() throws Exception {
        check("TwoPc:two_pc_demo,2", 8, 0);
    }

    @Test
    void row_two_pc_demo_3() throws Exception {
        check("TwoPc:two_pc_demo,3", 48, 0);
    }

    @Test
    void row_two_pc_demo_4() throws Exception {
        check("TwoPc:two_pc_demo,4", 384, 0);
    }

    @Test
    void row_vt_basic() throws Exception {
        check("VecTags:basic", 1, 0);
    }

    @Test
    void row_vt_lexicographic() throws Exception {
        check("VecTags:lexicographic", 1, 0);
    }

    // ------------------------------------------------------------------ J2c: the spawn_daemon rows

    @Test
    void row_job_push_2() throws Exception {
        check("JobQueue:push,2", 8, 0);
    }

    @Test
    void row_job_push_3() throws Exception {
        check("JobQueue:push,3", 27, 0);
    }

    @Test
    void row_job_push_4() throws Exception {
        check("JobQueue:push,4", 64, 0);
    }

    @Test
    void row_job_push_thread_2() throws Exception {
        check("JobQueue:push-thread,2", 8, 0);
    }

    @Test
    void row_job_push_thread_3() throws Exception {
        check("JobQueue:push-thread,3", 27, 0);
    }

    @Test
    void row_job_push_thread_4() throws Exception {
        check("JobQueue:push-thread,4", 64, 0);
    }

    @Test
    void row_job_pull_2() throws Exception {
        check("JobQueue:pull,2", 16, 0);
    }

    @Test
    void row_job_pull_3() throws Exception {
        check("JobQueue:pull,3", 81, 0);
    }

    @Test
    void row_job_pull_4() throws Exception {
        check("JobQueue:pull,4", 256, 0);
    }

    @Test
    void row_mpsc_simple() throws Exception {
        check("ActorChannels:mpsc", 1, 0);
    }
}
