package CitySentinal;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.*;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.sql.*;

/**
 * LoginScreen — shown before MainDashboard.
 *
 * Credentials are validated against a `users` table in MySQL.
 * If that table doesn't exist yet, it falls back to hardcoded
 * demo credentials (admin / admin123) so the app always works.
 *
 * SQL to create the users table (run once in MySQL):
 *   CREATE TABLE users (
 *       user_id   INT AUTO_INCREMENT PRIMARY KEY,
 *       username  VARCHAR(50)  NOT NULL UNIQUE,
 *       password  VARCHAR(255) NOT NULL,
 *       full_name VARCHAR(100)
 *   );
 *   INSERT INTO users (username, password, full_name)
 *   VALUES ('admin', 'admin123', 'System Administrator');
 */
public class LoginScreen {

    // ── Hardcoded fallback credentials ────────────────────────────────────
    private static final String FALLBACK_USER = "admin";
    private static final String FALLBACK_PASS = "admin123";

    // ── Palette (matches MainDashboard) ───────────────────────────────────
    private static final String C_BG      = "#0f1623";
    private static final String C_CARD    = "#1a2235";
    private static final String C_ACCENT  = "#4f46e5";
    private static final String C_ACCENT2 = "#818cf8";
    private static final String C_BORDER  = "#2d3748";
    private static final String C_TEXT    = "#e2e8f0";
    private static final String C_MUTED   = "#64748b";
    private static final String C_RED     = "#ef4444";

    public void show(Stage primaryStage) {

        // ── Root ──────────────────────────────────────────────────────────
        StackPane root = new StackPane();
        root.setStyle("-fx-background-color: " + C_BG + ";");

        // ── Animated background dots ──────────────────────────────────────
        Pane bgPane = new Pane();
        bgPane.setMouseTransparent(true);
        addBackgroundDots(bgPane);

        // ── Card ──────────────────────────────────────────────────────────
        VBox card = new VBox(18);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(40, 44, 40, 44));
        card.setMaxWidth(400);
        card.setStyle(
            "-fx-background-color: " + C_CARD + ";" +
            "-fx-background-radius: 16;" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 16;"
        );

        // Logo row
        HBox logoRow = new HBox(8);
        logoRow.setAlignment(Pos.CENTER);
        Circle logoDot = new Circle(6, Color.web(C_ACCENT2));
        Label logoLabel = new Label("SentinelCity");
        logoLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        logoLabel.setTextFill(Color.WHITE);
        logoRow.getChildren().addAll(logoDot, logoLabel);

        Label tagline = new Label("Urban Cyber Threat Monitor");
        tagline.setFont(Font.font("System", 12));
        tagline.setTextFill(Color.web(C_MUTED));

        // Divider
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + C_BORDER + ";");

        // Heading
        Label heading = new Label("Sign in to your account");
        heading.setFont(Font.font("System", FontWeight.BOLD, 15));
        heading.setTextFill(Color.web(C_TEXT));

        // Username field
        VBox userBox = fieldBox("Username", "Enter username");
        TextField usernameField = (TextField) ((VBox) userBox).getChildren().get(1);

        // Password field
        VBox passBox = fieldBox("Password", "Enter password");
        PasswordField passwordField = new PasswordField();
        styleField(passwordField, "Enter password");
        ((VBox) passBox).getChildren().set(1, passwordField);

        // Error label
        Label errorLabel = new Label("");
        errorLabel.setFont(Font.font("System", 12));
        errorLabel.setTextFill(Color.web(C_RED));
        errorLabel.setVisible(false);
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(312);

        // Login button
        Button loginBtn = new Button("Sign in");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setPrefHeight(42);
        loginBtn.setFont(Font.font("System", FontWeight.BOLD, 13));
        loginBtn.setStyle(
            "-fx-background-color: " + C_ACCENT + ";" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;"
        );
        loginBtn.setOnMouseEntered(e -> loginBtn.setStyle(
            "-fx-background-color: #4338ca;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;"
        ));
        loginBtn.setOnMouseExited(e -> loginBtn.setStyle(
            "-fx-background-color: " + C_ACCENT + ";" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 8;" +
            "-fx-cursor: hand;"
        ));

        // Hint
        Label hint = new Label("Demo credentials: admin / admin123");
        hint.setFont(Font.font("System", 11));
        hint.setTextFill(Color.web(C_MUTED));

        card.getChildren().addAll(
            logoRow, tagline, sep, heading,
            userBox, passBox, errorLabel, loginBtn, hint
        );

        // ── Login action ──────────────────────────────────────────────────
        Runnable doLogin = () -> {
            String user = usernameField.getText().trim();
            String pass = passwordField.getText();

            if (user.isEmpty() || pass.isEmpty()) {
                showError(errorLabel, "Please enter both username and password.");
                return;
            }

            loginBtn.setText("Signing in…");
            loginBtn.setDisable(true);

            new Thread(() -> {
                boolean ok = authenticate(user, pass);
                Platform.runLater(() -> {
                    if (ok) {
                        // Fade out then launch dashboard
                        FadeTransition ft = new FadeTransition(Duration.millis(400), root);
                        ft.setFromValue(1); ft.setToValue(0);
                        ft.setOnFinished(ev -> {
                            MainDashboard dashboard = new MainDashboard();
                            try { dashboard.start(primaryStage); }
                            catch (Exception ex) { ex.printStackTrace(); }
                        });
                        ft.play();
                    } else {
                        loginBtn.setText("Sign in");
                        loginBtn.setDisable(false);
                        shakeCard(card);
                        showError(errorLabel, "Invalid username or password.");
                    }
                });
            }).start();
        };

        loginBtn.setOnAction(e -> doLogin.run());
        passwordField.setOnAction(e -> doLogin.run());
        usernameField.setOnAction(e -> passwordField.requestFocus());

        // ── Layout ────────────────────────────────────────────────────────
        root.getChildren().addAll(bgPane, card);

        Scene scene = new Scene(root, 900, 600);
        primaryStage.setTitle("SentinelCity – Login");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(600);
        primaryStage.setMinHeight(500);
        primaryStage.show();

        // Slide card in
        card.setTranslateY(30);
        card.setOpacity(0);
        Timeline slideIn = new Timeline(
            new KeyFrame(Duration.millis(500),
                new KeyValue(card.translateYProperty(), 0, Interpolator.EASE_OUT),
                new KeyValue(card.opacityProperty(), 1, Interpolator.EASE_OUT)
            )
        );
        slideIn.play();

        Platform.runLater(usernameField::requestFocus);
    }

    // ── Authentication ────────────────────────────────────────────────────
    private boolean authenticate(String username, String password) {
        // Try DB first
        try (Connection conn = DBConnection.getConnection()) {
            if (conn != null) {
                String sql = "SELECT password FROM users WHERE username = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, username);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        return password.equals(rs.getString("password"));
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("DB auth failed, using fallback: " + e.getMessage());
        }
        // Fallback to hardcoded credentials
        return FALLBACK_USER.equals(username) && FALLBACK_PASS.equals(password);
    }

    // ── UI helpers ────────────────────────────────────────────────────────
    private VBox fieldBox(String labelText, String prompt) {
        VBox box = new VBox(6);
        Label lbl = new Label(labelText);
        lbl.setFont(Font.font("System", FontWeight.BOLD, 12));
        lbl.setTextFill(Color.web(C_TEXT));
        TextField tf = new TextField();
        styleField(tf, prompt);
        box.getChildren().addAll(lbl, tf);
        return box;
    }

    private void styleField(TextField tf, String prompt) {
        tf.setPromptText(prompt);
        tf.setPrefHeight(40);
        tf.setFont(Font.font("System", 13));
        tf.setStyle(
            "-fx-background-color: #0f1623;" +
            "-fx-text-fill: #e2e8f0;" +
            "-fx-prompt-text-fill: #4b5563;" +
            "-fx-border-color: " + C_BORDER + ";" +
            "-fx-border-radius: 7;" +
            "-fx-background-radius: 7;" +
            "-fx-padding: 0 12 0 12;"
        );
        tf.focusedProperty().addListener((obs, old, focused) ->
            tf.setStyle(tf.getStyle().replace(
                focused ? C_BORDER : C_ACCENT2,
                focused ? C_ACCENT2 : C_BORDER
            ))
        );
    }

    private void showError(Label lbl, String msg) {
        lbl.setText(msg);
        lbl.setVisible(true);
        FadeTransition ft = new FadeTransition(Duration.millis(200), lbl);
        ft.setFromValue(0); ft.setToValue(1); ft.play();
    }

    private void shakeCard(VBox card) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(60), card);
        tt.setByX(10);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
        tt.setOnFinished(e -> card.setTranslateX(0));
        tt.play();
    }

    private void addBackgroundDots(Pane pane) {
        double[][] positions = {
            {60, 80, 3}, {150, 200, 2}, {700, 100, 4}, {800, 400, 2.5},
            {200, 450, 3}, {600, 500, 2}, {400, 50, 2}, {50, 350, 3.5},
            {750, 250, 2}, {350, 520, 2.5}, {500, 300, 1.5}, {100, 150, 2}
        };
        for (double[] p : positions) {
            Circle c = new Circle(p[0], p[1], p[2], Color.web(C_ACCENT2, 0.25));
            pane.getChildren().add(c);

            // Gentle pulse animation
            FadeTransition ft = new FadeTransition(Duration.seconds(2 + Math.random() * 2), c);
            ft.setFromValue(0.1); ft.setToValue(0.5);
            ft.setCycleCount(Animation.INDEFINITE);
            ft.setAutoReverse(true);
            ft.setDelay(Duration.seconds(Math.random() * 2));
            ft.play();
        }

        // Subtle grid lines
        for (int x = 0; x < 900; x += 80) {
            Rectangle line = new Rectangle(x, 0, 0.5, 600);
            line.setFill(Color.web(C_ACCENT2, 0.04));
            pane.getChildren().add(line);
        }
        for (int y = 0; y < 600; y += 80) {
            Rectangle line = new Rectangle(0, y, 900, 0.5);
            line.setFill(Color.web(C_ACCENT2, 0.04));
            pane.getChildren().add(line);
        }
    }
}
