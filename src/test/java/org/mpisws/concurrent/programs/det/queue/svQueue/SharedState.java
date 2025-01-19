package org.mpisws.concurrent.programs.det.queue.svQueue;

public class SharedState {

    public boolean enqueue = true;
    public boolean dequeue = false;
    public int[] storedElements;

    public SharedState(int size) {
        // Define the storedElements array with the given size and fill it with -1
        storedElements = new int[size];
        for (int i = 0; i < size; i++) {
            storedElements[i] = -1;
        }
    }
}
