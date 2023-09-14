FROM confluentinc/cp-kafka-connect-base:7.4.0 as builder
ARG JMX_EXPORTER_VERSION=0.20.0

RUN mkdir -p /tmp/jmx_exporter \
    && curl -Lo jmx_prometheus_javaagent.jar https://repo.maven.apache.org/maven2/io/prometheus/jmx/jmx_prometheus_javaagent/${JMX_EXPORTER_VERSION}/jmx_prometheus_javaagent-${JMX_EXPORTER_VERSION}.jar \
    && mv jmx_prometheus_javaagent.jar /tmp/jmx_exporter

FROM confluentinc/cp-kafka-connect-base:7.4.0

COPY --from=builder /tmp/jmx_exporter /opt/
COPY confluent-connect-libs/ /usr/share/java/confluent-connect/
COPY backup-cli-libs/ /opt/backup-cli/

COPY kafka-connect.yml /opt/
COPY entrypoint.sh /entrypoint.sh

COPY cli.sh /usr/bin/cli

ENTRYPOINT ["/entrypoint.sh"]
