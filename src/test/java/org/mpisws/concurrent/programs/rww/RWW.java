package org.mpisws.concurrent.programs.rww;

import java.util.ArrayList;
import java.util.List;

public class RWW {

    public static void main(String[] args) {
        Shared shared = new Shared(0);
        List<ThreadA> threads = new ArrayList<ThreadA>();
        int numThreads = 10;
        for (int i = 0; i < numThreads; i++) {
            ThreadA thread = new ThreadA(shared);
            threads.add(thread);
        }
        for (int i = 0; i < numThreads; i++) {
            threads.get(i).start();
        }

        /*for (int i = 0; i < numThreads; i++) {
            try {
                threads.get(i).join();
            } catch (InterruptedException e) {

            }
        }*/
    }
}
