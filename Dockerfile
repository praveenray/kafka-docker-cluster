FROM ubuntu

RUN mkdir -p /opt/software /opt/projects/kafka/{logs,data} /opt/projects/zookeeper/data
COPY data/jdk-21.tar.gz /opt/software
COPY data/confluent.tar.gz /opt/software

WORKDIR /opt/software

RUN apt-get update -y \
    && apt-get install -y vim iputils-ping

ENV JAVA_HOME=/opt/software/jdk-21
ENV CONFLUENT_HOME=/opt/software/confluent
RUN tar zxf jdk-21.tar.gz \
    && tar zxf confluent.tar.gz \
    && rm -f jdk-21.tar.gz \
    && rm -f confluent.tar.gz

RUN mv jdk-21* jdk-21 \
    && mv confluent-* confluent \
    && echo "export JAVA_HOME=$JAVA_HOME" >> /etc/profile \
    && echo "export CONFLUENT_HOME=$CONFLUENT_HOME" >> /etc/profile \
    && echo "export PATH=$PATH:/opt/software/confluent/bin:/opt/software/jdk-21/bin" >> /etc/profile

COPY entrypoint.sh /opt/software
COPY kafka-reconfigure /opt/projects/kafka-reconfigure-src

WORKDIR /opt/projects/kafka-reconfigure-src
RUN ./gradlew clean install \
    && mv build/install/kafka-reconfigure /opt/projects \
    && chmod +x /opt/software/entrypoint.sh

ENTRYPOINT /opt/software/entrypoint.sh
