# kafka-tracing-interceptors

[![Build status](https://travis-ci.org/GuillaumeWaignier/kafka-tracing-interceptors.svg?branch=master)](https://travis-ci.org/GuillaumeWaignier/kafka-tracing-interceptors) [![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=org.ianitrix.kafka%3Atracing-interceptors&metric=alert_status)](https://sonarcloud.io/dashboard?id=org.ianitrix.kafka%3Atracing-interceptors)

Kafka Interceptors used to trace messages produced and consumed.

They can be used in java producer/consumer, kafka connect and kafka stream applications.

# Usage

Add the interceptor class in the kafka producer/consumer configuration:

## Example in Java

Producer (use **ProducerConfig.INTERCEPTOR\_CLASSES\_CONFIG**):

````java
final Map<String, Object> config = new HashMap<>();
config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
config.put(ProducerConfig.ACKS_CONFIG, "all");
config.put(ProducerConfig.CLIENT_ID_CONFIG, "example");
config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
config.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, ProducerTracingInterceptor.class.getName());

final KafkaProducer<String, String> producer = new KafkaProducer<>(config);
````

Consumer (use **ConsumerConfig.INTERCEPTOR\_CLASSES\_CONFIG**):

```java
final Map<String, Object> config = new HashMap<>();
config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
config.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
config.put(ConsumerConfig.CLIENT_ID_CONFIG, "example");
config.put(ConsumerConfig.GROUP_ID_CONFIG, "myGroupId");
config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
config.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, ConsumerTracingInterceptor.class.getName());

final KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config);
```

## Example with configuration file

Producer:

````bash
interceptor.classes=org.ianitrix.kafka.interceptors.ProducerTracingInterceptor
````

Consumer:

````bash
interceptor.classes=org.ianitrix.kafka.interceptors.ConsumerTracingInterceptor
````

# Tracing

All traces are kafka messages sent to the topic **\_tracing**.

## Send

key:
````json
{"correlationId":"af8074bc-a042-46ef-8064-203fa26cd9b3"}
````

value:

````json
{
  "topic": "test",
  "correlationId": "af8074bc-a042-46ef-8064-203fa26cd9b3",
  "date": "2020-03-19T08:57:27.367553Z",
  "type": "SEND",
  "clientId": "myProducer"
}
````

## Ack

key:
````json
{"topic":"test","partition":0,"offset":0}
````

value:
````json
{
  "topic": "test",
  "partition": 0,
  "offset": 0,
  "date": "2020-03-19T08:57:35.833484Z",
  "type": "ACK",
  "clientId": "myProducer"
}
````

## Consume

key:
````json
{"topic":"test","partition":0,"offset":0}
````

value:
````json
{
  "topic": "test",
  "partition": 0,
  "offset": 0,
  "correlationId": "af8074bc-a042-46ef-8064-203fa26cd9b3",
  "date": "2020-03-19T08:57:38.834501Z",
  "type": "CONSUME",
  "clientId": "myConsumer",
  "groupId": "myGroup"
}
````

## Commit

key:
````json
{"topic":"test","partition":0,"offset":33}
````

value:
````json
{
  "topic": "test",
  "partition": 0,
  "offset": 33,
  "date": "2020-03-19T08:57:43.801418Z",
  "type": "COMMIT",
  "clientId": "myConsumer",
  "groupId": "myGroup"
}
````


# Dashboard

The kafka stream application [kafka-tracing-aggregator](https://github.com/GuillaumeWaignier/kafka-tracing-aggregator) enriches the traces
and provide Kibana dashboard.

# Test

A docker-compose file provides a full environment with kafka and a simple producer/consumer.

````bash
cd /src/test/resources
docker-compose up -d
````

Then open [http://localhost:8080/](http://localhost:8080/)
