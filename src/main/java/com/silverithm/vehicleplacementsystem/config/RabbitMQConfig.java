package com.silverithm.vehicleplacementsystem.config;


import java.io.FileInputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.host}")
    private String host;

    @Value("${spring.rabbitmq.port}")
    private int port;

    @Value("${spring.rabbitmq.username}")
    private String username;

    @Value("${spring.rabbitmq.password}")
    private String password;

    @Bean
    public ConnectionFactory connectionFactory() throws NoSuchAlgorithmException, KeyManagementException {
        log.info("Initializing RabbitMQ ConnectionFactory with host: {}, port: {}", host, port);

        com.rabbitmq.client.ConnectionFactory rabbitFactory = new com.rabbitmq.client.ConnectionFactory();
        rabbitFactory.setMaxInboundMessageBodySize(1545270062);
        rabbitFactory.setHost(host);
        rabbitFactory.setPort(port);
        rabbitFactory.setUsername(username);
        rabbitFactory.setPassword(password);
//        rabbitFactory.useSslProtocol();
//        try {
//            KeyStore keyStore = KeyStore.getInstance("PKCS12");
//            keyStore.load(this.getClass().getClassLoader()
//                            .getResourceAsStream("keystore.p12"),
//                    "rlawnfpr12".toCharArray());
//            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
//                    TrustManagerFactory.getDefaultAlgorithm());
//            trustManagerFactory.init(keyStore);
//
//            SSLContext sslContext = SSLContext.getInstance("TLS");
//            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
//
//            rabbitFactory.useSslProtocol(sslContext);
//        } catch (Exception e) {
//            throw new RuntimeException("SSL 설정 실패", e);
//        }

        CachingConnectionFactory factory = new CachingConnectionFactory(rabbitFactory);
        log.info("RabbitMQ ConnectionFactory initialized successfully");
        return factory;
    }

    @Bean
    public Queue dispatchQueue() {
        String queueName = "dispatch.queue.temp";
        log.info("Creating dispatch queue: {}", queueName);
        Queue queue = QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", "dispatch.dlx")
                .withArgument("x-dead-letter-routing-key", "dispatch.dead")
                .build();
        log.info("Dispatch queue created: {}", queueName);
        return queue;
    }

    @Bean
    public Queue responseQueue() {
        String queueName = "dispatch-response-queue";
        log.info("Creating response queue: {}", queueName);
        Queue queue = new Queue(queueName, true);  // durable = true
        log.info("Response queue created: {}", queueName);
        return queue;
    }


    @Bean
    public Queue deadLetterQueue() {
        String queueName = "dispatch.dlq";
        log.info("Creating dead-letter queue: {}", queueName);
        return new Queue(queueName);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        String exchangeName = "dispatch.dlx";
        log.info("Creating dead-letter exchange: {}", exchangeName);
        return new DirectExchange(exchangeName);
    }

    @Bean
    DirectExchange exchange() {
        String exchangeName = "dispatch.exchange";
        log.info("Creating main exchange: {}", exchangeName);
        return new DirectExchange(exchangeName);
    }

    @Bean
    Binding dlqBinding() {
        log.info("Binding dead-letter queue to exchange with routing key: dispatch.dead");
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("dispatch.dead");
    }

    @Bean
    Binding queueBinding() {
        log.info("Binding dispatch queue to exchange with routing key: dispatch.route");
        return BindingBuilder.bind(dispatchQueue())
                .to(exchange())
                .with("dispatch.route");
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        log.info("Creating JSON message converter for RabbitMQ");
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        log.info("Creating RabbitTemplate with connection factory");
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        log.info("RabbitTemplate created successfully");
        return template;
    }
}
