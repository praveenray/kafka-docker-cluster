package kafka.reconfigure;

public class App {

    public static void main(String[] args) {
        ParseCommandLineAndEnv options = new ParseCommandLineAndEnv().read(args);
        KafkaReconfig kafka = new KafkaReconfig();
        kafka.change(
                options.getKafkaHome(),
                options.getBrokerPrefix(),
                options.getBrokerCount(),
                options.getBrokerId(),
                options.getOutsideDomainSuffix(),
                options.getInternalPort(),
                options.getExternalPort(),
                options.getLocalhostPort()
        );
    }
}
