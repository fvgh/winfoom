package org.kpax.winfoom.view;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.layout.BorderPane;
import org.kpax.winfoom.proxy.LocalProxyServer;
import org.kpax.winfoom.util.SwingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author Eugen Covaci {@literal eugen.covaci.q@gmail.com}
 * Created on 9/10/2019
 */
@Component
public class MainController {
    Logger logger = LoggerFactory.getLogger(MainController.class);

    @Autowired
    private LocalProxyServer localProxyServer;

    @FXML
    private BorderPane borderPane;


    public void start(ActionEvent actionEvent) {
        try {
            localProxyServer.start();
        } catch (Exception e) {
            logger.error("Error on starting proxy server", e);
            SwingUtils.showErrorMessage("Error on starting proxy server.\nSee the application's log for details.");
        }
    }
}
