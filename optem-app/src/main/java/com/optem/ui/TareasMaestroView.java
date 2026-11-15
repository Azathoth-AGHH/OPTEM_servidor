package com.optem.ui;

import java.awt.Desktop;
import java.util.Locale;
import java.io.File;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import com.optem.controllers.TareasMaestroController;
import com.optem.logic.NotificacionManager;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class TareasMaestroView {

    private final TareasMaestroController controller = new TareasMaestroController();
    private String colorFuerte, colorBgBase, colorBgPanel, colorTextMain, colorTextMuted, colorBorder;
    private VBox contenedorTarjetas;
    private ComboBox<String> filtroMateria;
    private boolean actualizandoFiltro=false;
    private String rutaArchivoSeleccionado = "";

    public VBox getView() {
        leerPreferencias();
        VBox layoutMain = new VBox(20);
        layoutMain.setPadding(new Insets(30, 40, 30, 40));
        layoutMain.setStyle("-fx-background-color: transparent;");

        VBox titleBox = new VBox(5);
        Label titulo = new Label("📤 Panel de Asignaciones (Para Alumnos)");
        titulo.setStyle("-fx-font-weight: 800; -fx-font-size: 28px; -fx-text-fill: " + colorTextMain + ";");
        Label subtitulo = new Label("Crea tareas, adjunta PDFs/Word, pon fechas límite y revisa las entregas.");
        subtitulo.setStyle("-fx-text-fill: " + colorTextMuted + "; -fx-font-size: 14px;");
        titleBox.getChildren().addAll(titulo, subtitulo);

        HBox body = new HBox(25);
        body.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(body, Priority.ALWAYS);

        VBox panelCrear = new VBox(15);
        panelCrear.setPrefWidth(400);
        panelCrear.setPadding(new Insets(25));
        panelCrear.setStyle("-fx-background-color: " + colorBgPanel + "; -fx-border-color: " + colorBorder + "; -fx-border-radius: 12; -fx-background-radius: 12;");

        Label lblCrearTit = new Label("Nueva Asignación");
        lblCrearTit.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + colorFuerte + ";");

        ComboBox<String> cmbMateria = new ComboBox<>();
        cmbMateria.setPromptText("Selecciona el Grupo...");
        cmbMateria.getItems().addAll(controller.getMisGruposOficiales());
        cmbMateria.setStyle("-fx-background-color: " + colorBgBase + "; -fx-border-color: " + colorBorder + "; -fx-border-radius: 6;");
        cmbMateria.setMaxWidth(Double.MAX_VALUE);

        TextField txtTitulo = new TextField();
        txtTitulo.setPromptText("Título de la tarea...");
        txtTitulo.setStyle("-fx-background-color: " + colorBgBase + "; -fx-text-fill: " + colorTextMain + "; -fx-border-color: " + colorBorder + "; -fx-border-radius: 6;");

        TextArea txtDesc = new TextArea();
        txtDesc.setPromptText("Instrucciones detalladas...");
        txtDesc.setPrefRowCount(4);
        txtDesc.setStyle("-fx-control-inner-background: " + colorBgBase + "; -fx-text-fill: " + colorTextMain + "; -fx-prompt-text-fill: " + colorTextMuted + "; -fx-border-color: " + colorBorder + "; -fx-border-radius: 6;");

        VBox boxFechaHora = new VBox(8);
        DatePicker dpFecha = new DatePicker(LocalDate.now());
        dpFecha.setStyle("-fx-control-inner-background: " + colorBgBase + ";");
        dpFecha.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dpFecha, Priority.ALWAYS);

        // Hora (0-12), minutos (0-59), AM/PM: se guarda en el formato ya usado por MySQL.
        ComboBox<Integer> cmbHora = new ComboBox<>();
        ComboBox<Integer> cmbMinutos = new ComboBox<>();
        ComboBox<String> cmbPeriodo = new ComboBox<>();
        for (int h=0;h<=12;h++) cmbHora.getItems().add(h);
        for (int m=0;m<=59;m++) cmbMinutos.getItems().add(m);
        cmbPeriodo.getItems().addAll("AM", "PM");
        cmbHora.setValue(11);cmbMinutos.setValue(59);cmbPeriodo.setValue("PM");
        cmbHora.setTooltip(new javafx.scene.control.Tooltip("Hora 0-12 (0 equivale a 12)"));
        cmbMinutos.setTooltip(new javafx.scene.control.Tooltip("Minutos 0-59"));
        cmbHora.setPrefWidth(72);cmbMinutos.setPrefWidth(72);cmbPeriodo.setPrefWidth(72);
        HBox selectorHora=new HBox(8,new Label("Hora:"),cmbHora,new Label("Min:"),cmbMinutos,cmbPeriodo);
        selectorHora.setAlignment(Pos.CENTER_LEFT);
        boxFechaHora.getChildren().addAll(dpFecha,selectorHora);

        VBox boxAdjunto = new VBox(5);
        Label lblArchivoSubido = new Label("Sin archivo adjunto");
        lblArchivoSubido.setStyle("-fx-text-fill: " + colorTextMuted + "; -fx-font-size: 11px; -fx-font-style: italic;");
        
        Button btnAdjuntar = new Button("📎 Buscar Documento (Word/PDF/Excel)");
        btnAdjuntar.setStyle("-fx-background-color: transparent; -fx-text-fill: " + colorFuerte + "; -fx-border-color: " + colorFuerte + "; -fx-border-radius: 6; -fx-cursor: hand;");
        btnAdjuntar.setMaxWidth(Double.MAX_VALUE);
        
        btnAdjuntar.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Seleccionar Material de Apoyo");
            File file = fileChooser.showOpenDialog(layoutMain.getScene().getWindow());
            if (file != null) {
                rutaArchivoSeleccionado = file.getAbsolutePath();
                lblArchivoSubido.setText("📎 Adjunto: " + file.getName());
                lblArchivoSubido.setStyle("-fx-text-fill: " + colorFuerte + "; -fx-font-size: 12px; -fx-font-weight: bold;");
            }
        });
        boxAdjunto.getChildren().addAll(btnAdjuntar, lblArchivoSubido);

        Button btnPublicar = new Button("Publicar Asignación");
        btnPublicar.setStyle("-fx-background-color: " + colorFuerte + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10; -fx-background-radius: 8; -fx-cursor: hand;");
        btnPublicar.setMaxWidth(Double.MAX_VALUE);

        btnPublicar.setOnAction(e -> {
            if (cmbMateria.getValue() == null || txtTitulo.getText().isBlank() || dpFecha.getValue()==null) {
                NotificacionManager.mostrarNotificacion("Faltan Datos", "Selecciona un grupo, título y fecha límite.", "ERROR");
                return;
            }
            final String tituloTarea=txtTitulo.getText(), grupoTarea=cmbMateria.getValue(), instrucciones=txtDesc.getText();
            final LocalDate limite=dpFecha.getValue();
            final int horaElegida=cmbHora.getValue(),minutoElegido=cmbMinutos.getValue();
            final String hora=String.format(Locale.US,"%02d:%02d %s",
                horaElegida==0?12:horaElegida,minutoElegido,cmbPeriodo.getValue());
            final String archivo=rutaArchivoSeleccionado;
            btnPublicar.setDisable(true);
            btnPublicar.setText("Publicando...");
            javafx.concurrent.Task<Void> tareaPublicacion=new javafx.concurrent.Task<>() {
                @Override protected Void call() {
                    controller.publicarNuevaTarea(tituloTarea,grupoTarea,instrucciones,limite,hora,archivo);
                    return null;
                }
            };
            tareaPublicacion.setOnSucceeded(ev -> {
                btnPublicar.setDisable(false);btnPublicar.setText("Publicar Asignación");
                NotificacionManager.mostrarNotificacion("Tarea Publicada", "Asignación guardada para el grupo "+grupoTarea, "INFO");
                txtTitulo.clear();txtDesc.clear();cmbMateria.setValue(null);rutaArchivoSeleccionado="";
                lblArchivoSubido.setText("Sin archivo adjunto");
                lblArchivoSubido.setStyle("-fx-text-fill: "+colorTextMuted+"; -fx-font-size: 11px; -fx-font-style: italic;");
                actualizarListaTareas();
            });
            tareaPublicacion.setOnFailed(ev -> {
                btnPublicar.setDisable(false);btnPublicar.setText("Publicar Asignación");
                Throwable error=tareaPublicacion.getException();
                NotificacionManager.mostrarNotificacion("Error al publicar",error==null?"Operación fallida":error.getMessage(),"ERROR");
            });
            Thread hilo=new Thread(tareaPublicacion,"optem-publicar-asignacion");
            hilo.setDaemon(true);hilo.start();
        });

        panelCrear.getChildren().addAll(lblCrearTit, cmbMateria, txtTitulo, txtDesc, new Label("Límite de Entrega:"), boxFechaHora, boxAdjunto, new Region(), btnPublicar);

        VBox panelLista = new VBox(15);
        HBox.setHgrow(panelLista, Priority.ALWAYS);
        
        contenedorTarjetas = new VBox(15);
        ScrollPane scroll = new ScrollPane(contenedorTarjetas);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        filtroMateria=new ComboBox<>();
        filtroMateria.setPromptText("Filtrar por materia / grupo");
        filtroMateria.setMaxWidth(Double.MAX_VALUE);
        filtroMateria.valueProperty().addListener((o,ant,nuevo)->{
            if(!actualizandoFiltro)actualizarListaTareas();
        });
        panelLista.getChildren().addAll(filtroMateria,scroll);
        
        body.getChildren().addAll(panelCrear, panelLista);
        layoutMain.getChildren().addAll(titleBox, body);
        
        actualizarListaTareas();
        return layoutMain;
    }

    private void actualizarListaTareas() {
        contenedorTarjetas.getChildren().clear();
        List<Map<String,String>> tareas=controller.getTodasLasAsignaciones();
        java.util.TreeSet<String> materias=new java.util.TreeSet<>();
        for(var t:tareas)materias.add(t.get("materia"));
        String actual=filtroMateria.getValue();
        actualizandoFiltro=true;
        try{
            filtroMateria.getItems().setAll("Todas las materias");
            filtroMateria.getItems().addAll(materias);
            filtroMateria.setValue(actual!=null && filtroMateria.getItems().contains(actual)?actual:"Todas las materias");
        }finally{actualizandoFiltro=false;}
        for (Map<String, String> tarea : tareas) {
            if(actual==null || "Todas las materias".equals(actual) || actual.equals(tarea.get("materia")))
                contenedorTarjetas.getChildren().add(crearTarjetaAsignacion(tarea));
        }
    }

    private VBox crearTarjetaAsignacion(Map<String, String> tarea) {
        VBox card = new VBox(15);
        card.setPadding(new Insets(20));
        card.setStyle("-fx-background-color: " + colorBgPanel + "; -fx-border-color: " + colorBorder + "; -fx-border-radius: 10; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 10, 0, 2, 2);");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label lblMat = new Label(tarea.get("materia"));
        lblMat.setStyle("-fx-background-color: " + colorFuerte + "20; -fx-text-fill: " + colorFuerte + "; -fx-font-weight: bold; -fx-padding: 4 8; -fx-background-radius: 6; -fx-font-size: 11px;");
        
        boolean estaAbierta = tarea.get("estado").equals("ABIERTA");
        Label lblEstado = new Label(estaAbierta ? "🟢 ABIERTA" : "🔴 CERRADA");
        lblEstado.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: " + (estaAbierta ? "#10b981" : "#ef4444") + ";");
        
        Region sp1 = new Region(); HBox.setHgrow(sp1, Priority.ALWAYS);
        header.getChildren().addAll(lblMat, sp1, lblEstado);

        Label lblTit = new Label(tarea.get("titulo"));
        lblTit.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: " + colorTextMain + ";");
        
        Label lblFecha = new Label("Límite: " + tarea.get("fecha") + " a las " + tarea.get("hora"));
        lblFecha.setStyle("-fx-text-fill: " + colorTextMuted + "; -fx-font-size: 12px;");

        VBox boxProgreso = new VBox(5);
        double entregasActuales = Double.parseDouble(tarea.get("entregas_actuales"));
        double total = Double.parseDouble(tarea.get("entregas_total"));
        
        HBox textProg = new HBox();
        Label lblEnt = new Label("Entregas completadas:"); lblEnt.setStyle("-fx-text-fill: " + colorTextMuted + ";");
        Region sp2 = new Region(); HBox.setHgrow(sp2, Priority.ALWAYS);
        Label lblNum = new Label((int)entregasActuales + " de " + (int)total); lblNum.setStyle("-fx-font-weight: bold; -fx-text-fill: " + colorTextMain + ";");
        textProg.getChildren().addAll(lblEnt, sp2, lblNum);
        
        ProgressBar pb = new ProgressBar(total == 0 ? 0 : entregasActuales / total);
        pb.setMaxWidth(Double.MAX_VALUE);
        pb.setStyle("-fx-accent: " + colorFuerte + "; -fx-control-inner-background: " + colorBgBase + ";");
        boxProgreso.getChildren().addAll(textProg, pb);

        HBox footer = new HBox(15);
        footer.setAlignment(Pos.CENTER_RIGHT);
        
        String rutaArchivo = tarea.get("ruta_archivo");
        if (rutaArchivo != null && !rutaArchivo.isEmpty()) {
            Button btnAbrir = new Button("📎 Ver Documento Adjunto");
            btnAbrir.setStyle("-fx-background-color: transparent; -fx-text-fill: #3b82f6; -fx-cursor: hand; -fx-font-weight: bold;");
            btnAbrir.setOnAction(e -> {
                try {
                    File file = new File(controller.descargarAdjuntoProfesor(tarea.get("id")));
                    if (file.exists()) Desktop.getDesktop().open(file);
                    else NotificacionManager.mostrarNotificacion("Error", "El archivo original fue movido.", "ERROR");
                } catch (Exception ex) {
                    new OptemAlert(javafx.scene.control.Alert.AlertType.ERROR,
                        "No se pudo abrir el documento adjunto: "+ex.getMessage()).showAndWait();
                }
            });
            footer.getChildren().add(btnAbrir);
        }

        Region spFooter = new Region(); HBox.setHgrow(spFooter, Priority.ALWAYS);
        footer.getChildren().add(spFooter);

        Button btnVer = new Button("Ver Trabajos");
        btnVer.setStyle("-fx-background-color: transparent; -fx-border-color: " + colorBorder + "; -fx-text-fill: " + colorTextMain + "; -fx-border-radius: 6; -fx-cursor: hand;");
        btnVer.setOnAction(e -> abrirModalEntregas(tarea, card.getScene().getWindow()));

        Button btnCerrar = new Button("Cerrar Recepción");
        btnCerrar.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand;");
        btnCerrar.setOnAction(e -> {
            try {
                controller.cerrarRecepcionTarea(tarea.get("id"));
                actualizarListaTareas();
                new OptemAlert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Recepción cerrada correctamente. Los alumnos ya no podrán enviar trabajos.").showAndWait();
            } catch (RuntimeException ex) {
                new OptemAlert(javafx.scene.control.Alert.AlertType.ERROR,
                    "No se pudo cerrar la recepción: "+ex.getMessage()).showAndWait();
            }
        });

        if (estaAbierta) footer.getChildren().addAll(btnVer, btnCerrar);
        else { footer.getChildren().add(btnVer); card.setOpacity(0.7); }

        card.getChildren().addAll(header, lblTit, lblFecha, boxProgreso, footer);
        return card;
    }

    /** Lista simplificada: un único botón Revisar por estudiante. */
    private void abrirModalEntregas(Map<String, String> tarea, javafx.stage.Window owner) {
        javafx.stage.Stage modal=new javafx.stage.Stage();
        modal.initOwner(owner);modal.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        modal.setTitle("Trabajos Entregados - "+tarea.get("titulo"));
        VBox layout=new VBox(12);layout.setPadding(new Insets(22));
        layout.setStyle("-fx-background-color: linear-gradient(to bottom right, " + (StudentPremiumTheme.isDarkMode() ? "#202735, #171D29" : "#ffffff, #eff8f2 55%, #e8f2fa") + "); -fx-border-color: " + (StudentPremiumTheme.isDarkMode() ? "#48556A" : "#b4d9c8") + "; -fx-border-width: 2; -fx-background-radius: 16; -fx-border-radius: 16;");
        Label titulo=new Label("Trabajos Entregados");
        titulo.setStyle("-fx-font-weight: bold; -fx-font-size: 20px; -fx-text-fill: "+colorTextMain+";");
        Label sub=new Label(tarea.get("titulo")+" ("+tarea.get("materia")+")");
        sub.setStyle("-fx-text-fill: "+colorTextMuted+";");
        VBox filas=new VBox(9);
        List<Map<String,String>> entregas=controller.obtenerEntregasDeTarea(tarea.get("id"));
        if(entregas.isEmpty()){
            filas.getChildren().add(new Label("Ningún estudiante ha entregado aún."));
        }else{
            for(int i=0;i<entregas.size();i++){
                final int indice=i;
                Map<String,String> ent=entregas.get(i);
                HBox fila=new HBox(10);fila.setAlignment(Pos.CENTER_LEFT);fila.setPadding(new Insets(12));
                fila.setStyle("-fx-background-color: linear-gradient(to bottom right, " + (StudentPremiumTheme.isDarkMode() ? "#252F3D, #202735" : "#ffffff, #eaf3f0") + "); -fx-background-radius: 12; -fx-border-color: " + (StudentPremiumTheme.isDarkMode() ? "#48556A" : "#c7dcd5") + "; -fx-border-radius: 12; -fx-effect: dropshadow(three-pass-box, rgba(24,66,59,.12), 8,0,0,2);");
                VBox datos=new VBox(3);
                Label estudiante=new Label("👤 "+ent.get("alumno"));
                estudiante.setStyle("-fx-font-weight: bold; -fx-text-fill: "+colorTextMain+";");
                Label fecha=new Label("Entregado: "+ent.get("fecha_entrega"));
                fecha.setStyle("-fx-font-size: 11px; -fx-text-fill: "+colorTextMuted+";");
                datos.getChildren().addAll(estudiante,fecha);
                Region hueco=new Region();HBox.setHgrow(hueco,Priority.ALWAYS);
                Button revisar=new Button("Revisar");
                revisar.setStyle("-fx-background-color: linear-gradient(to bottom right, #108c46, #076858 65%, #143f67); -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 11; -fx-padding: 9 17; -fx-cursor: hand;");
                revisar.setOnAction(e->new RevisionEntregaView(controller,entregas,tarea.get("titulo"),tarea.get("materia"),this::actualizarListaTareas).mostrar(modal,indice));
                fila.getChildren().addAll(datos,hueco,revisar);
                filas.getChildren().add(fila);
            }
        }
        ScrollPane sc=new ScrollPane(filas);sc.setFitToWidth(true);sc.setPrefViewportHeight(350);
        sc.setStyle("-fx-background: "+colorBgPanel+"; -fx-border-color: transparent;");
        layout.getChildren().addAll(titulo,sub,sc);
        modal.setScene(new javafx.scene.Scene(layout,610,480)); OptemDialogStyle.apply(modal);
        modal.showAndWait();
    }

    private void leerPreferencias() {
        Preferences prefs = Preferences.userNodeForPackage(DashboardView.class);
        String correoActual = prefs.get("LAST_ACTIVE_ACCOUNT", "");
        boolean isDark = prefs.getBoolean("PREF_DARK_MODE_" + correoActual, false);
        colorFuerte = prefs.get("PREF_ACCENT_COLOR_" + correoActual, "#3D5A4D");
        colorBgBase = isDark ? "#121212" : "#f1f5f9"; colorBgPanel = isDark ? "#1e1e1e" : "#ffffff";
        colorTextMain = isDark ? "#f8f9fa" : "#1e293b"; colorTextMuted = isDark ? "#94a3b8" : "#64748b";
        colorBorder = isDark ? "#333333" : "#e2e8f0";
    }
}