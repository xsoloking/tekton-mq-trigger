package com.solo.tekton.mq.consumer;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@SpringBootApplication
@EnableAsync
@Slf4j
public class TektonMqTriggerApplication {


	@Value("${flow.mq.exchange}")
	private String topicExchangeName;

	@Value("${flow.k8s.token}")
	private String k8sToken;
	@Value("${flow.k8s.url}")
	private String k8sUrl;

	@Bean
	public KubernetesClient kubernetesClient() {
		Config config = new ConfigBuilder()
				.withMasterUrl(k8sUrl)
				.withOauthToken(k8sToken)
				.withTrustCerts()
				.build();
		if (k8sUrl.equals("built-in-k3s")) {
			config = new ConfigBuilder().withAutoConfigure().withTrustCerts().build();
		}
		return new KubernetesClientBuilder().withConfig(config).build();
	}

//	@Bean
//	public ConnectionFactory connectionFactory() {
//		CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
//		connectionFactory.setAddresses("byai.uk");
//		connectionFactory.setUsername("default_user_Gw23yoG82Col_bGTNVQ");
//		connectionFactory.setPassword("a-ywM2Nss_05Qzy35Iv99z9MIQpGpXir");
//		return connectionFactory;
//	}
//
//	@Bean
//	public RabbitTemplate rabbitTemplate() {
//		return new RabbitTemplate(connectionFactory());
//	}

	@Bean
	public Executor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(10);
		executor.setQueueCapacity(500);
		executor.setThreadNamePrefix("LogRedirector-");
		executor.setAllowCoreThreadTimeOut(false);
		executor.initialize();
		return executor;
	}

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

	public static void main(String[] args) {
		SpringApplication.run(TektonMqTriggerApplication.class, args);
		log.debug("TektonMqTriggerApplication started, ver: 0906-v0.5.4");
	}

}
