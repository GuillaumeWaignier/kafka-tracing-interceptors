package org.ianitrix.kafka.interceptors;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.ianitrix.kafka.interceptors.pojo.TraceType;
import org.ianitrix.kafka.interceptors.pojo.TracingValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Testcontainers
public class TestInterceptor {

    private static final String TEST_TOPIC = "test";

    @Container
    private static final KafkaContainer kafka = new KafkaContainer("5.3.2")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withEnv("KAFKA_DEFAULT_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_NUM_PARTITIONS", "2");

    private static KafkaProducer<String, String> producer;
    private static KafkaConsumer<String, String> consumer;
    private static TraceTopicConsumer traceTopicConsumer;

    @BeforeAll
    public static void globalInit() {
        createProducer();
        createConsumer();
        traceTopicConsumer = new TraceTopicConsumer(kafka.getBootstrapServers());
    }

    private static void createProducer() {
        final Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        //config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, ProducerTracingInterceptor.class.getName());
        producer = new KafkaProducer<>(config);
    }

    private static void createConsumer() {
        final Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "TestInterceptor");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, ConsumerTracingInterceptor.class.getName());
        consumer = new KafkaConsumer<>(config);
    }

    @Test
    public void testSendMessage() {

        //send
        final String key = "A";
        final String value = "value A";
        producer.send(new ProducerRecord<>(TEST_TOPIC, key, value));

        //consume
        int partition;
        long offset;
        consumer.subscribe(Collections.singleton(TEST_TOPIC));
        while(true) {
            final ConsumerRecords<String, String> records = consumer.poll(java.time.Duration.ofMillis(200));
            if (! records.isEmpty()) {
                final ConsumerRecord<String, String> record = records.iterator().next();
                Assertions.assertEquals(key, record.key());
                Assertions.assertEquals(value, record.value());
                partition = record.partition();
                offset = record.offset();
                consumer.commitSync();
                break;
            }
        }

        //check trace
        Awaitility.await().atMost(Duration.ONE_MINUTE).until(() -> traceTopicConsumer.traces.size() == 5);

        //send
        final TracingValue send = traceTopicConsumer.traces.get(0);
        Assertions.assertEquals(TraceType.SEND, send.getType());
        Assertions.assertEquals(TEST_TOPIC, send.getTopic());
        final String correlationId = send.getCorrelationId();
        Assertions.assertNotNull(correlationId);
        Assertions.assertNotNull(send.getDate());

        // ack
        final TracingValue ack = traceTopicConsumer.traces.get(1);
        Assertions.assertEquals(TraceType.ACK, ack.getType());
        Assertions.assertEquals(TEST_TOPIC, ack.getTopic());
        Assertions.assertEquals(partition, ack.getPartition());
        Assertions.assertEquals(offset, ack.getOffset());
        Assertions.assertNotNull(ack.getDate());

        // consume
        final TracingValue consume = traceTopicConsumer.traces.get(2);
        Assertions.assertEquals(TraceType.CONSUME, consume.getType());
        Assertions.assertEquals(TEST_TOPIC, consume.getTopic());
        Assertions.assertEquals(partition, consume.getPartition());
        Assertions.assertEquals(offset, consume.getOffset());
        Assertions.assertEquals(correlationId, consume.getCorrelationId());
        Assertions.assertNotNull(consume.getDate());

        // commit for each partition
        for (int i = 0 ; i < 2 ; i++) {
            final TracingValue commit = traceTopicConsumer.traces.get(3 + i);
            Assertions.assertEquals(TraceType.COMMIT, commit.getType());
            Assertions.assertEquals(TEST_TOPIC, commit.getTopic());
            if (commit.getPartition() == partition) {
                Assertions.assertEquals(offset + 1, commit.getOffset());
            } else {
                Assertions.assertEquals(0, commit.getOffset());
            }
            Assertions.assertNotNull(commit.getDate());
        }


    }

}
