FROM ubuntu

RUN mkdir -p /opt/software /opt/projects/kafka/{logs,data} /opt/projects/zookeeper/data
COPY data/jdk11.tar.gz /opt/software
COPY data/confluent.tar.gz /opt/software

WORKDIR /opt/software

RUN apt-get update -y \
    && apt-get install -y vim iputils-ping

ENV JAVA_HOME=/opt/software/jdk-11
ENV CONFLUENT_HOME=/opt/software/confluent
RUN tar zxf jdk11.tar.gz \
    && tar zxf confluent.tar.gz \
    && mv jdk-11* jdk-11 \
    && mv confluent-* confluent \
    && echo "export JAVA_HOME=$JAVA_HOME" >> /etc/profile \
    && echo "export CONFLUENT_HOME=$CONFLUENT_HOME" >> /etc/profile \
    && echo "export PATH=$PATH:/opt/software/confluent/bin:/opt/software/jdk-11/bin" >> /etc/profile \
    && rm -f jdk11.tar.gz confluent.tar.gz

COPY entrypoint.sh /opt/software
COPY kafka-reconfigure /opt/projects/kafka-reconfigure-src

WORKDIR /opt/projects/kafka-reconfigure-src
RUN ./gradlew clean install \
    && mv build/install/kafka-reconfigure /opt/projects \
    && chmod +x /opt/software/entrypoint.sh


ENTRYPOINT /opt/software/entrypoint.sh

