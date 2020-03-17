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
 * @author Guillaume Waignier
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

        final TracingKey key = TracingKey.builder()
                .correlationId(correlationId)
                .build();

        final TracingValue value = TracingValue.builder()
                .topic(record.topic())
                .correlationId(correlationId)
                .date(Instant.now().toString())
                .type(TraceType.SEND)
                .build();

        super.sendTrace(key, value);
        return record;
    }

    @Override
    public void onAcknowledgement(final RecordMetadata metadata, final Exception exception) {
        if (metadata != null) {

            final TracingKey key = TracingKey.builder()
                    .topic(metadata.topic())
                    .partition(metadata.partition())
                    .offset(metadata.offset())
                    .build();

            final TracingValue value =  TracingValue.builder()
                    .topic(metadata.topic())
                    .partition(metadata.partition())
                    .offset(metadata.offset())
                    .date(Instant.now().toString())
                    .type(TraceType.ACK)
                    .build();

            super.sendTrace(key, value);
        } else {
            //TODO handle error
            log.error("Error in ack", exception);
        }
    }
}
