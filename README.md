
While installing Kafka on your machine is not very difficult, getting a cluster of nodes ready to play with, is somewhat
involved. If you don't want to spend money on AWS or GCP for your learning experiments, here are few simple
steps to get a cluster up and running on your Linux laptop or desktop. All you need is a desktop with Ubuntu and at least 32GB RAM.

This setup tries to mimic a real cluster with multiple servers.The instructions are for an Ubuntu host but should be generally applicable to other Linux variants.

It creates three separate docker containers on the same host. The containers are on a separate docker network - most likely that is the case for a real world cluster.

- Install [docker engine](https://docs.docker.com/engine/install/ubuntu/) on your host.
- git clone this project
- `mkdir data`
- Download JDK 21 and store in data folder
  - `curl -o data/jdk-21.tar.gz https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz` 
- Download and save confluent platform tar.gz file the data directory (save as confluent.tar.gz)
  - `curl -o data/confluent.tar.gz "http://packages.confluent.io/archive/7.7/confluent-7.7.1.tar.gz"`
- Build your docker image which embeds confluent downloaded in last step
  - `docker build -t ubuntu-kafka .`
- Run three node cluster:
  - `BROKER_COUNT=3 ./run.sh`

This will create a kafka cluster of 3 docker nodes called server-0, server-1 and server-2 on a network called my-kafka-cluster-net. 

Create a new topic:

    `docker exec server-1 /opt/software/confluent/bin/kafka-topics --bootstrap-server server-1:9092 --create --topic my-first-topic`

See topic details:
    `docker exec server-1 /opt/software/confluent/bin/kafka-topics --bootstrap-server server-1:9092 --describe --topic my-first-topic`


Shutdown cluster:

`docker kill $(docker ps -a -q) && docker network rm my-kafka-cluster-net`


---------------

Few Kafka basics a developer must know.

Kafka distributes your data across many nodes. Nodes are kept 'in-sync' by an underlying software called Apache Zookeeper. 
Writing of data to the cluster is done by 'kafka producer' library and reading from the cluster is done by Kafka Consumer Library. Your (java) code must utilize these two libraries to interact with the Cluster.  

Each node must be configured with two parameters called listeners and advertised.listeners.

You can find excellent information on listeners and advertised listeners [here](https://rmoff.net/2018/08/02/kafka-listeners-explained/).
Port numbers and host names in listener configurations can get confusing. Remember these two basic but non-obvious facts:
  - Everything starts with port number. If you contact a broker node on port 9000, the advertised listener which is configured for port 9000 (on that node) is returned to you.
  - The Kafka Cluster is not completely _self managed_. Broker/Leader information is exposed to the clients. This is different from other clusters you might be familiar with where
    the client can send it's requests to ANY of the nodes and distribution of data is taken care of without client knowing anything about rebalance, leadership status etc.

This implies your client needs to be aware of how many nodes, Leadership status, port numbers on which to reach each node. Although, these are taken care of by Kafka Client 
libraries, the fact remains that this information is exposed to the client. This means each node's advertised port must be reachable through firewall/load-balancers etc

For example, let's assume following setup:

                Pair1(listener, adv listener)   Pair2(listener, adv listener)
    Broker1:   (server-1:9091,server-1:9091)  (server-1:10091, external-name-broker-1.com:10091)
    Broker2:   (server-2:9091,server-2:9091)  (server-2:10092, external-name-broker-2.com:10092)
    Broker3:   (server-3:9091,server-3:9091)  (server-3:10093, external-name-broker-3.com:10093)
 
So, two pairs are configured for each host. First Element in a pair is telling the broker to listen on a particular port. Second is the corresponding advertised listener.
     
This configuration will be used with as follows.

A client comes calling to broker1 at port number 9091. Broker1 responds with second item in Pair1 - server-1:9091. All further correspondence between this client and broker1 must use (server-1:9091).
This means client must be able to make a connection with the name server-1 at port 9091. 

A client comes calling to broker1 at port number 10091. Broker1 responds with second item in Pair2 : external-name-broker-1.com:10091. All further correspondence between this client and broker1 must use (external-name-broker-1.com:10091).
This means client must be able to make a connection with the name external-name-broker-1.com at port 10091. This is probably the case with Clients outside of the Kafka LAN.
_This also implies the LoadBalance/Firewall between client and the broker node must allow traffic through port 10091_.

Since a client is allowed to connect to any node in the cluster, ALL externally exposed ports must be allowed through the Firewall. In this example, the firewall must allow ports
10091, 10092, 10093.

Other useful fun facts :

  - Kafka Topics have partition Leaders. ALL writes for a topic partition go through _same_ Leader node. Leader is the single point of failure and yes, it might become a
    hotspot if you are unlucky and not watching over your cluster. It's possible for a single node to end up a designated leader for multiple partitions even though other nodes are available to fulfill this role -  (Leader Skew)[https://hashedin.com/blog/re-balance-your-kafka/]
  - In the same vein, due to unlucky turn of events (node failures and node restarts), it's possible for an uneven partition distribution - [Broker Skew](https://hashedin.com/blog/re-balance-your-kafka/)   
  - Kafka cluster has a controller Node which has certain operations going through it (and only through it). Another potential single point of failure and hotspot candidate
  - Good news being, these single points of failures are not clients' responsibilities. Fortunately Kafka (and underlying zookeeper) watch over failing Leaders and work to elect new leaders without
    any manual intervention. Although, mortals like us must watch for Leader Skews and Broker Skews.

How data is distributed
    Data distribution is achieved via breaking a topic into multiple partitions and distributing incoming messages across partitions by keys. As long as your keys are evenly distributed, your
    data will be evenly distributed across brokers. Once again, the responsibility has been pushed up to the clients. 

How data Consumers are distributed
    One consumer process is assigned to each partition - parallelizing the processing of data. There are lots of nuances regarding what to do when a process dies but in effect, it boils down to
    watching for death of a consumer (and birth of new consumers) and dynamically reassigning partitions to newly born consumers or remaining consumers in case of a consumer failure.
    This is done by the controller node automatically - however the Kafka Admins must watch over for [excessive rebalances](https://medium.com/streamthoughts/apache-kafka-rebalance-protocol-or-the-magic-behind-your-streams-applications-e94baf68e4f2). 
     
