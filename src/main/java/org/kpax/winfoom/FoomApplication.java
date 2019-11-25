package org.kpax.winfoom;

import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.awt.*;

@SpringBootApplication
public class FoomApplication {

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        EventQueue.invokeLater(() -> {
            try {
                SplashFrame frame = new SplashFrame();
                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        Application.launch(JavafxApplication.class, args);
    }

}
