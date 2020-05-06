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
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;
import org.kpax.winfoom.config.ProxyConfig;
import org.kpax.winfoom.config.SystemConfig;
import org.kpax.winfoom.proxy.ProxyBlacklist;
import org.kpax.winfoom.proxy.ProxyContext;
import org.kpax.winfoom.proxy.ProxyValidator;
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
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.UnknownHostException;
import java.util.Objects;

@Profile("!test")
@Component
public class AppFrame extends JFrame {
    private static final long serialVersionUID = 4009799697210970761L;

    private static final Logger logger = LoggerFactory.getLogger(AppFrame.class);

    private static final int ICON_SIZE = 16;

    private static final int TOOLTIP_TIMEOUT = 10;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    private ProxyConfig proxyConfig;

    @Autowired
    private SystemConfig systemConfig;

    @Autowired
    private ProxyContext proxyContext;

    @Autowired
    private ProxyValidator proxyValidator;

    @Autowired
    private ProxyBlacklist proxyBlacklist;

    private JLabel proxyTypeLabel;
    private JComboBox<ProxyConfig.Type> proxyTypeCombo;

    private JLabel localPortLabel;
    private JSpinner localPortJSpinner;

    private JLabel testUrlLabel;
    private JTextField testUrlJTextField;

    private JButton btnStart;
    private JButton btnStop;
    private JButton btnCancelBlacklist;

    private JPanel mainContentPanel;
    private JPanel labelsFieldsPanel;
    private JPanel labelPanel;
    private JPanel fieldPanel;
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

        Image iconImage = SwingUtils.loadImage(AppFrame.class, "icon.png");
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
        setContentPane(getMainContentPanel());

        ToolTipManager.sharedInstance().setDismissDelay(TOOLTIP_TIMEOUT * 1000);

        getProxyTypeCombo().setSelectedItem(proxyConfig.getProxyType());

    }

    public void focusOnStartButton() {
        getBtnStart().requestFocus();
    }

    // ---------- Labels

    private JLabel getProxyTypeLabel() {
        if (proxyTypeLabel == null) {
            proxyTypeLabel = new JLabel("Proxy type* ");
        }
        return proxyTypeLabel;
    }

    private JLabel getProxyHostLabel() {
        return new JLabel("Proxy host* ");
    }

    private JLabel getProxyPortLabel() {
        return new JLabel("Proxy port* ");
    }

    private JLabel getLocalPortLabel() {
        return new JLabel("Local proxy port* ");
    }

    private JLabel getTestUrlLabel() {
        if (testUrlLabel == null) {
            testUrlLabel = new JLabel("Test URL* ");
        }
        return testUrlLabel;
    }

    private JLabel getPacFileLabel() {
        return new JLabel("PAC file location* ");
    }

    private JLabel getUsernameLabel() {
        return new JLabel("Username ");
    }

    private JLabel getPasswordLabel() {
        return new JLabel("Password ");
    }

    private JLabel getStorePasswordLabel() {
        return new JLabel("Store password ");
    }

    private JLabel getBlacklistTimeoutLabel() {
        return new JLabel("Blacklist timeout* ");
    }

    // ------- End Labels

    // -------- Fields

    private JComboBox<ProxyConfig.Type> getProxyTypeCombo() {
        if (proxyTypeCombo == null) {
            proxyTypeCombo = new JComboBox<>(ProxyConfig.Type.values());
            proxyTypeCombo.setMinimumSize(new Dimension(80, 35));
            proxyTypeCombo.addActionListener((e) -> {
                clearLabelsAndFields();
                getBtnCancelBlacklist().setVisible(false);
                addProxyType();
                ProxyConfig.Type proxyType = (ProxyConfig.Type) proxyTypeCombo.getSelectedItem();
                proxyConfig.setProxyType(proxyType);
                switch (Objects.requireNonNull(proxyType)) {
                    case HTTP:
                    case SOCKS4:
                        configureForHttp();
                        break;
                    case SOCKS5:
                        configureForSocks5();
                        break;
                    case PAC:
                        configureForPac();
                        break;
                    case DIRECT:
                        configureForDirect();
                        break;
                }
                this.pack();
            });
        }
        return proxyTypeCombo;
    }

    private JTextField getProxyHostJTextField() {
        JTextField proxyHostJTextField = createTextField(proxyConfig.getProxyHost());
        proxyHostJTextField.setToolTipText("The ip or domain name of the remote proxy");
        proxyHostJTextField.getDocument().addDocumentListener((TextChangeListener) (e) -> {
            proxyConfig.setProxyHost(proxyHostJTextField.getText());
        });
        return proxyHostJTextField;
    }

    private JTextField getPacFileJTextField() {
        JTextField pacFileJTextField = createTextField(proxyConfig.getProxyPacFileLocation());
        pacFileJTextField.getDocument().addDocumentListener((TextChangeListener) (e) -> {
            proxyConfig.setProxyPacFileLocation(pacFileJTextField.getText());
        });
        pacFileJTextField.setToolTipText(HttpUtils.toHtml("The location of the Proxy Auto-Config file." +
                "<br>It can be a local location (like <i>C:/pac/proxy.pac</i>) or a HTTP(s) address (like <i>http://pacserver:80/proxy.pac</i>)"));
        return pacFileJTextField;
    }

    private JSpinner getProxyPortJSpinner() {
        JSpinner proxyPortJSpinner = createJSpinner(proxyConfig.getProxyPort());
        proxyPortJSpinner.setToolTipText("The remote proxy port, between 1 and 65535");
        proxyPortJSpinner.addChangeListener(e -> {
            proxyConfig.setProxyPort((Integer) proxyPortJSpinner.getValue());
        });
        return proxyPortJSpinner;
    }

    private JSpinner getLocalPortJSpinner() {
        if (localPortJSpinner == null) {
            localPortJSpinner = createJSpinner(proxyConfig.getLocalPort());
            localPortJSpinner.setToolTipText("The port Winfoom will listen on, between 1 and 65535");
            localPortJSpinner.addChangeListener(e -> {
                proxyConfig.setLocalPort((Integer) localPortJSpinner.getValue());
            });
        }
        return localPortJSpinner;
    }

    private JTextField getTestUrlJTextField() {
        if (testUrlJTextField == null) {
            testUrlJTextField = createTextField(proxyConfig.getProxyTestUrl());
            testUrlJTextField.setToolTipText("The URL used to test the current settings");
            testUrlJTextField.getDocument().addDocumentListener((TextChangeListener) (e) -> {
                proxyConfig.setProxyTestUrl(testUrlJTextField.getText());
            });
        }
        return testUrlJTextField;
    }

    private JTextField getUsernameJTextField() {
        JTextField usernameJTextField = createTextField(proxyConfig.getProxyUsername());
        usernameJTextField.setToolTipText("The optional username if the SOCKS5 proxy requires authentication.");
        usernameJTextField.getDocument().addDocumentListener((TextChangeListener) (e) -> {
            proxyConfig.setProxyUsername(usernameJTextField.getText());
        });
        return usernameJTextField;
    }


    private JPasswordField getPasswordField() {
        JPasswordField passwordField = new JPasswordField(proxyConfig.getProxyPassword());
        passwordField.setToolTipText("The optional password if the SOCKS5 proxy requires authentication.");
        passwordField.getDocument().addDocumentListener((TextChangeListener) (e) -> {
            proxyConfig.setProxyPassword(new String(passwordField.getPassword()));
        });
        return passwordField;
    }

    private JCheckBox getStorePasswordJCheckBox() {
        JCheckBox storePasswordJCheckBox = new JCheckBox();
        storePasswordJCheckBox.setSelected(proxyConfig.isProxyStorePassword());
        storePasswordJCheckBox.setToolTipText("Whether to store the password or not." +
                "\nThe password is stored in a text file, encoded but not encrypted.");
        storePasswordJCheckBox.addActionListener((e -> {
            if (storePasswordJCheckBox.isSelected()) {
                int option = JOptionPane.showConfirmDialog(AppFrame.this,
                        "This is not recomanded!" +
                                "\nThe password is stored in a text file, encoded but not encrypted.",
                        "Warning",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (option != JOptionPane.OK_OPTION) {
                    storePasswordJCheckBox.setSelected(false);
                }
            }
            proxyConfig.setProxyStorePassword(storePasswordJCheckBox.isSelected());
        }));

        return storePasswordJCheckBox;
    }


    private JSpinner getBlacklistTimeoutJSpinner() {
        JSpinner proxyPortJSpinner = createJSpinner(proxyConfig.getBlacklistTimeout());
        proxyPortJSpinner.addChangeListener(e -> {
            proxyConfig.setBlacklistTimeout((Integer) proxyPortJSpinner.getValue());
        });
        proxyPortJSpinner.setToolTipText(HttpUtils.toHtml("If a proxy doesn't responds it is blacklisted"
                + "<br> which means it will not be used again until the blacklist timeout (in minutes) happens."
                + "<br>A value of zero or negative would disable the blacklisting mechanism."));
        return proxyPortJSpinner;
    }

    // ---------- End Fields

    // ------- Buttons

    private JButton getBtnStart() {
        if (btnStart == null) {
            btnStart = new JButton("Start");
            btnStart.setMargin(new Insets(2, 6, 2, 6));
            btnStart.setIcon(new TunedImageIcon("arrow-right.png"));
            btnStart.addActionListener(e -> {
                startServer();
                getBtnStop().requestFocus();
                if (proxyConfig.isAutoConfig()) {
                    getBtnCancelBlacklist().setEnabled(true);
                }
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
                if (proxyConfig.isAutoConfig()) {
                    getBtnCancelBlacklist().setEnabled(false);
                }
            });
            btnStop.setIcon(new TunedImageIcon("process-stop.png"));
            btnStop.setEnabled(false);
            btnStop.setToolTipText("Stop the proxy facade");
        }
        return btnStop;
    }

    private JButton getBtnCancelBlacklist() {
        if (btnCancelBlacklist == null) {
            btnCancelBlacklist = new JButton("Cancel blacklist");
            btnCancelBlacklist.setMargin(new Insets(2, 6, 2, 6));
            btnCancelBlacklist.addActionListener(e -> {
                int clearCount = proxyBlacklist.clear();
                SwingUtils.showInfoMessage(this, String.format("Found: %d blacklisted proxies!", clearCount));
            });
            btnCancelBlacklist.setIcon(new TunedImageIcon("clear-blacklist.png"));
            btnCancelBlacklist.setVisible(false);
            btnCancelBlacklist.setEnabled(false);
            btnCancelBlacklist.setToolTipText(HttpUtils.toHtml("Remove all proxies from blacklist log." +
                    "<br>Use this when you know that some blacklisted proxies are up" +
                    "<br>and it doesn't make sense to wait for timeout."));
        }
        return btnCancelBlacklist;
    }

    // ------- End Buttons

    // --------- Panels

    private JPanel getMainContentPanel() {
        if (mainContentPanel == null) {
            mainContentPanel = new JPanel(new BorderLayout());
            mainContentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

            mainContentPanel.add(getLabelsFieldsPanel(), BorderLayout.CENTER);
            mainContentPanel.add(getBtnPanel(), BorderLayout.SOUTH);
        }
        return mainContentPanel;
    }

    private JPanel getLabelPanel() {
        if (labelPanel == null) {
            labelPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        }
        return labelPanel;
    }

    private JPanel getFieldPanel() {
        if (fieldPanel == null) {
            fieldPanel = new JPanel(new GridLayout(0, 1, 5, 5));
        }
        return fieldPanel;
    }

    private JPanel getLabelsFieldsPanel() {
        if (labelsFieldsPanel == null) {
            labelsFieldsPanel = new JPanel(new BorderLayout());
            labelsFieldsPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
            labelsFieldsPanel.add(getLabelPanel(), BorderLayout.WEST);
            labelsFieldsPanel.add(getFieldPanel(), BorderLayout.CENTER);
            addProxyType();
        }
        return labelsFieldsPanel;
    }

    private void addProxyType() {
        getLabelPanel().add(getProxyTypeLabel());
        getFieldPanel().add(wrapToPanel(getProxyTypeCombo()));
    }

    private void clearLabelsAndFields() {
        getLabelPanel().removeAll();
        getFieldPanel().removeAll();
    }

    private void configureForHttp() {
        labelPanel.add(getProxyHostLabel());
        labelPanel.add(getProxyPortLabel());
        labelPanel.add(getLocalPortLabel());
        labelPanel.add(getTestUrlLabel());

        fieldPanel.add(getProxyHostJTextField());
        fieldPanel.add(wrapToPanel(getProxyPortJSpinner()));
        fieldPanel.add(wrapToPanel(getLocalPortJSpinner()));
        fieldPanel.add(getTestUrlJTextField());
    }

    private void configureForPac() {
        labelPanel.add(getPacFileLabel());
        labelPanel.add(getBlacklistTimeoutLabel());
        labelPanel.add(getLocalPortLabel());
        labelPanel.add(getTestUrlLabel());

        fieldPanel.add(getPacFileJTextField());
        fieldPanel.add(wrapToPanel(getBlacklistTimeoutJSpinner(),
                new JLabel(" (" + proxyBlacklist.getTemporalUnit().toString().toLowerCase() + ")")));
        fieldPanel.add(wrapToPanel(getLocalPortJSpinner()));
        fieldPanel.add(getTestUrlJTextField());

        getBtnCancelBlacklist().setEnabled(false);
        getBtnCancelBlacklist().setVisible(true);
    }


    private void configureForDirect() {
        labelPanel.add(getLocalPortLabel());
        labelPanel.add(getTestUrlLabel());

        fieldPanel.add(wrapToPanel(getLocalPortJSpinner()));
        fieldPanel.add(getTestUrlJTextField());
    }

    private void configureForSocks5() {
        labelPanel.add(getProxyHostLabel());
        labelPanel.add(getProxyPortLabel());
        labelPanel.add(getUsernameLabel());
        labelPanel.add(getPasswordLabel());
        labelPanel.add(getStorePasswordLabel());
        labelPanel.add(getLocalPortLabel());
        labelPanel.add(getTestUrlLabel());

        fieldPanel.add(getProxyHostJTextField());
        fieldPanel.add(wrapToPanel(getProxyPortJSpinner()));
        fieldPanel.add(getUsernameJTextField());
        fieldPanel.add(getPasswordField());
        fieldPanel.add(getStorePasswordJCheckBox());
        fieldPanel.add(wrapToPanel(getLocalPortJSpinner()));
        fieldPanel.add(getTestUrlJTextField());
    }


    private JPanel wrapToPanel(java.awt.Component... components) {
        FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT, 0, 0);
        JPanel panel = new JPanel(flowLayout);
        if (components != null) {
            for (java.awt.Component comp : components) {
                panel.setPreferredSize(comp.getPreferredSize());
                panel.add(comp);
            }
        }
        return panel;
    }

    private JPanel getBtnPanel() {
        if (btnPanel == null) {
            btnPanel = new JPanel();
            btnPanel.add(getBtnStart());
            btnPanel.add(getBtnStop());
            btnPanel.add(getBtnCancelBlacklist());
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
            mntmExit.setIcon(new TunedImageIcon("application-exit.png"));
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
            mntmAbout.setIcon(new TunedImageIcon("dialog-information.png"));
            mntmAbout.addActionListener(e -> SwingUtils.showInfoMessage(this, "About", "Winfoom - Basic Proxy Facade" +
                    "\nVersion: " + systemConfig.getAppVersion()
                    + "\nProject home page: https://github.com/ecovaci/winfoom"
                    + "\nLicense: Apache 2.0"));
        }
        return mntmAbout;
    }

    // ------- End Menu

    private JTextField createTextField(String text) {
        JTextField textField = new JTextField(text);
        textField.setPreferredSize(new Dimension(220, 25));
        textField.setMinimumSize(new Dimension(6, 25));
        return textField;
    }

    private JSpinner createJSpinner(Integer value) {
        JSpinner jSpinner = new JSpinner();
        jSpinner.setPreferredSize(new Dimension(60, 25));
        jSpinner.setEditor(new JSpinner.NumberEditor(jSpinner, "#"));
        SwingUtils.commitsOnValidEdit(jSpinner);
        jSpinner.setValue(value);
        return jSpinner;
    }


    private void disableAll() {
        SwingUtils.setEnabled(getContentPane(), false);
    }

    private boolean isValidInput() {

        if ((proxyConfig.getProxyType().isSocks() || proxyConfig.getProxyType().isHttp())
                && StringUtils.isBlank(proxyConfig.getProxyHost())) {
            SwingUtils.showErrorMessage(this, "Fill in the proxy host");
            return false;
        }

        if ((proxyConfig.getProxyType().isSocks() || proxyConfig.getProxyType().isHttp())) {
            if (proxyConfig.getProxyPort() == null || !HttpUtils.isValidPort(proxyConfig.getProxyPort())) {
                SwingUtils.showErrorMessage(this, "Fill in a valid proxy port, between 1 and 65535");
                return false;
            }
        }

        if (proxyConfig.isAutoConfig() && StringUtils.isBlank(proxyConfig.getProxyPacFileLocation())) {
            SwingUtils.showErrorMessage(this, "Fill in a valid Pac file location");
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
            proxyValidator.testProxyConfig();
        } catch (CredentialException e) {
            SwingUtils.showErrorMessage(this, "Wrong user/password!");
            return false;
        } catch (UnknownHostException e) {
            SwingUtils.showErrorMessage(this, "Wrong proxy host!");
            return false;
        } catch (HttpHostConnectException e) {
            SwingUtils.showErrorMessage(this, "Wrong proxy port!");
            return false;
        } catch (ConnectTimeoutException e) {
            SwingUtils.showErrorMessage(this, "Wrong proxy host/port!");
            return false;
        } catch (Exception e) {
            SwingUtils.showErrorMessage(this, "Validation error: "
                    + (e.getMessage() != null ? e.getMessage() : "See the log file for details!"));
            return false;
        }

        return true;
    }

    private void startServer() {
        if (proxyConfig.getProxyType().isSocks5()) {
            if (StringUtils.isNotEmpty(proxyConfig.getProxyUsername())
                    && StringUtils.isEmpty(proxyConfig.getProxyPassword())) {
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

    private static class TunedImageIcon extends ImageIcon {

        TunedImageIcon(String filename) {
            super(SwingUtils.loadImage(AppFrame.class, filename));
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

    private interface TextChangeListener extends DocumentListener {
        @Override
        default void insertUpdate(DocumentEvent e) {
            onTextChange(e);
        }

        @Override
        default void removeUpdate(DocumentEvent e) {
            onTextChange(e);
        }

        @Override
        default void changedUpdate(DocumentEvent e) {
            onTextChange(e);
        }

        void onTextChange(DocumentEvent e);
    }

}