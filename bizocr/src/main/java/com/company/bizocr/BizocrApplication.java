package com.company.bizocr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BizocrApplication {

	public static void main(String[] args) {
		SpringApplication.run(BizocrApplication.class, args);
	}

}
