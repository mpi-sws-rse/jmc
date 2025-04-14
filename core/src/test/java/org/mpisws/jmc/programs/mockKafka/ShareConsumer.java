package org.mpisws.jmc.programs.mockKafka;



import java.time.Duration;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ShareConsumer<K, V> {
    private final BlockingQueue<ProducerRecord<K, V>> queue = new LinkedBlockingQueue<>();
    private final Set<String> subscribedTopics = new HashSet<>();
    private long lastPollTime = 0;
    private static final long ACQUISITION_LOCK_TIMEOUT = 15000; // 15 seconds

    public void subscribe(Set<String> topics) {
        subscribedTopics.addAll(topics);
    }

    public void addRecord(ProducerRecord<K, V> record) {
        queue.offer(record);
    }

    public ConsumerRecords<K, V> poll(Duration timeout) {
        long now = System.currentTimeMillis();

        if (now - lastPollTime >= ACQUISITION_LOCK_TIMEOUT) {
            queue.clear(); // Simulating broker releasing unacknowledged messages
        }

        lastPollTime = now;
        List<ProducerRecord<K, V>> records = new ArrayList<>();
        ProducerRecord<K, V> record = queue.poll();

        if (record != null) {
            records.add(record);
        }

        return new ConsumerRecords<>(records);
    }
}
