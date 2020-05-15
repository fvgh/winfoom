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

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.util.InputOutputs;
import org.kpax.winfoom.util.SwingUtils;
import org.kpax.winfoom.view.AppFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

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
     * the application version (extracted from the MANIFEST file) are the same or backward compatible.
     * If not, the existent *.properties file are moved into a backup location.
     *
     * @throws IOException
     * @throws ConfigurationException
     */
    private static void checkAppVersion() throws IOException, ConfigurationException {
        logger.info("Check the application's version");
        Path appHomePath = Paths.get(System.getProperty("user.home"), SystemConfig.APP_HOME_DIR_NAME);
        if (Files.exists(appHomePath)) {
            Path proxyConfigPath = appHomePath.resolve(ProxyConfig.FILENAME);
            if (Files.exists(proxyConfigPath)) {
                Configuration proxyConfig = new Configurations()
                        .propertiesBuilder(proxyConfigPath.toFile()).getConfiguration();
                String existingVersion = proxyConfig.getString("app.version");
                logger.info("existingVersion [{}]", existingVersion);
                if (existingVersion != null) {
                    String actualVersion = FoomApplication.class.getPackage().getImplementationVersion();
                    logger.info("actualVersion [{}]", actualVersion);

                    if (actualVersion != null && !actualVersion.equals(existingVersion)) {
                        boolean isCompatibleProxyConfig = true;
                        if (Files.exists(proxyConfigPath)) {
                            isCompatibleProxyConfig = InputOutputs.isProxyConfigCompatible(proxyConfig);
                        }
                        logger.info("The existent proxy config is compatible with the new one: {}", isCompatibleProxyConfig);

                        if (!isCompatibleProxyConfig) {
                            logger.info("Backup the existent proxy.properties file since is invalid" +
                                    " (from a previous incompatible version)");
                            InputOutputs.backupFile(proxyConfigPath,
                                    true,
                                    StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                } else {
                    logger.info("Version not found within proxy.properties, " +
                            "backup both config files since they are invalid (from a previous incompatible version)");
                    InputOutputs.backupFile(proxyConfigPath, true, StandardCopyOption.REPLACE_EXISTING);
                    InputOutputs.backupFile(appHomePath.resolve(SystemConfig.FILENAME),
                            true,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } else {
                logger.info("No proxy.properties found, backup the system.properties file " +
                        "since is invalid (from a previous incompatible version)");
                InputOutputs.backupFile(appHomePath.resolve(SystemConfig.FILENAME),
                        true,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

}
