package org.ianitrix.kafka.interceptors.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;


/**
 * The value of the tracing messages.
 * @author Guillaume Waignier
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TracingValue {
    private String topic;
    private Integer partition;
    private Long offset;
    private String correlationId;
    private String date;
    private TraceType type;
    private String clientId;
    private String groupId;

    private Long durationMs;
}
