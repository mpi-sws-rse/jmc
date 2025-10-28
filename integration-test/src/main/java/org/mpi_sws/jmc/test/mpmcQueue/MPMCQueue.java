package org.mpi_sws.jmc.test.mpmcQueue;

import org.mpi_sws.jmc.api.util.statements.JmcAssume;

import java.util.concurrent.atomic.AtomicInteger;

public class MPMCQueue {
    private final int t_size;
    public final int[] m_array;
    // Both 32-bit: high 16 bits = readers started, low 16 bits = writers started
    private final AtomicInteger m_rdwr = new AtomicInteger(0);
    // low 16 bits count read-complete, written-complete
    private final AtomicInteger m_read = new AtomicInteger(0);
    private final AtomicInteger m_written = new AtomicInteger(0);

    public MPMCQueue(int size) {
        t_size = size;
        m_array = new int[size];
    }

    // Returns pointer to the next slot to read (null if empty)
    public Integer readFetch() {
        int rdwr = m_rdwr.get();
        int rd, wr;
        while (true) {
            rd = (rdwr >>> 16) & 0xFFFF;
            wr = rdwr & 0xFFFF;
            if (wr == rd) // empty
                return null;

            int newRdwr = ((rd + 1) << 16) | wr;
            // CAS high-16 bits
            if (m_rdwr.compareAndSet(rdwr, newRdwr)) break;

            rdwr = m_rdwr.get();
        }

        // Wait for write to complete
        JmcAssume.assume((m_written.get() & 0xFFFF) == wr);

        return m_array[rd % t_size];
    }

    public void readConsume() {
        m_read.getAndIncrement();
    }

    // Returns pointer to slot for producer to write, or null if full
    public Integer writePrepare() {
        int rdwr = m_rdwr.get();
        int rd, wr;
        while (true) {
            rd = (rdwr >>> 16) & 0xFFFF;
            wr = rdwr & 0xFFFF;
            if (wr == ((rd + t_size) & 0xFFFF)) // full
                return null;
            int newRdwr = (rd << 16) | ((wr + 1) & 0xFFFF);
            if (m_rdwr.compareAndSet(rdwr, newRdwr)) break;
            rdwr = m_rdwr.get();
        }

        // Wait for readers to complete previous cycle
        JmcAssume.assume((m_read.get() & 0xFFFF) == rd);

        return wr % t_size;
    }

    public void writePublish() {
        m_written.getAndIncrement();
    }
}
