package com.optem.ui;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.prefs.Preferences;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Panel de administración de IA: monitor real, sin prometer cambiar secretos de Ubuntu. */
public class AdminIAView {
    private String colorFuerte, colorBgPanel, colorTextMain, colorTextMuted, colorBorder;

    public VBox getView() {
        leerPreferencias();
        VBox main=new VBox(25);main.setPadding(new Insets(30,40,30,40));
        Label titulo=new Label("🤖 Estado del asistente OPTEM");
        titulo.setStyle("-fx-font-weight: bold; -fx-font-size: 28px; -fx-text-fill: "+colorTextMain+";");
        Label subtitulo=new Label("Verifica la conexión de OptemBot sin mostrar ni modificar claves privadas.");
        subtitulo.setStyle("-fx-text-fill: "+colorTextMuted+";");
        VBox encabezado=new VBox(6,titulo,subtitulo);
        AdminPremiumVisual.header(encabezado);

        VBox tarjeta=new VBox(15);tarjeta.setPadding(new Insets(25));
        tarjeta.setStyle("-fx-background-color: "+colorBgPanel+"; -fx-border-color: "+colorBorder+
            "; -fx-border-radius: 14; -fx-background-radius: 14;");
        Label seccion=new Label("Conexión con la IA de Ubuntu Server");
        seccion.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: "+colorTextMain+";");
        Label modelo=new Label("Modelo y API Key: administrados por el servicio optem-ia en Ubuntu."
            +"\nPor seguridad no pueden consultarse ni sobrescribirse desde este panel.");
        modelo.setWrapText(true);modelo.setStyle("-fx-text-fill: "+colorTextMuted+";");
        Label estado=new Label("Pulsa el botón para comprobar el estado de OptemBot.");
        estado.setWrapText(true);estado.setStyle("-fx-text-fill: "+colorTextMain+";");
        Button comprobar=new Button("↻ Comprobar conexión de IA");
        comprobar.setStyle("-fx-background-color: "+colorFuerte+"; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12 20;");
        comprobar.setOnAction(e->comprobarServicio(comprobar,estado));
        tarjeta.getChildren().addAll(seccion,modelo,estado,comprobar);
        AdminPremiumVisual.panel(tarjeta);
        AdminPremiumVisual.primary(comprobar);
        main.getChildren().addAll(encabezado,tarjeta);
        ScrollPane scroll=new ScrollPane(main);scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox resultado=new VBox(scroll);VBox.setVgrow(scroll,Priority.ALWAYS);
        return resultado;
    }

    private void comprobarServicio(Button btn, Label estado){
        String url=System.getenv().getOrDefault("OPTEM_IA_URL","http://100.67.119.35:8765/chat");
        URI health;
        try{health=URI.create(url).resolve("/health");}
        catch(Exception ex){new OptemAlert(Alert.AlertType.ERROR,"OPTEM_IA_URL es inválida.").showAndWait();return;}
        btn.setDisable(true);estado.setText("Comprobando el servicio de IA…");
        HttpRequest request=HttpRequest.newBuilder(health)
            .GET().timeout(Duration.ofSeconds(8)).build();
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build()
            .sendAsync(request,HttpResponse.BodyHandlers.ofString())
            .whenComplete((r,err)->Platform.runLater(()->{
                btn.setDisable(false);
                if(err!=null){estado.setText("Sin conexión: "+err.getMessage());return;}
                if(r.statusCode()==200 && r.body().contains("ok"))
                    estado.setText("● OptemBot en línea. Ubuntu respondió correctamente.");
                else estado.setText("El servicio respondió HTTP "+r.statusCode()+". Revisa optem-ia.service.");
            }));
    }

    private void leerPreferencias(){
        Preferences prefs=Preferences.userNodeForPackage(DashboardView.class);
        String correo=prefs.get("LAST_ACTIVE_ACCOUNT","");
        boolean oscuro=prefs.getBoolean("PREF_DARK_MODE_"+correo,false);
        colorFuerte=prefs.get("PREF_ACCENT_COLOR_"+correo,"#3D5A4D");
        colorBgPanel=oscuro?"#1e1e1e":"#ffffff";
        colorTextMain=oscuro?"#f8f9fa":"#1e293b";
        colorTextMuted=oscuro?"#94a3b8":"#64748b";
        colorBorder=oscuro?"#333333":"#d7e6e0";
    }
}
