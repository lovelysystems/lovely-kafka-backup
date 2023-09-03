FROM confluentinc/cp-kafka-connect-base:7.4.0

COPY confluent-connect-libs/ /usr/share/java/confluent-connect/

COPY entrypoint.sh /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
