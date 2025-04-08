package org.mpisws.jmc.programs.kafka;


public class ProducerRecord<K, V> {
    private final String topic;
    private final int partition;
    private final K key;
    private final V value;

    public ProducerRecord(String topic, int partition, K key, V value) {
        this.topic = topic;
        this.partition = partition;
        this.key = key;
        this.value = value;
    }

    public String topic() { return topic; }
    public int partition() { return partition; }
    public K key() { return key; }
    public V value() { return value; }
}

