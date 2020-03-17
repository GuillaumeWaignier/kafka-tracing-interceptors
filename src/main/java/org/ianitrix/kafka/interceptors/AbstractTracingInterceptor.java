package org.ianitrix.kafka.interceptors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringSerializer;
import org.ianitrix.kafka.interceptors.pojo.TracingKey;
import org.ianitrix.kafka.interceptors.pojo.TracingValue;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Common class to trace message
 * @author Guillaume Waignier
 */
@Slf4j
public abstract class AbstractTracingInterceptor  {

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String TRACE_TOPIC = "_tracing";
    public static final String CONFIGURATION_PREFIX = "ianitrix.interceptor.";

    private final ObjectMapper mapper = new ObjectMapper();

    private KafkaProducer<String, String> producer;
    private String clientId;
    private String groupId;

    protected void configure(final Map<String, ?> configs) {

        this.clientId = (String) configs.get(ProducerConfig.CLIENT_ID_CONFIG);
        this.groupId = (String) configs.get(ConsumerConfig.GROUP_ID_CONFIG);

        // Init with the same config from broker
        final Map<String, Object> producerConfig = new HashMap<>(configs);

        // set default interceptor config
        this.setDefaultConfig(producerConfig);

        // override with custom config
        this.overrideConfig(producerConfig);

        this.producer = new KafkaProducer<>(producerConfig);
    }

    private void setDefaultConfig(final Map<String, Object> producerConfig) {
        producerConfig.remove(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        //producerConfig.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producerConfig.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, producerConfig.getOrDefault(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip"));
        producerConfig.put(ProducerConfig.CLIENT_ID_CONFIG, producerConfig.getOrDefault(ProducerConfig.CLIENT_ID_CONFIG, "") + "_interceptor");
    }

    private void overrideConfig(final Map<String, Object> producerConfig) {
        final LinkedList<String> customKey = producerConfig.keySet().stream()
                .filter(key -> key.startsWith(CONFIGURATION_PREFIX))
                .map(key -> key.substring(CONFIGURATION_PREFIX.length()))
                .collect(Collectors.toCollection(LinkedList::new));

        customKey.forEach(key -> producerConfig.put(key, producerConfig.get(CONFIGURATION_PREFIX+key)));
    }

    protected void close() {
        this.producer.close();
    }

    protected String getOrCreateCorrelationID(final Headers headers) {
        final Header correlationIdHeader = headers.lastHeader(CORRELATION_ID_KEY);

        if (correlationIdHeader == null) {
            final String correlationId = UUID.randomUUID().toString();
            headers.add(CORRELATION_ID_KEY, correlationId.getBytes(StandardCharsets.UTF_8));
            return correlationId;
        }
        return new String(correlationIdHeader.value(), StandardCharsets.UTF_8);
    }

    protected void sendTrace(final TracingKey key, final TracingValue value) {

        value.setClientId(this.clientId);
        value.setGroupId(this.groupId);

        try {
            final String keyJson = this.mapper.writeValueAsString(key);
            final String valueJson = this.mapper.writeValueAsString(value);

            final ProducerRecord<String, String> traceRecord = new ProducerRecord<>(TRACE_TOPIC, keyJson, valueJson);
            this.producer.send(traceRecord);
        } catch (final JsonProcessingException e) {
            log.error("Impossible to send Trace with key={}, value={}", key, value, e);
        }
    }

}
