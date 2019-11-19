package org.kpax.winfoom;

import javafx.application.Application;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.kpax.winfoom.util.LocalIOUtils;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FoomApplication {

	public static void main(String[] args) {
		Application.launch(JavafxApplication.class, args);
	}

}
