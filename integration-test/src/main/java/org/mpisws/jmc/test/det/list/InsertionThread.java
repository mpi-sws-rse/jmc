package org.mpisws.jmc.test.det.list;

/**
 * InsertionThread is a thread that inserts an item into a set.
 */
public class InsertionThread extends Thread {

    /**
     * The set into which the item will be inserted.
     */
    private final Set set;

    /**
     * The item to be inserted into the set.
     */
    public final int item;

    /**
     * Constructs an InsertionThread with the specified set and item.
     *
     * @param set  the set into which the item will be inserted
     * @param item the item to be inserted
     */
    public InsertionThread(Set set, int item) {
        this.set = set;
        this.item = item;
    }

    /**
     * The run method that is executed when the thread starts.
     * It tries to insert the item into the set.
     */
    @Override
    public void run() {
        set.add(item);
    }
}
