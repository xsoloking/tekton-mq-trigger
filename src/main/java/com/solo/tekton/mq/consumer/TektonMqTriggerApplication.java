package com.solo.tekton.mq.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@Slf4j
public class TektonMqTriggerApplication {



	public static void main(String[] args) {
		SpringApplication.run(TektonMqTriggerApplication.class, args);
		log.debug("TektonMqTriggerApplication started, ver: 0910-v0.6.0");
	}

}
