package io.github.gagann06.internshiptracker;

import org.springframework.boot.SpringApplication;

public class TestInternshipTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.from(InternshipTrackerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
