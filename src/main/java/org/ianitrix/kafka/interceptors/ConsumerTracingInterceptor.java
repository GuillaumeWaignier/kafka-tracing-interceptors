package org.ianitrix.kafka.interceptors;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.ianitrix.kafka.interceptors.pojo.TraceType;
import org.ianitrix.kafka.interceptors.pojo.TracingKey;
import org.ianitrix.kafka.interceptors.pojo.TracingValue;

import java.time.Instant;
import java.util.Map;

/**
 * Trace the messages consume inside a Kafka Topic
 * @author Guillaume Waignier
 */
@Slf4j
public class ConsumerTracingInterceptor extends AbstractTracingInterceptor implements ConsumerInterceptor<Object, Object> {

    private String groupId;

    @Override
    public void configure(final Map<String, ?> configs) {
        super.configure(configs);
        this.groupId = (String) configs.get(ConsumerConfig.GROUP_ID_CONFIG);
    }

    @Override
    public void close() {
        super.close();
    }

    @Override
    public ConsumerRecords<Object, Object> onConsume(final ConsumerRecords<Object, Object> records) {
        records.forEach(this::sendConsume);
        return records;
    }

    private void sendConsume(final ConsumerRecord<Object, Object> record) {
        final String correlationId = super.getOrCreateCorrelationID(record.headers());

        final TracingKey key = TracingKey.builder()
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .groupId(this.groupId)
                .build();

        final TracingValue value = TracingValue.builder()
                .topic(record.topic())
                .partition(record.partition())
                .offset(record.offset())
                .correlationId(correlationId)
                .date(Instant.now().toString())
                .groupId(this.groupId)
                .type(TraceType.CONSUME)
                .build();

        super.sendTrace(key, value);
    }

    @Override
    public void onCommit(final Map<TopicPartition, OffsetAndMetadata> offsets) {
        offsets.forEach(this::sendCommit);
    }

    private void sendCommit(final TopicPartition topicPartition, final OffsetAndMetadata offsetAndMetadata) {
        // store the commit offset - 1 since, this offset correspond to next message to consume
        final TracingKey key = TracingKey.builder()
                .topic(topicPartition.topic())
                .partition(topicPartition.partition())
                .offset(offsetAndMetadata.offset() - 1)
                .groupId(this.groupId)
                .build();

        final TracingValue value = TracingValue.builder()
                .topic(topicPartition.topic())
                .partition(topicPartition.partition())
                .offset(offsetAndMetadata.offset() - 1)
                .date(Instant.now().toString())
                .type(TraceType.COMMIT)
                .groupId(this.groupId)
                .build();

        super.sendTrace(key, value);
    }
}
