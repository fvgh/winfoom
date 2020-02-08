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

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * This is the frame containing the splash image.
 * Should only be visible while the application is bootstrapped.
 */
public class SplashFrame extends JFrame {

    /**
     * The image scale.
     */
    private static final double SCALE = 0.75;

    private final JPanel contentPane;
    private JLabel label;
    private JProgressBar progressBar;

    /**
     * Create the frame.
     */
    public SplashFrame() {
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(scale());
        setLocationRelativeTo(null);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        contentPane.setLayout(new BorderLayout(0, 0));
        setContentPane(contentPane);
        contentPane.add(getLabel(), BorderLayout.CENTER);
        contentPane.add(getProgressBar(), BorderLayout.SOUTH);
    }

    private JLabel getLabel() {
        if (label == null) {
            Image image = Toolkit.getDefaultToolkit().getImage("config/img/splash.jpg");
            Dimension dimension = scale();
            Image scaledImage = image.getScaledInstance(dimension.width, dimension.height, Image.SCALE_SMOOTH);
            ImageIcon imageIcon = new ImageIcon(scaledImage);
            label = new JLabel(imageIcon);
        }
        return label;
    }

    private JProgressBar getProgressBar() {
        if (progressBar == null) {
            progressBar = new JProgressBar();
            progressBar.setPreferredSize(new Dimension(146, 10));
            progressBar.setIndeterminate(true);
        }
        return progressBar;
    }

    private Dimension scale() {
        return new Dimension((int) (305 * SCALE), (int) (455 * SCALE));
    }

    /**
     * Launch the application (for testing).
     */
    public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                SplashFrame frame = new SplashFrame();
                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

}
