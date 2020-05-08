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

import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.util.SwingUtils;
import org.kpax.winfoom.view.AppFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.FileSystemResource;
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
                    "\nRemove the system.properties and proxy.properties files from <USERDIR>/.winfoom directory then" +
                    " try again.");
            System.exit(1);
        }

        logger.info("Bootstrap Spring's application context");
        ApplicationContext applicationContext = SpringApplication.run(FoomApplication.class, args);

        logger.info("Launch the GUI");
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
     * Verify whether the existent system.properties file's releaseVersion property and
     * the application version (extracted from the MANIFEST file) are the same.
     * If not, the existent *.properties file are moved into a backup location.
     *
     * @throws IOException
     */
    private static void checkAppVersion() throws IOException {
        logger.info("Check the application's version");
        Path appHomePath = Paths.get(System.getProperty("user.home"), SystemConfig.APP_HOME_DIR_NAME);
        Path systemPropertiesPath = appHomePath.resolve(SystemConfig.FILENAME);

        if (Files.exists(systemPropertiesPath)) {
            Properties systemProperties = PropertiesLoaderUtils.loadProperties(
                    new FileSystemResource(systemPropertiesPath.toFile()));
            String existingVersion = systemProperties.getProperty("app.version");
            logger.info("existingVersion [{}]", existingVersion);
            String actualVersion = FoomApplication.class.getPackage().getImplementationVersion();
            logger.info("actualVersion [{}]", actualVersion);

            if (actualVersion != null && !actualVersion.equals(existingVersion)) {
                logger.info("Different versions found: existent = {} , actual = {}", existingVersion, actualVersion);

                Path backupDirPath = appHomePath.resolve(existingVersion);

                int selection = JOptionPane.showConfirmDialog(null,
                        "The configuration files found are from a different application version!\n" +
                                "The existent ones will be overwritten.\n" +
                                String.format("Press 'OK' if you want to save them to %s backup directory.",
                                        backupDirPath.toString()),
                        "Warning",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (selection == JOptionPane.YES_OPTION) {
                    if (!Files.exists(backupDirPath)) {
                        Files.createDirectory(backupDirPath);
                    }

                    logger.info("Move the existent config files to: {} directory", backupDirPath);
                    Files.move(systemPropertiesPath, backupDirPath.resolve(SystemConfig.FILENAME),
                            StandardCopyOption.REPLACE_EXISTING);
                    Path userPropertiesPath = appHomePath.resolve(ProxyConfig.FILENAME);
                    if (Files.exists(userPropertiesPath)) {
                        Files.move(userPropertiesPath, backupDirPath.resolve(ProxyConfig.FILENAME),
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                } else {
                    Files.delete(systemPropertiesPath);
                    Files.deleteIfExists(appHomePath.resolve(ProxyConfig.FILENAME));
                }

            }
        }

    }

}
