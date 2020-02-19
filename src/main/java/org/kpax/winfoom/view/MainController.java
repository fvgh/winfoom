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

package org.kpax.winfoom.view;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.WindowEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.HttpHostConnectException;
import org.kpax.winfoom.JavafxApplication;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.proxy.LocalProxyServer;
import org.kpax.winfoom.util.GuiUtils;
import org.kpax.winfoom.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.security.auth.login.CredentialException;
import java.io.IOException;
import java.net.UnknownHostException;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 9/10/2019
 */
@Component
public class MainController {
    final Logger logger = LoggerFactory.getLogger(MainController.class);


    @Autowired
    private LocalProxyServer localProxyServer;

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private JavafxApplication javafxApplication;

    @FXML
    private BorderPane borderPane;

    @FXML
    private TextField proxyHost;

    @FXML
    private TextField proxyPort;

    @FXML
    private TextField localProxyPort;

    @FXML
    private TextField testUrl;

    @FXML
    private Button startBtn;

    @FXML
    private Button stopBtn;

    @FXML
    private MenuBar menuBar;

    @FXML
    private HBox buttonsBox;

    @FXML
    private VBox centerBox;

    @FXML
    public void initialize() {
        proxyHost.setText(userConfig.getProxyHost());
        proxyHost.textProperty().addListener((obs, oldValue, newValue) -> userConfig.setProxyHost(newValue));

        proxyPort.setTextFormatter(GuiUtils.createDecimalOnlyTextFormatter());
        proxyPort.setText("" + userConfig.getProxyPort());

        proxyPort.textProperty().addListener((obs, oldValue, newValue) -> userConfig.setProxyPort(Integer.parseInt(newValue)));

        localProxyPort.setTextFormatter(GuiUtils.createDecimalOnlyTextFormatter());
        localProxyPort.setText("" + userConfig.getLocalPort());
        localProxyPort.textProperty().addListener((obs, oldValue, newValue) -> userConfig.setLocalPort(Integer.parseInt(newValue)));

        testUrl.setText(userConfig.getProxyTestUrl());
        testUrl.textProperty().addListener((obs, oldValue, newValue) -> userConfig.setProxyTestUrl(newValue));

        startedMode(false);
    }

    public void start(ActionEvent actionEvent) {
        if (isValidInput()) {
            try {
                localProxyServer.start();
                startedMode(true);
            } catch (Exception e) {
                logger.error("Error on starting proxy server", e);
                GuiUtils.showMessage(GuiUtils.MessageType.DLG_ERR_TITLE,
                        "Error on starting proxy server.\nSee the application's log for details.");
            }
        }
    }

    private boolean isValidInput() {
        if (StringUtils.isBlank(userConfig.getProxyHost())) {
            GuiUtils.showMessage("Validation Error", "Fill in the proxy address!");
            return false;
        }
        if (userConfig.getProxyPort() < 1) {
            GuiUtils.showMessage("Validation Error", "Fill in a valid proxy port!");
            return false;
        }
        if (userConfig.getLocalPort() < 1024) {
            GuiUtils.showMessage("Validation Error", "Fill in a valid proxy port!");
            return false;
        }
        if (StringUtils.isBlank(userConfig.getProxyTestUrl())) {
            GuiUtils.showMessage("Validation Error", "Fill in the test URL!");
            return false;
        }

        // Test the proxy configuration
        try {
            HttpUtils.testProxyConfig(userConfig);
        } catch (CredentialException e) {
            GuiUtils.showMessage("Test Connection Error", "Wrong user/password!");
            return false;
        } catch (UnknownHostException e) {
            GuiUtils.showMessage("Test Connection Error", "Wrong proxy host!");
            return false;
        } catch (HttpHostConnectException e) {
            GuiUtils.showMessage("Test Connection Error", "Wrong proxy port!");
            return false;
        } catch (IOException e) {
            GuiUtils.showMessage("Test Connection Error", e.getMessage());
            return false;
        }
        return true;
    }

    public void about(ActionEvent actionEvent) {
        GuiUtils.showMessage("About", "Winfoom - Basic Proxy Facade" +
                "\nVersion: " + systemConfig.getReleaseVersion()
                + "\nProject home page: https://github.com/ecovaci/winfoom"
                + "\nLicense: Apache 2.0");
    }

    public void close(ActionEvent actionEvent) {
        menuBar.fireEvent(
                new WindowEvent(javafxApplication.getPrimaryStage().getScene().getWindow(),
                        WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    public void stop(ActionEvent actionEvent) {
        // Get the pressed button
        ButtonType buttonType = GuiUtils.showCloseProxyAlertAndWait();
        if (buttonType == ButtonType.OK) {
            localProxyServer.close();
            startedMode(false);
        }

    }

    private void startedMode (boolean started) {
        centerBox.setDisable(started);
        startBtn.setDisable(started);
        stopBtn.setDisable(!started);
    }
}
