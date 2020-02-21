package org.ianitrix.kafka.interceptors;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.ianitrix.kafka.interceptors.pojo.TraceType;
import org.ianitrix.kafka.interceptors.pojo.TracingKey;
import org.ianitrix.kafka.interceptors.pojo.TracingValue;

import java.time.Instant;
import java.util.Map;

/**
 * Trace the messages produce inside a Kafka Topic
 */
@Slf4j
public class ProducerTracingInterceptor extends AbstractTracingInterceptor implements ProducerInterceptor<Object, Object> {

    @Override
    public void configure(final Map<String, ?> configs) {
        super.configure(configs);
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public ProducerRecord<Object, Object> onSend(final ProducerRecord<Object, Object> record) {

        final String correlationId = super.getOrCreateCorrelationID(record.headers());

        final TracingKey key = new TracingKey(record.topic(), null, null);
        final TracingValue value = new TracingValue(record.topic(), null, null, correlationId, Instant.now().toString(), TraceType.SEND);

        super.sendTrace(key, value);
        return record;
    }

    @Override
    public void onAcknowledgement(final RecordMetadata metadata, final Exception exception) {
        if (metadata != null) {
            final TracingKey key = new TracingKey(metadata.topic(), metadata.partition(), metadata.offset());
            final TracingValue value = new TracingValue(metadata.topic(), metadata.partition(), metadata.offset(), null, Instant.now().toString(), TraceType.ACK);
            super.sendTrace(key, value);
        } else {
            //TODO handle error
            log.error("Error in ack", exception);
        }
    }
}
