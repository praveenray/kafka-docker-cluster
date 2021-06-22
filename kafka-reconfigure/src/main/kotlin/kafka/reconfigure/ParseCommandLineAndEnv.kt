package kafka.reconfigure

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class ParseCommandLineAndEnv(
    val brokerPrefix: String = "",
    val brokerCount: Int = 1,
    val kafkaHome: Path = Paths.get("/opt/software/confluent"),
    val brokerId: Int = -1,
    val outsideDomainSuffix: String = "my.domain",
    val internalPort: Int = 9092,
    val externalPort: Int = 8092,
    val localhostPort: Int = 7092,
) {
    fun read(args: Array<String>): ParseCommandLineAndEnv {
        return parse(args, readFromEnv()).validate()
    }
    
    private fun validate(): ParseCommandLineAndEnv {
        if (!Files.isDirectory(kafkaHome)) {
            throw IllegalStateException("--kafka-home not supplied or invalid: ${this.kafkaHome}")
        }
        val serverProps = kafkaHome.resolve("etc/kafka/server.properties")
        if (!Files.exists(serverProps)) {
            throw IllegalStateException("server.properties not found at ${serverProps.parent}")
        }
        
        if (brokerId == -1) {
            throw IllegalStateException("--broker-id is not set")
        }
        
        if (brokerCount == -1) {
            throw IllegalStateException("--broker-count is not set")
        }

        if (brokerPrefix.isNullOrBlank()) {
            throw IllegalStateException("--broker-prefix is not set")
        }
        return this
    }
    
    private fun parse(args: Array<String>, fromEnv: ParseCommandLineAndEnv): ParseCommandLineAndEnv {
        val eq = Regex("=")
        return args.fold(fromEnv) { parsed, arg ->
            val tokens = arg.split(eq).map(String::trim)
            when (tokens.first()) {
                "--broker-prefix" -> parsed.copy(brokerPrefix = tokens.last())
                "--broker-count" -> parsed.copy(brokerCount = tokens.last().toInt())
                "--kafka-home" -> parsed.copy(kafkaHome = Paths.get(tokens.last()))
                "--broker-id" -> parsed.copy(brokerId = tokens.last().toInt())
                "--outside-domain-prefix" -> parsed.copy(outsideDomainSuffix = tokens.last())
                "--internal-port" -> parsed.copy(internalPort = tokens.last().toInt())
                "--external-port" -> parsed.copy(externalPort = tokens.last().toInt())
                "--localhost-port" -> parsed.copy(localhostPort = tokens.last().toInt())
                else -> parsed
            }
        }
    }

    private fun readFromEnv(): ParseCommandLineAndEnv {
        val commands = ParseCommandLineAndEnv()

        val brokerPrefix = System.getenv("BROKER_PREFIX")
        val withPrefix = if (!brokerPrefix.isNullOrBlank()) commands.copy(brokerPrefix = brokerPrefix) else commands

        val brokerId = System.getenv("BROKER_ID")
        val withId = if (!brokerId.isNullOrBlank()) withPrefix.copy(brokerId = brokerId.toInt()) else withPrefix
    
        val brokerCount = System.getenv("BROKER_COUNT")
        val withCount = if (!brokerCount.isNullOrBlank()) withId.copy(brokerCount = brokerCount.toInt()) else withId

        val kafkaHome = System.getenv("KAFKA_HOME")
        val withKafkaHome = if (!kafkaHome.isNullOrBlank()) withCount.copy(kafkaHome = Path.of(kafkaHome)) else withCount
        val withConfluentHome = if (withKafkaHome == withCount) {
            val confluentHome = System.getenv("CONFLUENT_HOME")
            if (!confluentHome.isNullOrBlank()) withKafkaHome.copy(kafkaHome = Path.of(confluentHome)) else withKafkaHome
        } else withKafkaHome
        
        val outsidePrefix = System.getenv("OUTSIDE_DOMAIN_PREFIX")
        val withOutsidePrefix = if (!outsidePrefix.isNullOrBlank()) withConfluentHome.copy(outsideDomainSuffix = outsidePrefix) else withConfluentHome
        
        val internalPort = System.getenv("INTERNAL_PORT")
        val withInternalPort = if (!internalPort.isNullOrBlank()) withOutsidePrefix.copy(internalPort = internalPort.toInt()) else withOutsidePrefix
    
        val externalPort = System.getenv("EXTERNAL_PORT")
        val withExternalPort = if (!externalPort.isNullOrBlank()) withInternalPort.copy(externalPort = externalPort.toInt()) else withInternalPort
    
        val localhostPort = System.getenv("LOCALHOST_PORT")
        return if (!localhostPort.isNullOrBlank()) withExternalPort.copy(localhostPort = localhostPort.toInt()) else withExternalPort
    }
}