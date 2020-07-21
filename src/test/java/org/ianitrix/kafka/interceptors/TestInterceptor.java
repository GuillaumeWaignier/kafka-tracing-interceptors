package org.ianitrix.kafka.interceptors;

import lombok.extern.slf4j.Slf4j;
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
import org.ianitrix.kafka.interceptors.pojo.TraceType;
import org.ianitrix.kafka.interceptors.pojo.TracingKey;
import org.ianitrix.kafka.interceptors.pojo.TracingValue;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Testcontainers
@Slf4j
class TestInterceptor {

    private static final String TEST_TOPIC = "test";

    @Container
    private static final KafkaContainer kafka = new KafkaContainer("5.3.2")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
            .withEnv("KAFKA_DEFAULT_REPLICATION_FACTOR", "1")
            .withEnv("KAFKA_NUM_PARTITIONS", "2");

    private static KafkaProducer<String, String> producer;
    private static KafkaConsumer<String, String> consumer;
    private static TraceTopicConsumer traceTopicConsumer;

    private static final String PRODUCER_CLIENT_ID = "producerClientId";
    private static final String CONSUMER_CLIENT_ID = "consumerClientId";
    private static final String CONSUMER_GROUP_ID = "TestInterceptor";

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
        config.put(ProducerConfig.CLIENT_ID_CONFIG, PRODUCER_CLIENT_ID);
        //config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, ProducerTracingInterceptor.class.getName());

        config.put(AbstractTracingInterceptor.CONFIGURATION_PREFIX + ProducerConfig.LINGER_MS_CONFIG, 1);
        producer = new KafkaProducer<>(config);
    }

    private static void createConsumer() {
        final Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        config.put(ConsumerConfig.CLIENT_ID_CONFIG, CONSUMER_CLIENT_ID);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP_ID);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, ConsumerTracingInterceptor.class.getName());
        consumer = new KafkaConsumer<>(config);
    }

    @Test
    void testSendMessage() {

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
        Awaitility.await().atMost(Duration.ofMinutes(5)).until(() -> traceTopicConsumer.orderedTraces.size() == 5);

        log.info("5 messages consumed {}", traceTopicConsumer.orderedTraces.toString());
        Assertions.assertEquals(TraceType.SEND, traceTopicConsumer.orderedTraces.get(0).getType());
        Assertions.assertEquals(TraceType.ACK, traceTopicConsumer.orderedTraces.get(1).getType());
        Assertions.assertEquals(TraceType.CONSUME, traceTopicConsumer.orderedTraces.get(2).getType());
        Assertions.assertEquals(TraceType.COMMIT, traceTopicConsumer.orderedTraces.get(3).getType());
        Assertions.assertEquals(TraceType.COMMIT, traceTopicConsumer.orderedTraces.get(4).getType());

        //send
        final TracingValue send = traceTopicConsumer.orderedTraces.get(0);
        Assertions.assertEquals(TraceType.SEND, send.getType());
        Assertions.assertEquals(TEST_TOPIC, send.getTopic());
        final String correlationId = send.getCorrelationId();
        Assertions.assertNotNull(correlationId);
        Assertions.assertNotNull(send.getDate());
        Assertions.assertNotNull(send.getId());
        Assertions.assertEquals(PRODUCER_CLIENT_ID, send.getClientId());
        final TracingKey keySend = new TracingKey();
        keySend.setCorrelationId(correlationId);
        Assertions.assertEquals(send, traceTopicConsumer.mapTraces.get(keySend));

        // ack
        final TracingValue ack = traceTopicConsumer.orderedTraces.get(1);
        Assertions.assertEquals(TraceType.ACK, ack.getType());
        Assertions.assertEquals(TEST_TOPIC, ack.getTopic());
        Assertions.assertEquals(partition, ack.getPartition());
        Assertions.assertEquals(offset, ack.getOffset());
        Assertions.assertNotNull(ack.getDate());
        Assertions.assertNotNull(ack.getId());
        Assertions.assertEquals(PRODUCER_CLIENT_ID, ack.getClientId());
        final TracingKey keyAck = new TracingKey();
        keyAck.setTopic(TEST_TOPIC);
        keyAck.setPartition(partition);
        keyAck.setOffset(offset);
        Assertions.assertEquals(ack, traceTopicConsumer.mapTraces.get(keyAck));

        // consume
        final TracingValue consume = traceTopicConsumer.orderedTraces.get(2);
        Assertions.assertNotNull(consume.getDate());
        Assertions.assertNotNull(consume.getId());
        final TracingValue expectedConsume = new TracingValue();
        expectedConsume.setType(TraceType.CONSUME);
        expectedConsume.setTopic(TEST_TOPIC);
        expectedConsume.setPartition(partition);
        expectedConsume.setOffset(offset);
        expectedConsume.setCorrelationId(correlationId);
        expectedConsume.setClientId(CONSUMER_CLIENT_ID);
        expectedConsume.setGroupId(CONSUMER_GROUP_ID);
        expectedConsume.setId(consume.getId());
        expectedConsume.setDate(consume.getDate());
        Assert.assertEquals(expectedConsume, consume);


        // commit for each partition
        for (int i = 0 ; i < 2 ; i++) {
            final TracingKey keyCommit = new TracingKey();
            final TracingValue commit = traceTopicConsumer.orderedTraces.get(3 + i);
            Assertions.assertEquals(TraceType.COMMIT, commit.getType());
            Assertions.assertEquals(TEST_TOPIC, commit.getTopic());
            Assertions.assertEquals(CONSUMER_CLIENT_ID, commit.getClientId());
            Assertions.assertEquals(CONSUMER_GROUP_ID, commit.getGroupId());
            if (commit.getPartition() == partition) {
                Assertions.assertEquals(offset , commit.getOffset());
                keyCommit.setPartition(partition);
                keyCommit.setOffset(offset);
            } else {
                Assertions.assertEquals(-1, commit.getOffset());
                keyCommit.setPartition(commit.getPartition());
                keyCommit.setOffset(-1L);
            }
            Assertions.assertNotNull(commit.getDate());
            Assertions.assertNotNull(commit.getId());
            keyCommit.setTopic(TEST_TOPIC);
            keyCommit.setGroupId(CONSUMER_GROUP_ID);
            Assertions.assertEquals(commit, traceTopicConsumer.mapTraces.get(keyCommit));
        }
    }

}
