package com.optem.ui;

import java.util.prefs.Preferences;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class ChatBotView {

    private String colorFuerte, colorTextMain, colorTextMuted, colorBgPanel, colorBorder;

    // Recibe el Runnable para poder cerrarse desde el Dashboard
    public VBox getView(Runnable onClose) {
        leerPreferencias();
        // Fijar la cuenta que abrió este chat; nunca heredar la cuenta del token fijo.
        final String correoSesion = Preferences.userNodeForPackage(DashboardView.class)
                .get("LAST_ACTIVE_ACCOUNT", "").trim();

        VBox root = new VBox();
        root.getStyleClass().add("student-premium-chat");
        root.setStyle("-fx-background-color: " + colorBgPanel + "; -fx-border-color: " + colorBorder + "; -fx-border-width: 1; -fx-background-radius: 12; -fx-border-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 20, 0, 0, 5);");
        
        // --- HEADER DEL WIDGET ---
        HBox header = new HBox(12);
        header.getStyleClass().add("student-premium-chat-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(15));
        header.setStyle(StudentPremiumTheme.active() ?
            "-fx-background-color: linear-gradient(to right, derive(-student-accent,-17%), -student-accent, -student-accent-deep); -fx-background-radius: 15 15 0 0;" :
            "-fx-background-color: " + colorFuerte + "; -fx-background-radius: 11 11 0 0;");

        Label iconoIA = new Label("🤖");
        iconoIA.setStyle("-fx-font-size: 26px; -fx-text-fill: white;");

        VBox titleBox = new VBox(2);
        Label title = new Label("Asistente IA");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: white;");
        Label subtitle = new Label("OptemBot en desarrollo");
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.8);");
        titleBox.getChildren().addAll(title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnCerrar = new Button("✕");
        btnCerrar.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 16px; -fx-cursor: hand; -fx-font-weight: bold;");
        btnCerrar.setOnAction(e -> {
            if (onClose != null) onClose.run(); // Cierra el chat en el Dashboard
        });

        header.getChildren().addAll(iconoIA, titleBox, spacer, btnCerrar);

        // --- CHAT REAL: conserva encabezado, paleta y panel original ---
        VBox body = new VBox(12);
        body.setPadding(new Insets(16));
        VBox.setVgrow(body, Priority.ALWAYS);
        Label bienvenida = new Label("Hola, soy OptemBot. Pregúntame por tus materias, grupos o tareas.");
        bienvenida.setWrapText(true);
        bienvenida.setStyle("-fx-text-fill: " + colorTextMuted + "; -fx-font-size: 12px;");
        VBox mensajes = new VBox(9, bienvenida);
        javafx.scene.control.ScrollPane historial = new javafx.scene.control.ScrollPane(mensajes);
        historial.setFitToWidth(true);
        historial.setPrefViewportHeight(260);
        historial.setStyle("-fx-background: " + colorBgPanel + "; -fx-background-color: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(historial, Priority.ALWAYS);
        javafx.scene.control.TextField entrada = new javafx.scene.control.TextField();
        entrada.setPromptText("Escribe tu pregunta...");
        entrada.setStyle("-fx-background-color: " + colorBgPanel + "; -fx-text-fill: " + colorTextMain +
                         "; -fx-border-color: " + colorBorder + "; -fx-background-radius: 8; -fx-border-radius: 8;");
        Button enviar = new Button("Enviar");
        enviar.setStyle("-fx-background-color: " + colorFuerte + "; -fx-text-fill: white; -fx-background-radius: 8; -fx-cursor: hand;");
        HBox controles = new HBox(8, entrada, enviar);
        HBox.setHgrow(entrada, Priority.ALWAYS);
        Runnable enviarMensaje = () -> {
            String pregunta = entrada.getText().trim();
            if (pregunta.isEmpty()) return;
            String correoActivo = Preferences.userNodeForPackage(DashboardView.class)
                    .get("LAST_ACTIVE_ACCOUNT", "").trim();
            if (correoSesion.isEmpty() || !correoSesion.equalsIgnoreCase(correoActivo)) {
                entrada.clear();
                Label avisoSesion = new Label("OptemBot: Cambió la cuenta activa. Cierra y vuelve a abrir el chat.");
                avisoSesion.setWrapText(true);
                avisoSesion.setStyle("-fx-text-fill: " + colorTextMuted + ";");
                mensajes.getChildren().add(avisoSesion);
                return;
            }
            entrada.clear();
            Label usuario = new Label("Tú: " + pregunta);
            usuario.setWrapText(true);
            usuario.setStyle("-fx-text-fill: " + colorTextMain + "; -fx-font-weight: bold;");
            Label respuesta = new Label("OptemBot: Consultando...");
            respuesta.setWrapText(true);
            respuesta.setStyle("-fx-text-fill: " + colorTextMuted + ";");
            mensajes.getChildren().addAll(usuario, respuesta);
            enviar.setDisable(true);
            com.optem.logic.OptemIAClient.preguntar(correoSesion, pregunta).whenComplete((texto, error) ->
                javafx.application.Platform.runLater(() -> {
                    String cuentaActual = Preferences.userNodeForPackage(DashboardView.class)
                            .get("LAST_ACTIVE_ACCOUNT", "").trim();
                    if (!correoSesion.equalsIgnoreCase(cuentaActual)) {
                        respuesta.setText("OptemBot: La sesión cambió. Abre el chat desde la cuenta actual.");
                    } else {
                        respuesta.setText("OptemBot: " + (error != null ?
                            "No fue posible completar la consulta. Revisa el servicio de IA." : texto));
                    }
                    enviar.setDisable(false);
                    historial.setVvalue(1.0);
                }));
        };
        enviar.setOnAction(e -> enviarMensaje.run());
        entrada.setOnAction(e -> enviarMensaje.run());
        body.getChildren().addAll(historial, controles);

        root.getChildren().addAll(header, body);
        return root;
    }

    private void leerPreferencias() {
        Preferences prefs = Preferences.userNodeForPackage(DashboardView.class);
        String correoActual = prefs.get("LAST_ACTIVE_ACCOUNT", "");
        boolean isDark = prefs.getBoolean("PREF_DARK_MODE_" + correoActual, false);
        
        colorFuerte = prefs.get("PREF_ACCENT_COLOR_" + correoActual, "#3D5A4D");
        colorBgPanel = isDark ? "#1e1e1e" : "#ffffff";
        colorTextMain = isDark ? "#f8f9fa" : "#1e293b";
        colorTextMuted = isDark ? "#94a3b8" : "#64748b";
        colorBorder = isDark ? "#333333" : "#e2e8f0";
    }
}