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

package org.kpax.winfoom.util;

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.apache.commons.lang3.Validate;

import javax.swing.*;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 11/21/2019
 */
public class GuiUtils {

    public static TextFormatter<String> createDecimalOnlyTextFormatter() {
        UnaryOperator<TextFormatter.Change> filter = change -> {
            String text = change.getText();
            if (text.matches("[0-9]*")) {
                return change;
            }
            return null;
        };
        return new TextFormatter<>(filter);
    }

    public static void showMessage(Message message, Stage stage) {
        showMessage(message.getType(), message.getText(), stage);
    }

    public static void showErrorMessage(String text, Stage stage) {
        showMessage(Message.MessageType.ERROR, text, stage);
    }
    public static void showWarningMessage(String text, Stage stage) {
        showMessage(Message.MessageType.WARNING, text, stage);
    }
    public static void showInfoMessage(String text, Stage stage) {
        showMessage(Message.MessageType.INFO, text, stage);
    }

    private static void showMessage(Message.MessageType type, String text, Stage stage) {
        Validate.notNull(type, "title cannot be null");
        Validate.notNull(text, "text cannot be null");

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.NONE, text, ButtonType.OK);
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.setTitle(type.getLabel());
            alert.initOwner(stage);

            Label img = new Label();
            img.getStyleClass().addAll("alert", type.getLabel().toLowerCase(), "dialog-pane");
            alert.setGraphic(img);

            if (stage != null && !stage.isShowing()) {
                stage.show();
            }

            alert.show();
        });
    }


    public static ButtonType showCloseAppAlertAndWait(Stage stage) {
        return showOkCancelAlertAndWait(
                "The local proxy facade is started. \nDo you like to stop the proxy facade and leave the application?",
                stage);
    }

    public static ButtonType showCloseProxyAlertAndWait(Stage stage) {
        return showOkCancelAlertAndWait(
                "The local proxy facade is started. \nDo you like to stop the proxy facade?",
                stage);
    }

    public static ButtonType showOkCancelAlertAndWait(String message, Stage stage) {
        Alert alert =
                new Alert(Alert.AlertType.NONE,
                        message,
                        ButtonType.OK,
                        ButtonType.CANCEL);
        alert.initStyle(StageStyle.UTILITY);
        alert.setTitle(Message.MessageType.CONFIRMATION.getLabel());

        Label img = new Label();
        img.getStyleClass().addAll("alert",
                Message.MessageType.CONFIRMATION.getLabel().toLowerCase(), "dialog-pane");
        alert.setGraphic(img);

        alert.initOwner(stage);
        if (stage != null && !stage.isShowing()) {
            stage.show();
        }

        // Get the pressed button
        return alert.showAndWait().orElse(ButtonType.CANCEL);
    }

    public static void closeAllAwtWindows() {
        SwingUtilities.invokeLater(
                () -> Stream.of(java.awt.Window.getWindows())
                        .forEach(java.awt.Window::dispose));
    }

    public static void executeRunnable(Runnable runnable, Stage stage) {
        stage.getScene().setCursor(Cursor.WAIT);
        Thread thread = new Thread(() -> {
            try {
                runnable.run();
            } finally {
                Platform.runLater(() ->
                        stage.getScene().setCursor(Cursor.DEFAULT));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

}
