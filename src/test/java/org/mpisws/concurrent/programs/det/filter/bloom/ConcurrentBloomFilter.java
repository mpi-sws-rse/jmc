package org.mpisws.concurrent.programs.det.filter.bloom;

import org.mpisws.util.concurrent.AtomicIntegerArray;
import org.mpisws.util.concurrent.JMCInterruptException;

import java.util.concurrent.ThreadLocalRandom;

public class ConcurrentBloomFilter {

    private final AtomicIntegerArray bitSet;
    private final int size;
    private final int hashFunctionsCount;

    public ConcurrentBloomFilter(int size, int hashFunctionsCount) {
        this.size = size;
        this.hashFunctionsCount = hashFunctionsCount;
        bitSet = new AtomicIntegerArray(size);
    }

    public void add(String item) throws JMCInterruptException {
        for (int i = 0; i < hashFunctionsCount; i++) {
            int hash = hash(item, i);
            bitSet.set(hash, 1);
        }
    }

    public boolean mightContain(String item) throws JMCInterruptException {
        for (int i = 0; i < hashFunctionsCount; i++) {
            int hash = hash(item, i);
            if (bitSet.get(hash) == 0) {
                return false;
            }
        }
        return true;
    }

    private int hash(String item, int i) {
        int hash = item.hashCode();
        hash = hash ^ i;
        return Math.abs(hash) % size;
    }

    public boolean mightContainProb(String item) {
        for (int i = 0; i < hashFunctionsCount; i++) {
            int hash = hash(item, i);
            if (bitSet.array[hash] == 0) {
                return ThreadLocalRandom.current().nextDouble() < 0.01;
            }
        }
        return true;
    }
}
