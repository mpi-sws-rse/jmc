package org.mpisws.concurrent.programs.nondet.filter.bloom;

import org.mpisws.symbolic.SymbolicInteger;
import org.mpisws.util.concurrent.AtomicReferenceArray;
import org.mpisws.util.concurrent.JMCInterruptException;

public class ConcurrentBloomFilter {

    private final AtomicReferenceArray<SymbolicInteger> bitSet;
    private final int size;
    private final int hashFunctionsCount;

    public ConcurrentBloomFilter(int size, int hashFunctionsCount) throws JMCInterruptException {
        this.size = size;
        this.hashFunctionsCount = hashFunctionsCount;
        bitSet = new AtomicReferenceArray<>(size);
        for (int i = 0; i < size; i++) {
            bitSet.set(i, new SymbolicInteger("bitSet[" + i + "]", false));
        }
    }

}
