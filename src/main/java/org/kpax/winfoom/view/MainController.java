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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.WindowEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.HttpHostConnectException;
import org.kpax.winfoom.FxApplication;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.proxy.LocalProxyServer;
import org.kpax.winfoom.util.GuiUtils;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.security.auth.login.CredentialException;
import java.io.IOException;
import java.net.UnknownHostException;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 9/10/2019
 */
@Component
@Profile("!test")
public class MainController {

    private final Logger logger = LoggerFactory.getLogger(MainController.class);

    @Autowired
    private LocalProxyServer localProxyServer;

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private FxApplication fxApplication;

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
    private GridPane gridPane;

    @FXML
    public void initialize() {
        proxyHost.setText(userConfig.getProxyHost());
        proxyHost.textProperty().addListener((obs, oldValue, newValue) -> userConfig.setProxyHost(newValue));

        proxyPort.setTextFormatter(GuiUtils.createDecimalOnlyTextFormatter());
        proxyPort.setText("" + userConfig.getProxyPort());

        proxyPort.textProperty().addListener(
                (obs, oldValue, newValue) -> userConfig.setProxyPort(
                        StringUtils.isNotEmpty(newValue) ? Integer.parseInt(newValue) : null));

        localProxyPort.setTextFormatter(GuiUtils.createDecimalOnlyTextFormatter());
        localProxyPort.setText("" + userConfig.getLocalPort());
        localProxyPort.textProperty().addListener(
                (obs, oldValue, newValue) -> userConfig.setLocalPort(
                        StringUtils.isNotEmpty(newValue) ? Integer.parseInt(newValue) : null));

        testUrl.setText(userConfig.getProxyTestUrl());
        testUrl.textProperty().addListener((obs, oldValue, newValue) -> userConfig.setProxyTestUrl(newValue));

        startedMode(false);
    }

    public void start(ActionEvent actionEvent) {
        disableAll();
        GuiUtils.executeRunnable(() -> {
            if (isValidInput()) {
                try {
                    localProxyServer.start();
                    startedMode(true);
                } catch (Exception e) {
                    logger.error("Error on starting proxy server", e);
                    startedMode(false);
                    GuiUtils.showErrorMessage(
                            "Error on starting proxy server.\nSee the application's log for details.",
                            fxApplication.getPrimaryStage());
                }
            } else {
                startedMode(false);
            }
        }, fxApplication.getPrimaryStage());
    }

    private boolean isValidInput() {
        Message formErrMessage = null;
        if (StringUtils.isBlank(userConfig.getProxyHost())) {
            formErrMessage = Message.error("Fill in the proxy address!");
        } else if (userConfig.getProxyPort() < 1) {
            formErrMessage = Message.error("Fill in a valid proxy port!");
        } else if (userConfig.getLocalPort() < 1024) {
            formErrMessage = Message.error("Fill in a valid local proxy port!");
        } else if (StringUtils.isBlank(userConfig.getProxyTestUrl())) {
            formErrMessage = Message.error("Fill in the test URL!");
        }

        if (formErrMessage != null) {
            GuiUtils.showMessage(formErrMessage, fxApplication.getPrimaryStage());
            return false;
        }

        Message testConnErrMessage = null;
        try {
            // Test the proxy configuration
            HttpUtils.testProxyConfig(userConfig);
        } catch (CredentialException e) {
            testConnErrMessage = Message.error("Wrong user/password!");
        } catch (UnknownHostException e) {
            testConnErrMessage = Message.error("Wrong proxy host!");
        } catch (HttpHostConnectException e) {
            testConnErrMessage = Message.error("Wrong proxy port!");
        } catch (IOException e) {
            testConnErrMessage = Message.error(e.getMessage());
        }

        if (testConnErrMessage != null) {
            GuiUtils.showMessage(testConnErrMessage, fxApplication.getPrimaryStage());
            return false;
        }

        return true;
    }

    public void about(ActionEvent actionEvent) {
        GuiUtils.showInfoMessage("Winfoom - Basic Proxy Facade" +
                "\nVersion: " + systemConfig.getReleaseVersion()
                + "\nProject home page: https://github.com/ecovaci/winfoom"
                + "\nLicense: Apache 2.0", fxApplication.getPrimaryStage());
    }

    public void close(ActionEvent actionEvent) {
        menuBar.fireEvent(
                new WindowEvent(fxApplication.getPrimaryStage().getScene().getWindow(),
                        WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    public void stop(ActionEvent actionEvent) {
        // Get the pressed button
        ButtonType buttonType = GuiUtils.showCloseProxyAlertAndWait(fxApplication.getPrimaryStage());
        if (buttonType == ButtonType.OK) {
            localProxyServer.close();
            startedMode(false);
        }

    }

    public void autoStart() {
        startBtn.fire();
    }

    private void startedMode(boolean started) {
        gridPane.setDisable(started);
        startBtn.setDisable(started);
        stopBtn.setDisable(!started);
    }

    private void disableAll() {
        gridPane.setDisable(true);
        startBtn.setDisable(true);
        stopBtn.setDisable(true);
    }


}
