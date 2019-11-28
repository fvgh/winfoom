package org.kpax.winfoom;

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.awt.*;

@SpringBootApplication
public class FoomApplication {

    private static final Logger logger = LoggerFactory.getLogger(FoomApplication.class);

    public static void main(String[] args) {

        // Necessary for tray icon
        System.setProperty("java.awt.headless", "false");

        // Show splash screen
        EventQueue.invokeLater(() -> {
            try {
                new SplashFrame().setVisible(true);
            } catch (Exception e) {
                logger.error("Cannot show splash screen", e);
            }
        });

        // Launch the Javafx application
        Application.launch(JavafxApplication.class, args);
    }

}
