package com.exentricdev.clipshare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication
public class ClipshareApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClipshareApplication.class, args);
	}

}
