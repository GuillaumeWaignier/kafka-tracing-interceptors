package org.ianitrix.kafka.interceptors;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.ianitrix.kafka.interceptors.pojo.TraceType;
import org.ianitrix.kafka.interceptors.pojo.TracingKey;
import org.ianitrix.kafka.interceptors.pojo.TracingValue;

import java.time.Instant;
import java.util.Map;

/**
 * Trace the messages consume inside a Kafka Topic
 */
@Slf4j
public class ConsumerTracingInterceptor extends AbstractTracingInterceptor implements ConsumerInterceptor<Object, Object> {

    @Override
    public void configure(final Map<String, ?> configs) {
        super.configure(configs);
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public ConsumerRecords<Object, Object> onConsume(final ConsumerRecords<Object, Object> records) {
        records.forEach(record -> sendConsume(record));
        return records;
    }

    private void sendConsume(final ConsumerRecord<Object, Object> record) {
        final String correlationId = super.getOrCreateCorrelationID(record.headers());

        final TracingKey key = new TracingKey(record.topic(), record.partition(), record.offset());
        final TracingValue value = new TracingValue(record.topic(), record.partition(), record.offset(), correlationId, Instant.now().toString(), TraceType.CONSUME);

        super.sendTrace(key, value);
    }

    @Override
    public void onCommit(final Map<TopicPartition, OffsetAndMetadata> offsets) {
        offsets.forEach((topicPartition, offsetAndMetadata) -> sendAck(topicPartition, offsetAndMetadata));
    }

    private void sendAck(final TopicPartition topicPartition, final OffsetAndMetadata offsetAndMetadata) {
        final TracingKey key = new TracingKey(topicPartition.topic(), topicPartition.partition(), offsetAndMetadata.offset());
        final TracingValue value = new TracingValue(topicPartition.topic(), topicPartition.partition(), offsetAndMetadata.offset(), null, Instant.now().toString(), TraceType.COMMIT);

        super.sendTrace(key, value);
    }
}
