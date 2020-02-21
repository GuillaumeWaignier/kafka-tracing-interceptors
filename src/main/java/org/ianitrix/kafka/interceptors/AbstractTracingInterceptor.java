package org.ianitrix.kafka.interceptors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.StringSerializer;
import org.ianitrix.kafka.interceptors.pojo.TracingKey;
import org.ianitrix.kafka.interceptors.pojo.TracingValue;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Common class to trace message
 */
@Slf4j
public abstract class AbstractTracingInterceptor  {

    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String TRACE_TOPIC = "_tracing";
    private static final List<String> COMMON_CONFIG = List.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG);

    private ObjectMapper mapper = new ObjectMapper();
    private KafkaProducer<String, String> producer;

    protected void configure(final Map<String, ?> configs) {
        final Map<String, Object> producerConfig = new HashMap<>();

        COMMON_CONFIG.forEach(conf -> producerConfig.put(conf, configs.get(conf)));

        //TODO: handle secure brokers
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        //producerConfig.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producerConfig.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");

        this.producer = new KafkaProducer<>(producerConfig);
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
