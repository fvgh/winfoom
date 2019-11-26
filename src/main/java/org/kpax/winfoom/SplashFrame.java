package org.kpax.winfoom;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SplashFrame extends JFrame {

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
        setSize(scale(305, 455));
        setLocationRelativeTo(null);
        contentPane = new JPanel();
        contentPane.setBorder(new EmptyBorder(0, 0, 0, 0));
        contentPane.setLayout(new BorderLayout(0, 0));
        setContentPane(contentPane);
        contentPane.add(getLabel(), BorderLayout.CENTER);
        contentPane.add(getProgressBar(), BorderLayout.SOUTH);
    }

    /**
     * Launch the application.
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

    private JLabel getLabel() {
        if (label == null) {
            Image image = Toolkit.getDefaultToolkit().getImage("config/img/splash.jpg");
            Dimension dimension = scale(305, 455);
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

    private Dimension scale(int width, int height) {
        return new Dimension((int) (width * SCALE), (int) (height * SCALE));
    }
}
