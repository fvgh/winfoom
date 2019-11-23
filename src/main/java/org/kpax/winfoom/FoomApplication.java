package org.kpax.winfoom;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FoomApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        Application.launch(JavafxApplication.class, args);
    }

}
