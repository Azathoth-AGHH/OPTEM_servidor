package com.optem.logic;

import java.awt.Toolkit;
import java.util.prefs.Preferences;

import com.optem.ui.DashboardView;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class NotificacionManager {

    public static void mostrarNotificacion(String titulo, String mensaje, String tipo) {
        Platform.runLater(() -> {
            Preferences prefs = Preferences.userNodeForPackage(DashboardView.class);
            String correoActual = prefs.get("LAST_ACTIVE_ACCOUNT", "");
            String nivel = prefs.get("PREF_NOTIFICATIONS_" + correoActual, "COMPLETO");

            if (nivel.equals("APAGADO")) return;
            if (nivel.equals("PRIORITARIO") && !tipo.equals("URGENTE") && !tipo.equals("REUNION")) return;

            Toolkit.getDefaultToolkit().beep();

            Stage toastStage = new Stage();
            toastStage.initStyle(StageStyle.TRANSPARENT);
            toastStage.setAlwaysOnTop(true);

            VBox toastLayout = new VBox(5);
            toastLayout.setPadding(new Insets(15, 20, 15, 20));
            toastLayout.setStyle("-fx-background-color: linear-gradient(to bottom right, #0d813f, #086956 57%, #143b65); " +
                "-fx-background-radius: 17; -fx-border-color: #bde6c8; -fx-border-width: 1.5; " +
                "-fx-border-radius: 17; -fx-effect: dropshadow(three-pass-box, rgba(14,57,52,0.4), 20, 0, 0, 8);");

            // Mismo acento individual y diseño premium que las pantallas de OPTEM.
            // Solo estilo; no cambia las reglas ni el tiempo de las notificaciones.
            String rolActivo = prefs.get("ROLE_" + correoActual, "");
            if ("MAESTRO".equalsIgnoreCase(rolActivo) || "ESTUDIANTE".equalsIgnoreCase(rolActivo)) {
                String colorPreferido = prefs.get("PREF_ACCENT_COLOR_" + correoActual, "#3D5A4D");
                if ("ERROR".equalsIgnoreCase(tipo)) colorPreferido = "#B42332";
                else if ("URGENTE".equalsIgnoreCase(tipo)) colorPreferido = "#9B5F0A";
                try {
                    Color baseColor = Color.web(colorPreferido).interpolate(Color.BLACK, .31);
                    String tono = String.format(java.util.Locale.ROOT, "#%02X%02X%02X",
                        (int)Math.round(baseColor.getRed()*255),
                        (int)Math.round(baseColor.getGreen()*255),
                        (int)Math.round(baseColor.getBlue()*255));
                    toastLayout.setStyle("-fx-background-color: linear-gradient(to bottom right, derive(" + tono + ", 17%), " + tono + " 53%, derive(" + tono + ", -17%));" +
                        "-fx-background-radius: 17; -fx-border-color: derive(" + colorPreferido + ", 53%);" +
                        "-fx-border-width: 1.3; -fx-border-radius: 17;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(14,35,57,.31), 18, 0, 0, 7);");
                } catch (IllegalArgumentException ignored) {
                    // Se conserva el estilo original si una preferencia contiene un color inválido.
                }
            }

            Label lblTitulo = new Label(titulo);
            lblTitulo.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold; -fx-font-size: 16px;");
            
            Label lblMensaje = new Label(mensaje);
            lblMensaje.setWrapText(true);
            lblMensaje.setMaxWidth(370);
            lblMensaje.setStyle("-fx-text-fill: #edf9f3; -fx-font-size: 13px;");

            toastLayout.getChildren().addAll(lblTitulo, lblMensaje);

            toastLayout.setMinWidth(270);
            toastLayout.setMaxWidth(425);
            Scene scene = new Scene(toastLayout);
            var css = NotificacionManager.class.getResource("/css/optem-v6.css");
            if (css != null) scene.getStylesheets().add(css.toExternalForm());
            scene.setFill(Color.TRANSPARENT);
            toastStage.setScene(scene);

            Screen screen = Screen.getPrimary();
            Rectangle2D bounds = screen.getVisualBounds();
            toastStage.show();
            toastStage.setX(bounds.getMaxX() - toastStage.getWidth() - 20);
            
            toastStage.setY(bounds.getMaxY());
            FadeTransition ftIn = new FadeTransition(Duration.millis(300), toastLayout);
            ftIn.setFromValue(0); ftIn.setToValue(1); ftIn.play();
            
            javafx.animation.TranslateTransition tt = new javafx.animation.TranslateTransition(Duration.millis(300), toastLayout);
            tt.setFromY(50); tt.setToY(-20 - toastStage.getHeight()); tt.play();

            PauseTransition delay = new PauseTransition(Duration.seconds(3));
            delay.setOnFinished(e -> {
                FadeTransition ftOut = new FadeTransition(Duration.millis(300), toastLayout);
                ftOut.setFromValue(1); ftOut.setToValue(0);
                ftOut.setOnFinished(ev -> toastStage.close());
                ftOut.play();
            });
            delay.play();
        });
    }
}