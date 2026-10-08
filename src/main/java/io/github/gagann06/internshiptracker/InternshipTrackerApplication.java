package io.github.gagann06.internshiptracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InternshipTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(InternshipTrackerApplication.class, args);
	}

}
