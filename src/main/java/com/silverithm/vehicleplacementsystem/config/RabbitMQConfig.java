package com.silverithm.vehicleplacementsystem.config;


import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
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
