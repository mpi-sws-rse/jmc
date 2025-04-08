package org.mpisws.jmc.programs.kafka;

import java.util.Iterator;
import java.util.List;

public class ConsumerRecords<K, V> {
    private final List<ProducerRecord<K, V>> records;

    public ConsumerRecords(List<ProducerRecord<K, V>> records) {
        this.records = records;
    }

    public List<ProducerRecord<K, V>> records(String topic) {
        return records;
    }

    public int count() {
        return records.size();
    }

    public Iterator<Object> iterator() {
        return null;
    }
}

