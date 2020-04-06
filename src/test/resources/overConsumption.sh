#!/bin/sh

set -e

# Read command line
if [ "$#" -eq 2 ]
then
  bootstrap_server=${1}
  number_message=${2}
else
  echo "Usage: ${0} <kafka-bootstrap-server:port> <number-of-messages-to-consume>" >&2
  echo "      exemple: ${0} kafka:9092 1000" >&2
  exit 1
fi

group=myConsumerOver

echo "interceptor.classes=org.ianitrix.kafka.interceptors.ConsumerTracingInterceptor" > /consume.config
echo "client.id=${group}" >> /consume.config

while true
do
  kafka-consumer-perf-test --broker-list ${bootstrap_server} --group ${group} --messages ${number_message} --consumer.config /consume.config --topic test
  kafka-consumer-groups --bootstrap-server kafka:9092 --topic test --to-earliest --reset-offsets --group ${group} --execute

  sleep 60

done

