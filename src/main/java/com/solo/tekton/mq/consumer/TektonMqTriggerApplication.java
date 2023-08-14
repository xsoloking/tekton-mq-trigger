package com.solo.tekton.mq.consumer;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.tekton.client.DefaultTektonClient;
import io.fabric8.tekton.client.TektonClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TektonMqTriggerApplication {


	static final String topicExchangeName = "flow-tasks";

	static final String queueName = "tasks-triggered";

	static final String k8sToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6Im5sVFE5bERyTm95Vk9RT0o1QzdydWlaOFhSd0w3dEZIMFM4QVAxdDhFc1kifQ.eyJhdWQiOlsiaHR0cHM6Ly9rdWJlcm5ldGVzLmRlZmF1bHQuc3ZjLmNsdXN0ZXIubG9jYWwiLCJrM3MiXSwiZXhwIjo1MjkxMzE5Mjg3LCJpYXQiOjE2OTEzMjI4ODcsImlzcyI6Imh0dHBzOi8va3ViZXJuZXRlcy5kZWZhdWx0LnN2Yy5jbHVzdGVyLmxvY2FsIiwia3ViZXJuZXRlcy5pbyI6eyJuYW1lc3BhY2UiOiJkZWZhdWx0Iiwic2VydmljZWFjY291bnQiOnsibmFtZSI6ImFkbWluLXVzZXIiLCJ1aWQiOiJkYjhhOWJmYi00OTYwLTQ2MjYtYjEzMy0xMmE1YWQ1MmI1ZmEifX0sIm5iZiI6MTY5MTMyMjg4Nywic3ViIjoic3lzdGVtOnNlcnZpY2VhY2NvdW50OmRlZmF1bHQ6YWRtaW4tdXNlciJ9.YLo_7lptDoANVf9Dd8eUY53FY5JsnSNHoKgFEggxUDRbEjrA8j-6Awu2sbnqvYmEkKbfsWG6v5lwArg4zFEhGRZdnlOsjOi93QJS9rJQrq2k_U9zzZ_MbSSs-YLisbKcnQ1bb7dgFvGOuOLDZ7Yzv5cxGUcua1v3SMR6Fc7k2RCTRHq9jVc2CPQQ_w0fXF0LZCUOWINQtEM38CKGfijCPuIVyis5bhhNktHPtm5EMeOanjAxsOELv9UCzTNhT5oI0-RgKnlJR4r7iTkCirWdPn1J98AR5glwKM-VZ3QST2T5PNl7NcLYOKL1zrvDA1jGze9gqj-RCENa_sge29NxMw";

	static final String k8sUrl = "https://131.186.20.125:6443";


	@Bean
	public TektonClient tektonClient() {
		Config config = new ConfigBuilder()
				.withMasterUrl(k8sUrl)
				.withOauthToken(k8sToken)
				.withTrustCerts()
				.build();
		return new DefaultTektonClient(config);
	}

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
	Queue queue() {
		return new Queue(queueName, false);
	}

	@Bean
	TopicExchange exchange() {
		return new TopicExchange(topicExchangeName);
	}

	@Bean
	Binding binding(Queue queue, TopicExchange exchange) {
		return BindingBuilder.bind(queue).to(exchange).with("trigger");
	}

	public static void main(String[] args) {
		SpringApplication.run(TektonMqTriggerApplication.class, args);
	}

}
