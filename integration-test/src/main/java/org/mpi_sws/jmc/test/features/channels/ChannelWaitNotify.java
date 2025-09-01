package org.mpi_sws.jmc.test.features.channels;

import org.mpi_sws.jmc.api.JmcObject;

public class ChannelWaitNotify implements Channel {

    private Object item;

    public ChannelWaitNotify() {
        this.item = null;
    }

    public synchronized void send(Object item) throws InterruptedException {
        while (this.item != null) {
            JmcObject.objectWait(this);
        }
        this.item = item;
        JmcObject.objectNotify(this);
    }

    public synchronized Object receive() throws InterruptedException {
        while (this.item == null) {
            JmcObject.objectWait(this);
        }
        Object receivedItem = this.item;
        this.item = null;
        JmcObject.objectNotify(this);
        return receivedItem;
    }
}
