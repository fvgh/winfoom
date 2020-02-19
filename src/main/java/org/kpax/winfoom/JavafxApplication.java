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
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.proxy.LocalProxyServer;
import org.kpax.winfoom.util.GuiUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * The entry point for Javafx. It will configure and launch Spring's context.
 */
public class JavafxApplication extends Application {

    private final Logger logger = LoggerFactory.getLogger(JavafxApplication.class);

    private ConfigurableApplicationContext applicationContext;

    private Stage primaryStage;

    @Override
    public void init() {
        try {
            ApplicationContextInitializer<GenericApplicationContext> initializer = genericApplicationContext -> {
                genericApplicationContext.registerBean(JavafxApplication.class, () -> JavafxApplication.this);
                genericApplicationContext.registerBean(FileBasedConfigurationBuilder.class, () -> new Configurations()
                        .propertiesBuilder(Paths.get(System.getProperty("user.dir"), "config",
                                "user.properties").toFile()));
            };
            SpringApplication springApplication = new SpringApplication(FoomApplication.class);
            springApplication.addInitializers(initializer);
            this.applicationContext = springApplication.run(getParameters().getRaw().toArray(new String[0]));
        } finally {
            // Initializing Spring context is
            // the only time consuming task,
            // so we close the splash screen.
            GuiUtils.closeAllAwtWindows();
        }

    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        // Preventing exit on close
        Platform.setImplicitExit(false);

        Resource fxml = this.applicationContext.getResource("classpath:/view/main.fxml");
        FXMLLoader fxmlLoader = new FXMLLoader(fxml.getURL());
        fxmlLoader.setControllerFactory(this.applicationContext::getBean);
        Parent root = fxmlLoader.load();

        Scene scene = new Scene(root);
        this.primaryStage.setScene(scene);
        this.primaryStage.setTitle("WinFoom");
        this.primaryStage.getIcons().add(
                new javafx.scene.image.Image(Paths.get("./config/img/icon.png").toUri().toURL().toExternalForm()));

        if (SystemTray.isSupported()) {
            Image iconImage = Toolkit.getDefaultToolkit().getImage("config/img/icon.png");
            final TrayIcon trayIcon = new TrayIcon(iconImage, "Basic Proxy Facade");
            trayIcon.setImageAutoSize(true);
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    Platform.runLater(() -> {
                        primaryStage.setIconified(false);
                        primaryStage.show();
                    });
                }
            });
            final SystemTray tray = SystemTray.getSystemTray();
            this.primaryStage.iconifiedProperty().addListener((observableValue, oldVal, newVal) -> {
                if (newVal != null) {
                    if (newVal) {
                        try {
                            tray.add(trayIcon);
                            Platform.runLater(primaryStage::hide);
                        } catch (AWTException ex) {
                            logger.error("Cannot add icon to tray", ex);
                        }
                    } else {
                        tray.remove(trayIcon);
                    }
                }
            });
        } else {
            logger.warn("Icon tray not supported!");
        }

        this.primaryStage.show();

        scene.getWindow().addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
            if (this.applicationContext.getBean(LocalProxyServer.class).isStarted()) {

                // Get the pressed button
                ButtonType buttonType = GuiUtils.showCloseAppAlertAndWait();

                if (buttonType == ButtonType.OK) {
                    Platform.exit();
                } else {
                    event.consume();
                }

            } else {
                Platform.exit();
            }
        });
    }

    @Override
    public void stop() {
        logger.info("Close the Spring context");
        this.applicationContext.close();
    }


    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public void sizeToScene() {
        primaryStage.sizeToScene();
    }

    public BorderPane getMainContainer() {
        return (BorderPane) primaryStage.getScene().getRoot();
    }


}
