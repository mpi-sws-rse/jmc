package org.mpisws.jmc.test.list;

/**
 * DeletionThread is a thread that deletes an item from a set.
 */
public class DeletionThread extends Thread {

    /**
     * The set from which the item will be deleted.
     */
    private final Set set;

    /**
     * The item to be deleted from the set.
     */
    private final int item;

    /**
     * Constructs a DeletionThread with the specified set and item.
     *
     * @param set  the set from which the item will be deleted
     * @param item the item to be deleted
     */
    public DeletionThread(Set set, int item) {
        this.set = set;
        this.item = item;
    }

    /**
     * The run method that is executed when the thread starts.
     * It tries to delete the item from the set.
     */
    @Override
    public void run() {
        set.remove(item);
    }
}
