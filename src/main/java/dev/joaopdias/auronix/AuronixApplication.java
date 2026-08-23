package dev.joaopdias.auronix;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AuronixApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuronixApplication.class, args);
	}

}
