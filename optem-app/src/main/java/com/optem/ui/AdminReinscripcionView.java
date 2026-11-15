package com.optem.ui;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import com.optem.controllers.AdminController;
import com.optem.controllers.DashboardController;
import com.optem.controllers.SolicitudesAgrupadasDAO;
import com.optem.logic.NotificacionManager;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

public class AdminReinscripcionView {

    private final DashboardController controller = new DashboardController();
    private final AdminController adminController = new AdminController();
    private final SolicitudesAgrupadasDAO solicitudDao = new SolicitudesAgrupadasDAO();

    private String colorFuerte, colorBgBase, colorBgPanel, colorTextMain, colorTextMuted, colorBorder, fontSizeActual;

    private boolean periodoAbierto = false;
    private Preferences prefsGlobales;
    
    private VBox listaAlumnosRegistrados; 

    public VBox getView() {
        leerPreferencias();
        
        prefsGlobales = Preferences.userRoot().node("optem_erp_global"); // solo preferencia local histórica
        periodoAbierto = adminController.isReinscripcionActiva();

        VBox layoutMain = new VBox(25);
        layoutMain.setPadding(new Insets(30, 40, 30, 40));
        layoutMain.setStyle("-fx-background-color: transparent; -fx-font-size: " + fontSizeActual + ";");

        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        
        VBox titleBox = new VBox(5);
        Label titulo = new Label("📑 Centro de Reinscripciones y Oferta");
        titulo.setStyle("-fx-font-weight: 800; -fx-font-size: 28px; -fx-text-fill: " + colorTextMain + ";");
        Label subtitulo = new Label("Controla la apertura del ciclo, asigna maestros y entrega hojas de pago.");
        subtitulo.setStyle("-fx-text-fill: " + colorTextMuted + "; -fx-font-size: 14px;");
        titleBox.getChildren().addAll(titulo, subtitulo);

        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);

        Button btnNotificar = new Button("📢 Notificar a Estudiantes");
        btnNotificar.setStyle("-fx-background-color: transparent; -fx-border-color: " + colorFuerte + "; -fx-border-radius: 8; -fx-text-fill: " + colorFuerte + "; -fx-font-weight: bold; -fx-padding: 10 20; -fx-cursor: hand;");
        btnNotificar.setOnAction(e -> new OptemAlert(javafx.scene.control.Alert.AlertType.INFORMATION,
            "El envío masivo de correos no está habilitado en esta versión. " +
            "Puedes publicar avisos desde la sección correspondiente. No se envió ningún correo.").showAndWait());

        header.getChildren().addAll(titleBox, spacer, btnNotificar);
        AdminPremiumVisual.header(header);
        AdminPremiumVisual.secondary(btnNotificar);

        HBox controlBox = new HBox(20);
        controlBox.setAlignment(Pos.CENTER_LEFT);
        controlBox.setPadding(new Insets(25));
        controlBox.setStyle("-fx-background-color: " + colorBgPanel + "; -fx-border-color: " + colorFuerte + "; -fx-border-width: 2; -fx-border-radius: 12; -fx-background-radius: 12; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 10, 0, 2, 2);");

        VBox infoEstado = new VBox(5);
        Label lblEstadoTit = new Label("Estado Actual del Sistema:");
        lblEstadoTit.setStyle("-fx-text-fill: " + colorTextMuted + "; -fx-font-size: 14px;");
        
        HBox estadoVisual = new HBox(10);
        estadoVisual.setAlignment(Pos.CENTER_LEFT);
        Circle dotEstado = new Circle(6, Color.web(periodoAbierto ? "#10b981" : "#ef4444"));
        Label lblEstadoVal = new Label(periodoAbierto ? "PERIODO DE REINSCRIPCIÓN: ABIERTO" : "PERIODO DE REINSCRIPCIÓN: CERRADO");
        lblEstadoVal.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + (periodoAbierto ? "#10b981" : "#ef4444") + ";");
        estadoVisual.getChildren().addAll(dotEstado, lblEstadoVal);
        
        Label lblAlerta = new Label(periodoAbierto ? "Los estudiantes ahora pueden acceder al módulo y generar sus referencias." : "El módulo de estudiantes está bloqueado. Nadie puede reinscribirse.");
        lblAlerta.setStyle("-fx-text-fill: " + colorTextMain + "; -fx-font-size: 12px;");
        infoEstado.getChildren().addAll(lblEstadoTit, estadoVisual, lblAlerta);

        Region spacer2 = new Region(); HBox.setHgrow(spacer2, Priority.ALWAYS);

        Button btnToggleEstado = new Button(periodoAbierto ? "🔒 CERRAR PERIODO" : "🔓 HABILITAR REINSCRIPCIONES");
        btnToggleEstado.setStyle("-fx-background-color: " + (periodoAbierto ? "#ef4444" : "#10b981") + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12 25; -fx-background-radius: 8; -fx-cursor: hand;");
        
        btnToggleEstado.setOnAction(e -> {
            try{
                boolean nuevoEstado=!periodoAbierto;
                adminController.toggleReinscripcion(nuevoEstado);
                periodoAbierto=nuevoEstado;
            }catch(RuntimeException ex){
                new com.optem.ui.OptemAlert(javafx.scene.control.Alert.AlertType.ERROR,ex.getMessage()).showAndWait();
                return;
            } 
            
            dotEstado.setFill(Color.web(periodoAbierto ? "#10b981" : "#ef4444"));
            lblEstadoVal.setText(periodoAbierto ? "PERIODO DE REINSCRIPCIÓN: ABIERTO" : "PERIODO DE REINSCRIPCIÓN: CERRADO");
            lblEstadoVal.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: " + (periodoAbierto ? "#10b981" : "#ef4444") + ";");
            lblAlerta.setText(periodoAbierto ? "Los estudiantes ahora pueden acceder al módulo y generar sus referencias." : "El módulo de estudiantes está bloqueado.");
            
            btnToggleEstado.setText(periodoAbierto ? "🔒 CERRAR PERIODO" : "🔓 HABILITAR REINSCRIPCIONES");
            btnToggleEstado.setStyle("-fx-background-color: " + (periodoAbierto ? "#ef4444" : "#10b981") + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12 25; -fx-background-radius: 8; -fx-cursor: hand;");
            NotificacionManager.mostrarNotificacion("Cambio de Estado", periodoAbierto ? "Se ha habilitado la reinscripción." : "Las reinscripciones han concluido.", "INFO");
        });
        controlBox.getChildren().addAll(infoEstado, spacer2, btnToggleEstado);
        AdminPremiumVisual.panel(controlBox);

        VBox creacionGruposBox = new VBox(15);
        creacionGruposBox.setPadding(new Insets(20));
        creacionGruposBox.setStyle("-fx-background-color: " + colorBgPanel + "; -fx-border-color: " + colorBorder + "; -fx-border-radius: 12; -fx-background-radius: 12;");
        AdminPremiumVisual.panel(creacionGruposBox);
        Label lblOfertaTit = new Label("👨‍🏫 Apertura de Grupos (Asignación de Maestros)");
        lblOfertaTit.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: " + colorTextMain + ";");

        GridPane gridOferta = new GridPane();
        gridOferta.setHgap(15); gridOferta.setVgap(15);
        gridOferta.getStyleClass().add("admin-premium-form");

        javafx.scene.control.ComboBox<Integer> cbSemestre=new javafx.scene.control.ComboBox<>();
        cbSemestre.getItems().addAll(adminController.semestresDisponibles());
        cbSemestre.setPromptText("Semestre");
        javafx.scene.control.ComboBox<String> cbMateria=new javafx.scene.control.ComboBox<>();
        cbMateria.setPromptText("Selecciona materia");
        java.util.Map<String,String> materias=new java.util.LinkedHashMap<>();
        cbSemestre.valueProperty().addListener((o,a,n)->{
            cbMateria.getItems().clear();materias.clear();
            if(n!=null){materias.putAll(adminController.materiasPorSemestre(n));
                for(var ent:materias.entrySet())cbMateria.getItems().add(ent.getKey()+" - "+ent.getValue());}
        });
        TextField txtMateria = new TextField(); txtMateria.setPromptText("Ej: Arquitectura de Software");
        txtMateria.setStyle("-fx-background-color: " + colorBgBase + "; -fx-text-fill: " + colorTextMain + "; -fx-padding: 8; -fx-border-color: " + colorBorder + "; -fx-border-radius: 6;");
        javafx.scene.control.ComboBox<String> cbMaestro=new javafx.scene.control.ComboBox<>();
        cbMaestro.setPromptText("Docente");
        javafx.scene.control.ComboBox<String> cbHorario=new javafx.scene.control.ComboBox<>();
        cbHorario.setPromptText("Selecciona horario");
        cbHorario.setPrefWidth(205);
        AdminPremiumVisual.field(cbHorario);
        // Cambiar la materia actualiza profesores y los cinco turnos.
        cbMateria.valueProperty().addListener((o,a,n)->{
            cbMaestro.getItems().clear(); cbMaestro.setValue(null);
            cbHorario.getItems().clear(); cbHorario.setValue(null);
            if(n!=null){
                String clave=n.split(" - ",2)[0];
                for(var ent:adminController.docentesPorMateria(clave).entrySet())
                    cbMaestro.getItems().add(ent.getValue()+" · "+ent.getKey());
                cbHorario.getItems().addAll(adminController.horariosDisponibles(clave));
            }
        });
        TextField txtGrupo = new TextField(); txtGrupo.setPromptText("Generación automática");
        txtGrupo.setEditable(false);
        cbMateria.valueProperty().addListener((o,a,n)->{
            if(n!=null)txtGrupo.setText(adminController.generarClaveGrupo(n));
        });
        txtGrupo.setStyle("-fx-background-color: " + colorBgBase + "; -fx-text-fill: " + colorTextMain + "; -fx-padding: 8; -fx-border-color: " + colorBorder + "; -fx-border-radius: 6;");

        gridOferta.add(crearLabelSimple("Semestre:"), 0, 0); gridOferta.add(cbSemestre, 0, 1);
        gridOferta.add(crearLabelSimple("Materia a ofertar:"), 1, 0); gridOferta.add(cbMateria, 1, 1);
        gridOferta.add(crearLabelSimple("Maestro Asignado:"), 2, 0); gridOferta.add(cbMaestro, 2, 1);
        gridOferta.add(crearLabelSimple("Horario Asignado:"), 3, 0); gridOferta.add(cbHorario, 3, 1);
        gridOferta.add(crearLabelSimple("Clave de Grupo:"), 4, 0); gridOferta.add(txtGrupo, 4, 1);

        Button btnCrearGrupo = new Button("Registrar Grupo en Sistema");
        btnCrearGrupo.setStyle("-fx-background-color: " + colorFuerte + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 15; -fx-background-radius: 6; -fx-cursor: hand;");
        AdminPremiumVisual.primary(btnCrearGrupo);
        btnCrearGrupo.setOnAction(e -> {
            if(cbMateria.getValue()==null || cbMaestro.getValue()==null || cbHorario.getValue()==null){
                NotificacionManager.mostrarNotificacion("Faltan datos","Selecciona semestre, materia, docente y horario.","ERROR");return;
            }
            String clave=cbMateria.getValue().split(" - ",2)[0];
            String docente=cbMaestro.getValue().substring(cbMaestro.getValue().lastIndexOf(" · ")+3);
            try{
                if(!adminController.crearGrupoOficial(clave,docente,txtGrupo.getText(),cbHorario.getValue()))
                    throw new IllegalStateException("MySQL no confirmó el registro");
                new com.optem.ui.OptemAlert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "se aperturo el grupo con exito").showAndWait();
                cbMateria.setValue(null);cbMaestro.setValue(null);cbHorario.setValue(null);txtGrupo.clear();
            }catch(Exception ex){new com.optem.ui.OptemAlert(javafx.scene.control.Alert.AlertType.ERROR,
                "no se pudo aperturar grupo: "+ex.getMessage()).showAndWait();}
        });

        creacionGruposBox.getChildren().addAll(lblOfertaTit, gridOferta, btnCrearGrupo);

        VBox enviosBox = new VBox(15);
        enviosBox.setPadding(new Insets(20));
        enviosBox.setStyle("-fx-background-color: " + colorBgPanel + "; -fx-border-color: " + colorBorder + "; -fx-border-radius: 12; -fx-background-radius: 12;");
        AdminPremiumVisual.panel(enviosBox);
        Label lblEnviosTit = new Label("📥 Solicitudes de Estudiantes");
        lblEnviosTit.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: " + colorTextMain + ";");

        listaAlumnosRegistrados = new VBox(10);
        cargarListaSolicitudes();

        ScrollPane scrollSolicitudes = new ScrollPane(listaAlumnosRegistrados);
        scrollSolicitudes.setPrefHeight(250);
        scrollSolicitudes.setFitToWidth(true);
        scrollSolicitudes.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        enviosBox.getChildren().addAll(lblEnviosTit, scrollSolicitudes);

        layoutMain.getChildren().addAll(header, controlBox, creacionGruposBox, enviosBox);
        
        ScrollPane scroll = new ScrollPane(layoutMain);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        
        VBox layoutFinal = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return layoutFinal;
    }

    /** Una tarjeta por estudiante y periodo, mostrando TODAS las materias solicitadas. */
    private void cargarListaSolicitudes() {
        listaAlumnosRegistrados.getChildren().clear();
        List<Map<String,String>> solicitudes = solicitudDao.obtenerSolicitudesAgrupadas();
        if(solicitudes.isEmpty()){
            Label vacio=new Label("Aún no hay solicitudes registradas.");
            vacio.setStyle("-fx-text-fill: "+colorTextMuted+"; -fx-font-style: italic;");
            listaAlumnosRegistrados.getChildren().add(vacio);
            return;
        }
        for(Map<String,String> sol: solicitudes){
            HBox fila=new HBox(15);fila.setAlignment(Pos.CENTER_LEFT);fila.setPadding(new Insets(15));
            fila.setStyle("-fx-background-color: "+colorBgBase+"; -fx-background-radius: 8; -fx-border-color: "+colorBorder+"; -fx-border-radius: 8;");
            AdminPremiumVisual.row(fila);
            VBox info=new VBox(5);HBox.setHgrow(info,Priority.ALWAYS);
            Label nombre=new Label("👤 "+sol.get("nombre"));
            nombre.setStyle("-fx-font-weight: bold; -fx-text-fill: "+colorTextMain+";");
            Label periodo=new Label(sol.get("periodo_nombre")+" · "+sol.get("cantidad")+" materia(s)");
            periodo.setStyle("-fx-text-fill: "+colorTextMuted+";");
            Label lista=new Label(sol.get("materias"));lista.setWrapText(true);
            lista.setStyle("-fx-text-fill: "+colorTextMain+";");
            boolean enviado="HOJA ENVIADA".equalsIgnoreCase(sol.get("estado"));
            Label estado=new Label(enviado?"✅ Hoja de Pago Enviada":"⏳ Pendiente de Generar Pago");
            estado.setStyle("-fx-font-size: 11px; -fx-text-fill: "+(enviado?"#10b981":"#f59e0b")+"; -fx-font-weight: bold;");
            info.getChildren().addAll(nombre,periodo,lista,estado);
            VBox acciones=new VBox(8);acciones.setAlignment(Pos.CENTER_RIGHT);
            if(!enviado){
                Button btnSubir=new Button("📎 Subir Hoja de Pago (PDF)");
                btnSubir.setStyle("-fx-background-color: "+colorFuerte+"; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 6; -fx-cursor: hand;");
                AdminPremiumVisual.primary(btnSubir);
                btnSubir.setOnAction(e->{
                    FileChooser fc=new FileChooser();fc.setTitle("Hoja de Pago: "+sol.get("nombre"));
                    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documentos PDF","*.pdf"));
                    File archivo=fc.showOpenDialog(fila.getScene().getWindow());
                    if(archivo==null)return;
                    try {
                        solicitudDao.subirHojaDePago(sol.get("correo"),Integer.parseInt(sol.get("id_periodo")),archivo.getAbsolutePath());
                        NotificacionManager.mostrarNotificacion("Documento Enviado","Hoja guardada en MySQL.","INFO");
                        cargarListaSolicitudes();
                    }catch(Exception ex){new com.optem.ui.OptemAlert(javafx.scene.control.Alert.AlertType.ERROR,ex.getMessage()).showAndWait();}
                });
                acciones.getChildren().add(btnSubir);
            }
            Button btnEliminar=new Button("🗑 Eliminar solicitud");
            btnEliminar.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");
            btnEliminar.setOnAction(e->{
                var confirmacion=new com.optem.ui.OptemAlert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                    "¿Eliminar las "+sol.get("cantidad")+" solicitudes de "+sol.get("nombre")+" del periodo "+sol.get("periodo_nombre")+"? Las inscripciones ya confirmadas no se modificarán.",
                    javafx.scene.control.ButtonType.YES,javafx.scene.control.ButtonType.NO);
                if(confirmacion.showAndWait().orElse(javafx.scene.control.ButtonType.NO)!=javafx.scene.control.ButtonType.YES)return;
                try {solicitudDao.eliminarSolicitudesAlumno(sol.get("correo"),Integer.parseInt(sol.get("id_periodo")));cargarListaSolicitudes();}
                catch(Exception ex){new com.optem.ui.OptemAlert(javafx.scene.control.Alert.AlertType.ERROR,ex.getMessage()).showAndWait();}
            });
            acciones.getChildren().add(btnEliminar);
            fila.getChildren().addAll(info,acciones);
            listaAlumnosRegistrados.getChildren().add(fila);
        }
    }

    private void leerPreferencias() {
        Preferences prefs = Preferences.userNodeForPackage(DashboardView.class);
        String correoActual = prefs.get("LAST_ACTIVE_ACCOUNT", "");
        
        String fontPref = prefs.get("PREF_FONT_SIZE_" + correoActual, "Normal");
        fontSizeActual = fontPref.equals("Pequeño") ? "12px" : fontPref.equals("Grande") ? "17px" : "14px";

        boolean isDark = prefs.getBoolean("PREF_DARK_MODE_" + correoActual, false);
        colorFuerte = prefs.get("PREF_ACCENT_COLOR_" + correoActual, "#3D5A4D");
        colorBgBase = isDark ? "#121212" : "#f1f5f9"; colorBgPanel = isDark ? "#1e1e1e" : "#ffffff";
        colorTextMain = isDark ? "#f8f9fa" : "#1e293b"; colorTextMuted = isDark ? "#94a3b8" : "#64748b";
        colorBorder = isDark ? "#333333" : "#e2e8f0";
    }

    private Label crearLabelSimple(String texto) {
        Label lbl = new Label(texto);
        lbl.setStyle("-fx-text-fill: " + colorTextMuted + "; -fx-font-weight: bold;");
        return lbl;
    }
}