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

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.DefaultFormatter;
import java.awt.*;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;

/**
 * Various Swing related methods.
 *
 * @author Eugen Covaci
 */
public class SwingUtils {

    private static final String DLG_ERR_TITLE = "Error";

    private static final String DLG_INFO_TITLE = "Info";

    private static final String DLG_WARN_TITLE = "Warning";

    /**
     * Enable/disable a component and all it's sub-components.
     *
     * @param component the {@link Component} to be enabled/disabled.
     * @param enabled   {@code true} or {@code false}.
     */
    public static void setEnabled(final Component component, final boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                setEnabled(child, enabled);
            }
        }
    }

    /**
     * Accept an input value only if it is valid.
     *
     * @param spinner the {@link JSpinner} instance.
     */
    public static void commitsOnValidEdit(final JSpinner spinner) {
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

    /**
     * Execute a {@link Runnable}, showing a waiting cursor until the execution ends.
     *
     * @param runnable the {@link Runnable} instance (not null)
     * @param frame    the current {@link JFrame}
     */
    public static void executeRunnable(final Runnable runnable, final JFrame frame) {
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

    /**
     * Load an image from a specified classpath location.
     *
     * @param cls      used to localize the image URL.
     * @param filename the image's filename.
     * @return the loaded {@link Image}.
     */
    public static Image loadImage(Class<?> cls, String filename) {
        try {
            URL resource = cls.getResource("/img/" + filename);
            if (resource != null) {
                return ImageIO.read(resource);
            } else {
                throw new FileNotFoundException("Resource not found");
            }

        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load the image named: " + filename, e);
        }
    }

}
