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

    private final int frameWidth = 305;
    private final int frameHeight = 465;

    private final JPanel contentPane;
    private Canvas canvas;
    private JProgressBar progressBar;

    /**
     * Create the frame.
     */
    public SplashFrame() {
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(frameWidth, frameHeight);
        setLocationRelativeTo(null);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        contentPane.setLayout(new BorderLayout(0, 0));
        setContentPane(contentPane);
        contentPane.add(getCanvas(), BorderLayout.CENTER);
        contentPane.add(getProgressBar(), BorderLayout.SOUTH);
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

    private Canvas getCanvas() {
        if (canvas == null) {
            canvas = new Canvas() {
                @Override
                public void paint(Graphics g) {
                    super.paint(g);
                    Image image = Toolkit.getDefaultToolkit().getImage("config/img/splash.jpg");
                    Graphics2D g2d = (Graphics2D) g.create();
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    g2d.drawImage(image, 0, 0, this);
                    g2d.dispose();
                }
            };
        }
        return canvas;
    }

    private JProgressBar getProgressBar() {
        if (progressBar == null) {
            progressBar = new JProgressBar();
            progressBar.setPreferredSize(new Dimension(146, 10));
            progressBar.setIndeterminate(true);
        }
        return progressBar;
    }

}
