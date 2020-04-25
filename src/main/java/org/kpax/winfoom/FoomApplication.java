/*
 * Copyright (c) 2020. Eugen Covaci
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.kpax.winfoom;

import org.kpax.winfoom.view.AppFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.swing.*;
import java.awt.*;

/**
 * The entry point for Winfoom application.
 */
@EnableScheduling
@SpringBootApplication
public class FoomApplication {

    private static final Logger logger = LoggerFactory.getLogger(FoomApplication.class);

    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (Exception e) {
            logger.warn("Failed to set Windows L&F, use the default look and feel", e);
        }

        ConfigurableApplicationContext applicationContext = SpringApplication.run(FoomApplication.class, args);
        final AppFrame frame = applicationContext.getBean(AppFrame.class);
        EventQueue.invokeLater(() -> {
            try {
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                frame.focusOnStartButton();
            } catch (Exception e) {
                logger.error("GUI error", e);
            }
        });
    }

}
