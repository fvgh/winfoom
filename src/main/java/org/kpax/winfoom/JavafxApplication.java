package org.kpax.winfoom;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.kpax.winfoom.util.LocalIOUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.Resource;

public class JavafxApplication extends Application {

    private ConfigurableApplicationContext applicationContext;

    private Stage primaryStage;

    @Override
    public void init() throws Exception {

        ApplicationContextInitializer<GenericApplicationContext> initializer = new ApplicationContextInitializer<GenericApplicationContext>() {
            @Override
            public void initialize(GenericApplicationContext genericApplicationContext) {
                genericApplicationContext.registerBean(JavafxApplication.class, () -> JavafxApplication.this);
                genericApplicationContext.registerBean(FileBasedConfigurationBuilder.class, () -> new Configurations()
                        .propertiesBuilder(LocalIOUtils.toPath(System.getProperty("user.dir"), "config",
                                "user.properties")));
            }
        };

        SpringApplication springApplication = new SpringApplication(FoomApplication.class);
        springApplication.addInitializers(initializer);
        this.applicationContext = springApplication.run(getParameters().getRaw().toArray(new String[0]));

    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        Resource fxml = this.applicationContext.getResource("classpath:/view/main.fxml");
        FXMLLoader fxmlLoader = new FXMLLoader(fxml.getURL());
        fxmlLoader.setControllerFactory(this.applicationContext::getBean);
        Parent root = fxmlLoader.load();
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setTitle("WinFoom");
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        this.applicationContext.close();
        Platform.exit();
    }


    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public void sizeToScene() {
        primaryStage.sizeToScene();
    }

    public BorderPane getMainContainer() {
        return (BorderPane) primaryStage.getScene().getRoot();
    }


}
