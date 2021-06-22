package kafka.reconfigure

import java.nio.file.*

class KafkaReconfig {
    fun change(
        kafkaHome: Path, brokerPrefix: String, brokerCount: Int, thisBrokerId: Int,
        outsideDomainSuffix: String,
        internalPort: Int,
        externalPort: Int,
        localhostPort: Int,
        ) {
        changeZookeeperProperties(kafkaHome, brokerPrefix, brokerCount, thisBrokerId)
        changeKafkaServerProperties(
            kafkaHome = kafkaHome,
            brokerPrefix = brokerPrefix,
            brokerCount = brokerCount,
            thisBrokerId = thisBrokerId,
            outsideDomainSuffix = outsideDomainSuffix,
            internalPort = internalPort,
            localhostPort = localhostPort,
            externalPort = externalPort,
        )
    }
    
    fun changeZookeeperProperties(kafkaHome: Path, brokerPrefix: String, brokerCount: Int, thisBrokerId: Int) {
        val zookeeperDir = Paths.get("/opt/projects/zookeeper/data")
        val toAdd = mapOf(
            "dataDir" to zookeeperDir.toAbsolutePath().toString(),
            "tickTime" to "2000",
            "initLimit" to "5",
            "syncLimit" to "2",
            "autopurge.snapRetainCount" to "3",
            "autopurge.purgeInterval" to "24",
        ).plus(
            (0 until brokerCount).map { index ->
                "server.$index" to "$brokerPrefix-$index:2888:3888"
            }
        )
        writeZookeeperId(zookeeperDir, thisBrokerId)
        replaceValues(kafkaHome.resolve("etc/kafka/zookeeper.properties"), toAdd)
    }
    
    private fun writeZookeeperId(zookeeperDir: Path, thisBrokerId: Int) {
        val idFile = zookeeperDir.resolve("myid")
        if (!Files.isDirectory(zookeeperDir)) {
            Files.createDirectories(zookeeperDir)
        }
        Files.writeString(idFile, thisBrokerId.toString(), StandardOpenOption.CREATE_NEW)
    }
    
    private fun changeKafkaServerProperties(
        kafkaHome: Path,
        brokerPrefix: String,
        brokerCount: Int,
        thisBrokerId: Int,
        outsideDomainSuffix: String,
        internalPort: Int,
        localhostPort: Int,
        externalPort: Int,
    ) {
        val serverName = "${brokerPrefix}-${thisBrokerId}"
        val zookeeperConnect = (1..brokerCount).joinToString(",") { _ -> "$serverName:2181" }
        val listeners = "LISTENER_INTERNAL://$serverName:$internalPort,LISTENER_LOCAL://$serverName:${localhostPort + thisBrokerId},LISTENER_OUTSIDE://$serverName:${externalPort + thisBrokerId}"
        val advertisedListeners = "LISTENER_INTERNAL://$serverName:$internalPort,LISTENER_LOCAL://localhost:${localhostPort + thisBrokerId},LISTENER_OUTSIDE://$serverName.$outsideDomainSuffix:${externalPort + thisBrokerId}"
        val toAdd = mapOf(
            "broker.id" to thisBrokerId.toString(),
            "listeners" to listeners,
            "advertised.listeners" to advertisedListeners,
            "listener.security.protocol.map" to "LISTENER_INTERNAL:PLAINTEXT,LISTENER_LOCAL:PLAINTEXT,LISTENER_OUTSIDE:PLAINTEXT",
            "inter.broker.listener.name" to "LISTENER_INTERNAL",
            "log.dirs" to "/opt/projects/kafka/logs",
            "num.partitions" to "3",
            "zookeeper.connect" to zookeeperConnect,
            "default.replication.factor" to "3",
            "min.insync.replicas" to "2",
        )
        replaceValues(kafkaHome.resolve("etc/kafka/server.properties"), toAdd)
    }
    
    private fun changeFile(propsFile: Path, toAdd: Map<String, String>) {
        if (!Files.exists(propsFile)) {
            throw IllegalStateException("File not found: $propsFile")
        }
        val lines = propsFile.toFile().readLines()
        var maxLines = lines.size
        val eq = Regex("=")
        val contentsMap = lines.foldIndexed(emptyMap<String, List<Any>>()) { index, accum, line ->
            val key = line.split(eq).map(String::trim).first()
            accum.plus(key to listOf(index, line))
        }
        
        val withNewValues = toAdd.keys.fold(contentsMap) { accum, key ->
            val value = toAdd[key]!!
            if (contentsMap.containsKey(key)) {
                val lineIndex = contentsMap[key]
                if (lineIndex != null) {
                    val lineNum = lineIndex.first() as Int
                    accum.plus(key to listOf(lineNum, "$key=$value"))
                } else accum.plus(key to listOf(maxLines++, "$key=$value"))
            } else accum.plus(key to listOf(maxLines++, "$key=$value"))
        }
        
        val sortedLines = withNewValues.values.sortedBy { line ->
            line.first() as Int
        }.map { it.last() }
        
        propsFile.toFile().writeText(sortedLines.joinToString("\n") + "\n")
    }
    
    private fun replaceValues(propsFile: Path, toAdd: Map<String, String>) {
        if (!Files.exists(propsFile)) {
            throw IllegalStateException("File not found: $propsFile")
        }
        
        val lines = mutableListOf<String>()
        val eq = Regex("=")
        val keysDone = mutableSetOf<String>()
        propsFile.toFile().bufferedReader().use { reader ->
            while (true) {
                val l = reader.readLine() ?: break
                val line = l.trim()
                if (!line.isBlank() && !line.startsWith("#")) {
                    val values = line.split(eq).map(String::trim)
                    val key = values.first()
                    val value = if (toAdd.containsKey(key)) {
                        keysDone.add(key)
                        toAdd[key]
                    } else values.last()
                    lines.add("$key=$value")
                } else lines.add(line)
            }
        }
        val keysLeft = toAdd.keys.minus(keysDone)
        lines.addAll(keysLeft.map { key ->
            "$key=${toAdd[key]}"
        })
        
        Files.writeString(propsFile, lines.joinToString("\n"), StandardOpenOption.TRUNCATE_EXISTING)
    }
}
