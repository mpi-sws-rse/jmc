package org.mpisws.jmc.programs.mockKafka;


public class Producer<K, V> {
    private final ShareConsumer<K, V> shareConsumer;

    public Producer(ShareConsumer<K, V> shareConsumer) {
        this.shareConsumer = shareConsumer;
    }

    public void send(ProducerRecord<K, V> record) {
        shareConsumer.addRecord(record);
    }

    public void flush() {
        // Simulate flush operation
    }
}

