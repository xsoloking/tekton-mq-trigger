package com.solo.tekton.mq.consumer.config;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
public class RabbitMQConfiguration {

    @Value("${flow.mq.exchange}")
    private String topicExchangeName;


    @Bean
    MessagePostProcessor messagePostProcessor() {
        return new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                message.getMessageProperties().setContentType("application/json");
                message.getMessageProperties().setContentEncoding("UTF-8");
                return message;
            }
        };
    }

//	@Bean
//	Queue queue() {
//		return new Queue(queueName, true, false, true);
//	}
//
//	@Bean
//	TopicExchange exchange() {
//		return new TopicExchange(topicExchangeName);
//	}
//
//	@Bean
//	Binding binding(Queue queue, TopicExchange exchange) {
//		return BindingBuilder.bind(queue).to(exchange).with("trigger");
//	}
}
