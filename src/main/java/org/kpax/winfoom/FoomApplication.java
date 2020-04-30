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

import org.kpax.winfoom.util.JarUtils;
import org.kpax.winfoom.util.SwingUtils;
import org.kpax.winfoom.view.AppFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

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

        // Check version
        try {
            checkAppVersion();
        } catch (Exception e) {
            logger.error("Failed to verify app version", e);
            SwingUtils.showErrorMessage(null, "Failed to verify application version." +
                    "\nRemove the system.properties and user.properties files from <USERDIR>/.winfoom directory then try again.");
            return;
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
                SwingUtils.showErrorMessage(null, "Failed to load the graphical interface." +
                        "\nPlease check the application's log file.");
            }
        });
    }

    /**
     * It verifies whether the existent system.properties file releaseVersion property and
     * the application version (extracted from the MANIFEST file) are the same.
     * If not, the existent *.properties file are moved into a backup location.
     *
     * @throws IOException
     */
    private static void checkAppVersion() throws IOException {

        Path appHomePath = Paths.get(System.getProperty("user.home"), ".winfoom");
        Path systemPropertiesPath = appHomePath.resolve("system.properties");
        if (Files.exists(systemPropertiesPath)) {
            Resource resource = new FileSystemResource(systemPropertiesPath.toFile());
            Properties systemProperties = PropertiesLoaderUtils.loadProperties(resource);
            String existingVersion = systemProperties.getProperty("releaseVersion");
            String actualVersion = JarUtils.getVersion(FoomApplication.class);
            if (!actualVersion.equals(existingVersion)) {
                Path backupDirPath = appHomePath.resolve(existingVersion + "-backup");
                if (!Files.exists(backupDirPath)) {
                    Files.createDirectory(backupDirPath);
                }
                Files.move(systemPropertiesPath, backupDirPath.resolve("system.properties"),
                        StandardCopyOption.REPLACE_EXISTING);
                Path userPropertiesPath = appHomePath.resolve("user.properties");
                if (Files.exists(userPropertiesPath)) {
                    Files.move(userPropertiesPath, backupDirPath.resolve("user.properties"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

    }

}
