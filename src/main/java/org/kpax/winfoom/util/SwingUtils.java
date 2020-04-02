/*******************************************************************************
 * Copyright (c) 2018 Eugen Covaci.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 *
 * Contributors:
 *     Eugen Covaci - initial design and implementation
 *******************************************************************************/

package org.kpax.winfoom.util;

import org.apache.commons.lang3.Validate;

import javax.swing.*;
import javax.swing.text.DefaultFormatter;
import java.awt.*;

/**
 * Various Swing related methods.
 *
 * @author Eugen Covaci
 */
public class SwingUtils {

    private static final String DLG_ERR_TITLE = "Error";

    private static final String DLG_INFO_TITLE = "Info";

    private static final String DLG_WARN_TITLE = "Warning";

    public static void setFont(Component component, Font font) {
        component.setFont(font);
        if (component instanceof JMenu) {
            JMenu menu = (JMenu) component;
            for (int i = 0; i < menu.getItemCount(); i++) {
                setFont(menu.getItem(i), font);
            }
        } else if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                setFont(child, font);
            }
        }
    }

    public static void setEnabled(Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                setEnabled(child, enabled);
            }
        }
    }

    public static void commitsOnValidEdit(JSpinner spinner) {
        JComponent comp = spinner.getEditor();
        JFormattedTextField field = (JFormattedTextField) comp.getComponent(0);
        ((DefaultFormatter) field.getFormatter()).setCommitsOnValidEdit(true);
    }

    public static void showMessage(Component parentComponent, String title, String message, int type) {
        JOptionPane.showMessageDialog(parentComponent, message, title, type);
    }

    public static void showErrorMessage(Component parentComponent, String message) {
        showErrorMessage(parentComponent, DLG_ERR_TITLE, message);
    }

    public static void showErrorMessage(Component parentComponent, String title, String message) {
        showMessage(parentComponent, title, message, JOptionPane.ERROR_MESSAGE);
    }

    public static void showInfoMessage(Component parentComponent, String message) {
        showInfoMessage(parentComponent, DLG_INFO_TITLE, message);
    }

    public static void showInfoMessage(Component parentComponent, String title, String message) {
        showMessage(parentComponent, title, message, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showWarningMessage(Component parentComponent, String message) {
        showWarningMessage(parentComponent, DLG_WARN_TITLE, message);
    }

    public static void showWarningMessage(Component parentComponent, String title, String message) {
        showMessage(parentComponent, title, message, JOptionPane.WARNING_MESSAGE);
    }

    public static void executeRunnable(final Runnable runnable,final JFrame frame) {
		Validate.notNull(runnable, "runnable cannot be null");
		Validate.notNull(frame, "frame cannot be null");
        frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        Thread thread = new Thread(() -> {
            try {
                runnable.run();
            } finally {
                EventQueue.invokeLater(() ->
                        frame.setCursor(Cursor.getDefaultCursor()));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

}
