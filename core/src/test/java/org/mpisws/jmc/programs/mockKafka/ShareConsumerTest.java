package org.mpisws.jmc.programs.mockKafka;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ShareConsumerTest {
//    @Test
    public static void main(String[] args) {
        try
        {
            ShareConsumer<byte[], byte[]> shareConsumer = new ShareConsumer<>();
            Producer<byte[], byte[]> producer = new Producer<>(shareConsumer);

            ProducerRecord<byte[], byte[]> producerRecord1 = new ProducerRecord<>("test-topic", 0,
                    "key_1".getBytes(), "value_1".getBytes());
            ProducerRecord<byte[], byte[]> producerRecord2 = new ProducerRecord<>("test-topic", 0,
                    "key_2".getBytes(), "value_2".getBytes());

            shareConsumer.subscribe(Set.of("test-topic"));

            // Produce first record
            producer.send(producerRecord1);
            producer.flush();

            ConsumerRecords<byte[], byte[]> consumerRecords = shareConsumer.poll(Duration.ofMillis(5000));
            ProducerRecord<byte[], byte[]> consumerRecord = consumerRecords.records("test-topic").get(0);
            assertEquals("key_1", new String(consumerRecord.key()));
            assertEquals("value_1", new String(consumerRecord.value()));
            assertEquals(1, consumerRecords.count());

            consumerRecords = shareConsumer.poll(Duration.ofMillis(1000));
            assertEquals(0, consumerRecords.count());

            // Produce second record
            producer.send(producerRecord2);
            producer.flush();

            consumerRecords = shareConsumer.poll(Duration.ofMillis(5000));
            consumerRecord = consumerRecords.records("test-topic").get(0);
            assertEquals("key_2", new String(consumerRecord.key()));
            assertEquals("value_2", new String(consumerRecord.value()));
            assertEquals(1, consumerRecords.count());

            // Allow acquisition lock to time out
            Thread.sleep(20000);

            consumerRecords = shareConsumer.poll(Duration.ofMillis(5000));
            consumerRecord = consumerRecords.records("test-topic").get(0);
            assertEquals("key_2", new String(consumerRecord.key()));
            assertEquals("value_2", new String(consumerRecord.value()));
            assertEquals(1, consumerRecords.count());

            consumerRecords = shareConsumer.poll(Duration.ofMillis(1000));
            assertEquals(0, consumerRecords.count());
        }
        catch (InterruptedException e) {
            System.out.println("Interrupted");
        }
        catch (IndexOutOfBoundsException e) {
            System.out.println("Index out of bounds");
        }
    }
}

