package com.solo.tekton.mq.consumer;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@SpringBootApplication
@EnableAsync
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
		return new KubernetesClientBuilder().withConfig(config).build();
	}

	@Bean
	public ConnectionFactory connectionFactory() {
		CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
		connectionFactory.setAddresses("byai.uk");
		connectionFactory.setUsername("default_user_Gw23yoG82Col_bGTNVQ");
		connectionFactory.setPassword("a-ywM2Nss_05Qzy35Iv99z9MIQpGpXir");
		return connectionFactory;
	}

	@Bean
	public RabbitTemplate rabbitTemplate() {
		return new RabbitTemplate(connectionFactory());
	}

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
	}

}
