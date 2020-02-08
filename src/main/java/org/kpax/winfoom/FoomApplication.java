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

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.awt.*;

/**
 *  The entry point for Winfoom application.
 */
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
