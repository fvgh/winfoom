package org.kpax.winfoom.util;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.Region;

import java.util.function.UnaryOperator;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 11/21/2019
 */
public class FxUtils {

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

    public static enum MessageType {
        DLG_ERR_TITLE("Error"), DLG_INFO_TITLE("Info"), DLG_WARN_TITLE("Warning");

        private String label;

        MessageType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

}
