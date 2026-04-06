package CitySentinal;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Entry point — launches the LoginScreen, which then opens MainDashboard.
 */
public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        new LoginScreen().show(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
