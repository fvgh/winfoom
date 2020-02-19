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

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.Region;
import javafx.stage.StageStyle;

import java.awt.*;
import java.util.Optional;
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

    public static void showMessage(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.NONE, message, ButtonType.OK);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.setTitle(title);
        alert.show();
    }

    public static void showMessage(MessageType messageType, String message) {
        showMessage(messageType.getLabel(), message);
    }

    public static ButtonType showCloseAppAlertAndWait () {
        return showOkCancelAlertAndWait ("The local proxy facade is started. \nDo you like to stop the proxy facade and leave the application?");
    }

    public static ButtonType showCloseProxyAlertAndWait () {
        return showOkCancelAlertAndWait ("The local proxy facade is started. \nDo you like to stop the proxy facade?");
    }

    public static ButtonType showOkCancelAlertAndWait (String message) {
        Alert alert =
                new Alert(Alert.AlertType.NONE,
                        message,
                        ButtonType.OK,
                        ButtonType.CANCEL);
        alert.initStyle(StageStyle.UTILITY);
        alert.setTitle("Warning");

        Optional<ButtonType> result = alert.showAndWait();

        // Get the pressed button
        return result.orElse(ButtonType.CANCEL);
    }

    public static void closeAllAwtWindows () {
        Stream.of(java.awt.Window.getWindows()).forEach(Window::dispose);
    }

    public enum MessageType {
        DLG_ERR_TITLE("Error"), DLG_INFO_TITLE("Info"), DLG_WARN_TITLE("Warning");

        private final String label;

        MessageType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

}
