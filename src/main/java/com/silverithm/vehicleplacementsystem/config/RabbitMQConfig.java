package com.silverithm.vehicleplacementsystem.config;


import java.io.FileInputStream;
import java.security.KeyStore;
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

@Configuration
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
    public ConnectionFactory connectionFactory() {

        com.rabbitmq.client.ConnectionFactory rabbitFactory = new com.rabbitmq.client.ConnectionFactory();
        rabbitFactory.setMaxInboundMessageBodySize(1545270062);
        rabbitFactory.setHost(host);
        rabbitFactory.setPort(port);
        rabbitFactory.setUsername(username);
        rabbitFactory.setPassword(password);
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(this.getClass().getClassLoader()
                            .getResourceAsStream("keystore.p12"),
                    "rlawnfpr12".toCharArray());
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

            rabbitFactory.useSslProtocol(sslContext);
        } catch (Exception e) {
            throw new RuntimeException("SSL 설정 실패", e);
        }

        CachingConnectionFactory factory = new CachingConnectionFactory(rabbitFactory);
        return factory;
    }

    @Bean
    public Queue dispatchQueue() {
        return QueueBuilder.durable("dispatch.queue")
                .withArgument("x-dead-letter-exchange", "dispatch.dlx")
                .withArgument("x-dead-letter-routing-key", "dispatch.dead")
                .build();
    }

    @Bean
    public Queue responseQueue() {
        return new Queue("dispatch-response-queue", true);  // durable = true
    }


    @Bean
    public Queue deadLetterQueue() {
        return new Queue("dispatch.dlq");
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange("dispatch.dlx");
    }

    @Bean
    DirectExchange exchange() {
        return new DirectExchange("dispatch.exchange");
    }

    @Bean
    Binding dlqBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with("dispatch.dead");
    }

    @Bean
    Binding queueBinding() {
        return BindingBuilder.bind(dispatchQueue())
                .to(exchange())
                .with("dispatch.route");
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
