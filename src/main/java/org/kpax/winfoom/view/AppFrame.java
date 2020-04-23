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

import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.HttpHostConnectException;
import org.jdesktop.beansbinding.AutoBinding;
import org.jdesktop.beansbinding.BeanProperty;
import org.jdesktop.beansbinding.BindingGroup;
import org.jdesktop.beansbinding.Bindings;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.config.UserConfig;
import org.kpax.winfoom.proxy.ProxyContext;
import org.kpax.winfoom.proxy.ProxyType;
import org.kpax.winfoom.util.HttpUtils;
import org.kpax.winfoom.util.SwingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.security.auth.login.CredentialException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.UnknownHostException;

@Profile("!test")
@Component
public class AppFrame extends JFrame {
    private static final long serialVersionUID = 4009799697210970761L;

    private static final Logger logger = LoggerFactory.getLogger(AppFrame.class);

    private static final int ICON_SIZE = 16;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    private UserConfig userConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ProxyContext proxyContext;

    private JLabel proxyTypeLabel;
    private JComboBox<ProxyType> proxyTypeCombo;

    private JLabel proxyHostLabel;
    private JTextField proxyHostJTextField;
    private JLabel proxyPortLabel;
    private JSpinner proxyPortJSpinner;
    private JLabel localPortLabel;
    private JSpinner localPortJSpinner;

    private JLabel testUrlLabel;
    private JTextField testUrlJTextField;

    private JButton btnConfig;
    private JButton btnStart;
    private JButton btnStop;

    private JPanel btnPanel;

    private JMenuBar menuBar;
    private JMenu mnFile;
    private JMenuItem mntmExit;
    private JMenu mnHelp;
    private JMenuItem mntmAbout;

    /**
     * Create the frame.
     */
    @PostConstruct
    public void init() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setResizable(false);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownApp();
            }
        });

        Image iconImage = Toolkit.getDefaultToolkit().getImage("config/img/icon.png");
        setIconImage(iconImage);
        //
        if (SystemTray.isSupported()) {
            final SystemTray tray = SystemTray.getSystemTray();
            final TrayIcon trayIcon = new TrayIcon(iconImage, "WinFoom");
            trayIcon.setImageAutoSize(true);
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    setVisible(true);
                    setState(Frame.NORMAL);
                }
            });
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowIconified(WindowEvent e) {
                    try {
                        tray.add(trayIcon);
                    } catch (AWTException ex) {
                        logger.error("Cannot add icon to tray", ex);
                    }
                    setVisible(false);
                }

                @Override
                public void windowDeiconified(WindowEvent e) {
                    tray.remove(trayIcon);
                    setExtendedState(getExtendedState() & ~Frame.ICONIFIED);
                    setVisible(true);
                }
            });
        }
        //
        setTitle("WinFoom");
        setJMenuBar(getMainMenuBar());
        JPanel mainContentPane = new JPanel();
        setContentPane(mainContentPane);
        //
        GridBagLayout gridBagLayout = new GridBagLayout();
        gridBagLayout.columnWidths = new int[]{0, 0, 0};
        gridBagLayout.rowHeights = new int[]{35, 35, 35, 35, 35, 35, 10};
        gridBagLayout.columnWeights = new double[]{0.0, 0.0, 0.0};
        gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0E-4};
        mainContentPane.setLayout(gridBagLayout);

        GridBagConstraints labelGbc1 = new GridBagConstraints();
        labelGbc1.fill = GridBagConstraints.BOTH;
        labelGbc1.insets = new Insets(5, 5, 5, 5);
        labelGbc1.gridx = 0;
        labelGbc1.gridy = 0;
        mainContentPane.add(getProxyTypeLabel(), labelGbc1);

        GridBagConstraints componentGbc1 = new GridBagConstraints();
        componentGbc1.insets = new Insets(5, 5, 5, 5);
        componentGbc1.anchor = GridBagConstraints.WEST;
        componentGbc1.gridx = 1;
        componentGbc1.gridy = 0;
        mainContentPane.add(getProxyTypeCombo(), componentGbc1);

        GridBagConstraints componentGbc2 = new GridBagConstraints();
        componentGbc2.insets = new Insets(5, 5, 5, 5);
        componentGbc2.anchor = GridBagConstraints.WEST;
        componentGbc2.gridx = 2;
        componentGbc2.gridy = 0;
        mainContentPane.add(getBtnConfig(), componentGbc2);

        GridBagConstraints labelGbc3 = new GridBagConstraints();
        labelGbc3.fill = GridBagConstraints.BOTH;
        labelGbc3.insets = new Insets(5, 5, 5, 5);
        labelGbc3.gridx = 0;
        labelGbc3.gridy = 1;
        mainContentPane.add(getProxyHostLabel(), labelGbc3);

        GridBagConstraints componentGbc3 = new GridBagConstraints();
        componentGbc3.insets = new Insets(5, 5, 5, 5);
        componentGbc3.fill = GridBagConstraints.HORIZONTAL;
        componentGbc3.gridx = 1;
        componentGbc3.gridy = 1;
        componentGbc3.gridwidth = 2;
        mainContentPane.add(getProxyHostJTextField(), componentGbc3);

        GridBagConstraints labelGbc4 = new GridBagConstraints();
        labelGbc4.fill = GridBagConstraints.BOTH;
        labelGbc4.insets = new Insets(5, 5, 5, 5);
        labelGbc4.gridx = 0;
        labelGbc4.gridy = 2;
        mainContentPane.add(getProxyPortLabel(), labelGbc4);

        GridBagConstraints componentGbc4 = new GridBagConstraints();
        componentGbc4.anchor = GridBagConstraints.WEST;
        componentGbc4.insets = new Insets(5, 5, 5, 5);
        componentGbc4.gridx = 1;
        componentGbc4.gridy = 2;
        componentGbc4.gridwidth = 2;
        mainContentPane.add(getProxyPortJSpinner(), componentGbc4);

        GridBagConstraints labelGbc5 = new GridBagConstraints();
        labelGbc5.fill = GridBagConstraints.BOTH;
        labelGbc5.insets = new Insets(5, 5, 5, 5);
        labelGbc5.gridx = 0;
        labelGbc5.gridy = 3;
        mainContentPane.add(getLocalPortLabel(), labelGbc5);

        GridBagConstraints componentGbc5 = new GridBagConstraints();
        componentGbc5.anchor = GridBagConstraints.WEST;
        componentGbc5.insets = new Insets(5, 5, 5, 5);
        componentGbc5.gridx = 1;
        componentGbc5.gridy = 3;
        componentGbc5.gridwidth = 2;
        mainContentPane.add(getLocalPortJSpinner(), componentGbc5);

        GridBagConstraints labelGbcTestUrl = new GridBagConstraints();
        labelGbcTestUrl.fill = GridBagConstraints.BOTH;
        labelGbcTestUrl.insets = new Insets(5, 5, 5, 5);
        labelGbcTestUrl.gridx = 0;
        labelGbcTestUrl.gridy = 4;
        mainContentPane.add(getTestUrlLabel(), labelGbcTestUrl);

        GridBagConstraints componentGbcTestUrl = new GridBagConstraints();
        componentGbcTestUrl.insets = new Insets(5, 5, 5, 5);
        componentGbcTestUrl.gridx = 1;
        componentGbcTestUrl.gridy = 4;
        componentGbcTestUrl.gridwidth = 2;
        mainContentPane.add(getTestUrlJTextField(), componentGbcTestUrl);

        GridBagConstraints gbcBtnPanel = new GridBagConstraints();
        gbcBtnPanel.gridx = 0;
        gbcBtnPanel.gridy = 5;
        gbcBtnPanel.gridwidth = 3;
        gbcBtnPanel.gridheight = 2;
        mainContentPane.add(getBtnPanel(), gbcBtnPanel);

        initDataBindings();
    }

    public void focusOnStartButton() {
        getBtnStart().requestFocus();
    }

    // ---------- Labels

    private JLabel getProxyTypeLabel() {
        if (proxyTypeLabel == null) {
            proxyTypeLabel = new JLabel("Proxy type:");
        }
        return proxyTypeLabel;
    }

    private JLabel getProxyHostLabel() {
        if (proxyHostLabel == null) {
            proxyHostLabel = new JLabel("Proxy host:");
        }
        return proxyHostLabel;
    }

    private JLabel getProxyPortLabel() {
        if (proxyPortLabel == null) {
            proxyPortLabel = new JLabel("Proxy port:");
        }
        return proxyPortLabel;
    }

    private JLabel getLocalPortLabel() {
        if (localPortLabel == null) {
            localPortLabel = new JLabel("Local proxy port:");
        }
        return localPortLabel;
    }

    private JLabel getTestUrlLabel() {
        if (testUrlLabel == null) {
            testUrlLabel = new JLabel("Test URL:");
        }
        return testUrlLabel;
    }

    // ------- End Labels

    // -------- Fields

    private JComboBox<ProxyType> getProxyTypeCombo() {
        if (proxyTypeCombo == null) {
            proxyTypeCombo = new JComboBox<>(new ProxyType[]{ProxyType.HTTP, ProxyType.SOCKS5, ProxyType.PAC});
            proxyTypeCombo.setMinimumSize(new Dimension(80, 35));
            proxyTypeCombo.addActionListener((e) -> {
                getBtnConfig().setVisible(userConfig.isSocks5() || userConfig.isPac());
            });
        }
        return proxyTypeCombo;
    }

    private JTextField getProxyHostJTextField() {
        if (proxyHostJTextField == null) {
            proxyHostJTextField = createTextField();
        }
        return proxyHostJTextField;
    }

    private JSpinner getProxyPortJSpinner() {
        if (proxyPortJSpinner == null) {
            proxyPortJSpinner = createJSpinner();
        }
        return proxyPortJSpinner;
    }

    private JSpinner getLocalPortJSpinner() {
        if (localPortJSpinner == null) {
            localPortJSpinner = createJSpinner();
        }
        return localPortJSpinner;
    }

    private JTextField getTestUrlJTextField() {
        if (testUrlJTextField == null) {
            testUrlJTextField = createTextField();
        }
        return testUrlJTextField;
    }

    // ---------- End Fields

    // ------- Buttons

    private JButton getBtnStart() {
        if (btnStart == null) {
            btnStart = new JButton("Start");
            btnStart.setMargin(new Insets(2, 6, 2, 6));
            btnStart.setIcon(new TunedImageIcon("config/img/arrow-right.png"));
            btnStart.addActionListener(e -> {
                startServer();
                getBtnStop().requestFocus();
            });
            btnStart.setToolTipText("Start the proxy facade");
        }
        return btnStart;
    }

    private JButton getBtnStop() {
        if (btnStop == null) {
            btnStop = new JButton("Stop");
            btnStop.setMargin(new Insets(2, 6, 2, 6));
            btnStop.addActionListener(e -> {
                stopServer();
                focusOnStartButton();
            });
            btnStop.setIcon(new TunedImageIcon("config/img/process-stop.png"));
            btnStop.setEnabled(false);
            btnStop.setToolTipText("Stop the proxy facade");
        }
        return btnStop;
    }

    private JButton getBtnConfig() {
        if (btnConfig == null) {
            btnConfig = new JButton("Config");
            btnConfig.setIcon(new TunedImageIcon("config/img/preferences-system.png"));
            btnConfig.setPreferredSize(new Dimension(90, 25));
            btnConfig.setMargin(new Insets(1, 1, 1, 1));
            btnConfig.addActionListener(e -> showConfigInputDialog());
            btnConfig.setToolTipText(HttpUtils.toHtml("Set the user/password required for this proxy." +
                    "<br>Leave it empty if the proxy does not require authentication."));
        }
        return btnConfig;
    }

    // ------- End Buttons

    // --------- Panels

    private JPanel getBtnPanel() {
        if (btnPanel == null) {
            btnPanel = new JPanel();
            btnPanel.add(getBtnStart());
            btnPanel.add(getBtnStop());
        }
        return btnPanel;
    }

    // -------- Menu

    private JMenuBar getMainMenuBar() {
        if (menuBar == null) {
            menuBar = new JMenuBar();
            menuBar.add(getMnFile());
            menuBar.add(getMnHelp());
        }
        return menuBar;
    }

    private JMenu getMnFile() {
        if (mnFile == null) {
            mnFile = new JMenu("File");
            mnFile.add(getMntmExit());
        }
        return mnFile;
    }

    private JMenuItem getMntmExit() {
        if (mntmExit == null) {
            mntmExit = new JMenuItem("Exit");
            mntmExit.setIcon(new TunedImageIcon("config/img/application-exit.png"));
            mntmExit.addActionListener(e -> shutdownApp());
        }
        return mntmExit;
    }

    private JMenu getMnHelp() {
        if (mnHelp == null) {
            mnHelp = new JMenu("Help");
            mnHelp.add(getMntmAbout());
        }
        return mnHelp;
    }

    private JMenuItem getMntmAbout() {
        if (mntmAbout == null) {
            mntmAbout = new JMenuItem("About");
            mntmAbout.setIcon(new TunedImageIcon("config/img/dialog-information.png"));
            mntmAbout.addActionListener(e -> SwingUtils.showInfoMessage(this, "About", "Winfoom - Basic Proxy Facade" +
                    "\nVersion: " + systemConfig.getReleaseVersion()
                    + "\nProject home page: https://github.com/ecovaci/winfoom"
                    + "\nLicense: Apache 2.0"));
        }
        return mntmAbout;
    }

    // ------- End Menu

    private JTextField createTextField() {
        JTextField textField = new JTextField();
        textField.setPreferredSize(new Dimension(220, 25));
        textField.setMinimumSize(new Dimension(6, 25));
        return textField;
    }

    private JSpinner createJSpinner() {
        JSpinner jSpinner = new JSpinner();
        jSpinner.setPreferredSize(new Dimension(60, 25));
        jSpinner.setMinimumSize(new Dimension(32, 25));
        jSpinner.setEditor(new JSpinner.NumberEditor(jSpinner, "#"));
        SwingUtils.commitsOnValidEdit(jSpinner);
        return jSpinner;
    }

    private void initDataBindings() {
        //
        BeanProperty<UserConfig, ProxyType> proxyTypeProperty = BeanProperty.create("proxyType");
        AutoBinding<UserConfig, ProxyType, JComboBox, Object> autoBindingProxyType = Bindings
                .createAutoBinding(AutoBinding.UpdateStrategy.READ_WRITE, userConfig, proxyTypeProperty,
                        getProxyTypeCombo(), BeanProperty.create("selectedItem"));
        autoBindingProxyType.bind();
        //
        BeanProperty<UserConfig, String> proxyHostProperty = BeanProperty.create("proxyHost");
        AutoBinding<UserConfig, String, JTextField, String> autoBindingProxyHost = Bindings
                .createAutoBinding(AutoBinding.UpdateStrategy.READ_WRITE, userConfig, proxyHostProperty,
                        getProxyHostJTextField(), BeanProperty.create("text"));
        autoBindingProxyHost.bind();
        //
        BeanProperty<UserConfig, Integer> proxyPortProperty = BeanProperty.create("proxyPort");
        AutoBinding<UserConfig, Integer, JSpinner, Object> autoBindingProxyPort = Bindings
                .createAutoBinding(AutoBinding.UpdateStrategy.READ_WRITE, userConfig, proxyPortProperty,
                        getProxyPortJSpinner(), BeanProperty.create("value"));
        autoBindingProxyPort.bind();
        //
        BeanProperty<UserConfig, Integer> localPortProperty = BeanProperty.create("localPort");
        AutoBinding<UserConfig, Integer, JSpinner, Object> autoBindingLocalPort = Bindings
                .createAutoBinding(AutoBinding.UpdateStrategy.READ_WRITE, userConfig, localPortProperty,
                        getLocalPortJSpinner(), BeanProperty.create("value"));
        autoBindingLocalPort.bind();
        //
        BeanProperty<UserConfig, String> testUrlProperty = BeanProperty.create("proxyTestUrl");
        AutoBinding<UserConfig, String, JTextField, String> autoBindingProxyTestUrl = Bindings
                .createAutoBinding(AutoBinding.UpdateStrategy.READ_WRITE, userConfig, testUrlProperty,
                        getTestUrlJTextField(), BeanProperty.create("text"));
        autoBindingProxyTestUrl.bind();
        //
        BindingGroup bindingGroup = new BindingGroup();
        bindingGroup.addBinding(autoBindingProxyType);
        bindingGroup.addBinding(autoBindingProxyHost);
        bindingGroup.addBinding(autoBindingProxyPort);
        bindingGroup.addBinding(autoBindingLocalPort);
        bindingGroup.addBinding(autoBindingProxyTestUrl);
        //
    }

    private void disableAll() {
        SwingUtils.setEnabled(getContentPane(), false);
    }

    private boolean isValidInput() {
        if (StringUtils.isBlank(proxyHostJTextField.getText())) {
            SwingUtils.showErrorMessage(this, "Validation Error", "Fill in the proxy address");
            return false;
        }
        Integer proxyPort = (Integer) proxyPortJSpinner.getValue();
        if (proxyPort == null || !HttpUtils.isValidPort(proxyPort)) {
            SwingUtils.showErrorMessage(this, "Fill in a valid proxy port, between 1 and 65535");
            return false;
        }
        Integer localPort = (Integer) localPortJSpinner.getValue();
        if (localPort == null || !HttpUtils.isValidPort(localPort)) {
            SwingUtils.showErrorMessage(this, "Fill in a valid local proxy port, between 1 and 65535");
            return false;
        }

        if (StringUtils.isBlank(testUrlJTextField.getText())) {
            SwingUtils.showErrorMessage(this, "Fill in the proxy test URL");
            return false;
        }

        // Test the proxy configuration
        try {
            HttpUtils.testProxyConfig(userConfig);
        } catch (CredentialException e) {
            SwingUtils.showErrorMessage(this, "Wrong user/password!");
            return false;
        } catch (UnknownHostException e) {
            SwingUtils.showErrorMessage(this, "Wrong proxy host!");
            return false;
        } catch (HttpHostConnectException e) {
            SwingUtils.showErrorMessage(this, "Wrong proxy port!");
            return false;
        } catch (Exception e) {
            SwingUtils.showErrorMessage(this, e.getMessage());
            return false;
        }

        return true;
    }

    private void startServer() {
        if (userConfig.isSocks5()) {
            if (StringUtils.isNotEmpty(userConfig.getProxySocksUsername())
                    && StringUtils.isEmpty(userConfig.getProxyPassword())) {
                int option = JOptionPane.showConfirmDialog(this, "The username is not empty, but you did not provide any password." +
                        "\nDo you still want to proceed?", "Warning", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (option != JOptionPane.OK_OPTION) {
                    return;
                }
            }
        }
        disableAll();
        SwingUtils.executeRunnable(() -> {
            if (isValidInput()) {
                try {
                    proxyContext.start();
                    getBtnStop().setEnabled(true);
                } catch (Exception e) {
                    logger.error("Error on starting proxy server", e);
                    enableInput();
                    SwingUtils.showErrorMessage(AppFrame.this,
                            "Error on starting proxy server.\nSee the application's log for details.");
                }
            } else {
                enableInput();
            }
        }, this);

    }

    private void stopServer() {
        if (proxyContext.isStarted() && JOptionPane.showConfirmDialog(this,
                "The local proxy facade is started. \nDo you like to stop the proxy facade?",
                "Warning", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.YES_OPTION) {
            proxyContext.stop();
            enableInput();
        }
    }

    private void enableInput() {
        SwingUtils.setEnabled(getContentPane(), true);
        getBtnStop().setEnabled(false);
    }


    private void shutdownApp() {
        if (!proxyContext.isStarted() || JOptionPane.showConfirmDialog(this,
                "The local proxy facade is started. \nDo you like to stop the proxy facade and leave the application?",
                "Warning", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.YES_OPTION) {
            logger.info("Now shutdown application");
            applicationContext.close();
            dispose();
        }
    }


    private void showConfigInputDialog() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));

        JPanel labelPanel = new JPanel(new GridLayout(0, 1, 2, 2));
        labelPanel.add(new JLabel("Username: ", SwingConstants.RIGHT));
        labelPanel.add(new JLabel("Password: ", SwingConstants.RIGHT));
        labelPanel.add(new JLabel("Store password: ", SwingConstants.RIGHT));
        panel.add(labelPanel, BorderLayout.WEST);

        JPanel controlsPanel = new JPanel(new GridLayout(0, 1, 2, 2));
        JTextField username = new JTextField(userConfig.getProxySocksUsername());
        controlsPanel.add(username);
        JPasswordField password = new JPasswordField(userConfig.getProxyPassword());
        controlsPanel.add(password);
        JCheckBox storePassword = new JCheckBox();
        storePassword.setSelected(userConfig.isProxySocksStorePassword());
        storePassword.addActionListener((e -> {
            if (storePassword.isSelected()) {
                int option = JOptionPane.showConfirmDialog(AppFrame.this,
                        "This is not recomanded!" +
                                "\nThe password is stored in a text file, encoded but not encrypted.",
                        "Warning",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (option != JOptionPane.OK_OPTION) {
                    storePassword.setSelected(false);
                }
            }
        }));

        controlsPanel.add(storePassword);
        panel.add(controlsPanel, BorderLayout.CENTER);

        int option = JOptionPane.showConfirmDialog(
                this,
                panel,
                "Config",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (option == JOptionPane.OK_OPTION) {
            userConfig.setProxySocksUsername(username.getText());
            userConfig.setProxyPassword(password.getPassword());
            userConfig.setProxySocksStorePassword(storePassword.isSelected());
        }

    }

    private static class TunedImageIcon extends ImageIcon {

        TunedImageIcon(String filename) {
            super(filename);
        }

        @Override
        public int getIconHeight() {
            return ICON_SIZE;
        }

        @Override
        public int getIconWidth() {
            return ICON_SIZE;
        }

        @Override
        public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2d.drawImage(getImage(), x, y, c);
            g2d.dispose();
        }
    }

}