package br.com.itau.journey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude={DataSourceAutoConfiguration.class})
@EnableScheduling
@EnableCaching
public class WorkerPollingApplication {

	public static void main(String[] args) {
		SpringApplication.run(WorkerPollingApplication.class, args);
	}

}
