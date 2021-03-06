package org.ianitrix.kafka.interceptors.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

/**
 * The key of the tracing messages.
 * @author Guillaume Waignier
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TracingKey {
    private String topic;
    private Integer partition;
    private Long offset;
    private String groupId;
    private String correlationId;
}
