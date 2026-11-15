package com.optem.ui;

import java.io.File;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

import com.optem.controllers.AjustesController;
import com.optem.controllers.DashboardController;
import com.optem.database.MockDB;

import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

public class DashboardView {

    private final DashboardController controller = new DashboardController();

    private boolean isSidebarExpanded = true;
    private static final double EXPANDED_WIDTH = 260;
    private static final double COLLAPSED_WIDTH = 75;
    
    private ProgressBar xpBar;
    private Label xpLabel; 
    private Circle userPic;
    private Label lblName;
    private String correoActualGlobal;
    private Button btnToggle; 

    private Button btnInicio, btnAgenda, btnNotas, btnTareas, btnReuniones, btnActividades, btnGrupos, btnPomodoro, btnLogros, btnReinscripcion, btnAjustes;
    private Button btnAdStats, btnAdPerfiles, btnAdDocs, btnAdIA, btnAdReinscripcion, btnAdConfig;

    private BorderPane root;
    private Button menuSeleccionado;
    private ScrollPane scrollAlumno;
    private StackPane navigationOverlay;
    private final LinkedHashMap<String, CachedPage> viewCache = new LinkedHashMap<>();
    private static final long PAGE_CACHE_NS = java.util.concurrent.TimeUnit.SECONDS.toNanos(18);
    private static final int MAX_CACHED_PAGES = 6;
    private record CachedPage(Node content, long createdNs) {}

    
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isMaximized = true;

    private final Set<KeyCode> teclasActivas = new HashSet<>();
    private AnimationTimer ratonTimer;

    public void show(Stage stage, String correoUsuario, String fileKey) {
        
        // Los caches no se comparten entre cuentas ni entre reconstrucciones del tema.
        viewCache.clear();
        navigationOverlay = null;
        controller.inicializarUsuario(correoUsuario);
        this.correoActualGlobal = correoUsuario;

        String nombreEstudianteActivo = controller.getNombreEstudiante();
        String colorFuerte = controller.getColorFuerte();
        boolean animacionesActivas = controller.isAnimacionesActivas();
        boolean wasShowing = stage.isShowing();
        
        StackPane mainRoot = new StackPane();
        root = new BorderPane();
        navigationOverlay = crearCapaCarga();
        
        if (!wasShowing) { stage.initStyle(StageStyle.UNDECORATED); }
        
        HBox titleBar = new HBox(15); 
        titleBar.setAlignment(Pos.CENTER_RIGHT); 
        titleBar.setPadding(new Insets(0, 15, 0, 0));
        titleBar.setStyle("-fx-background-color: -color-bg-panel; -fx-border-color: -color-border; -fx-border-width: 0 0 1 0;");
        titleBar.setMinHeight(40);
        titleBar.setPrefHeight(40);
        
        Button btnMaxim = new Button(isMaximized ? "🗗" : "🗖");

        titleBar.setOnMousePressed(event -> { xOffset = event.getSceneX(); yOffset = event.getSceneY(); });
        titleBar.setOnMouseDragged(event -> { 
            if (isMaximized) {
                isMaximized = false;
                btnMaxim.setText("🗖");
                Screen screen = Screen.getPrimary();
                Rectangle2D bounds = screen.getVisualBounds();
                double w = bounds.getWidth() * 0.85;
                double h = bounds.getHeight() * 0.85;
                stage.setWidth(w);
                stage.setHeight(h);
                xOffset = w / 2;
                yOffset = 20; 
            }
            stage.setX(event.getScreenX() - xOffset); 
            stage.setY(event.getScreenY() - yOffset); 
        });

        String btnControlStyle = "-fx-background-color: transparent; -fx-text-fill: -color-text-muted; -fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 15; -fx-effect: null;";
        String btnControlHover = "-fx-background-color: rgba(128,128,128,0.2); -fx-text-fill: -color-text-main; -fx-background-radius: 5;";
        
        // Estado real de MySQL, comprobado en segundo plano.
        com.optem.logic.OfflineSyncService.get().start();
        Label estadoConexion = new Label("● Comprobando conexión...");
        estadoConexion.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
        Tooltip estadoTooltip = new Tooltip("Los cambios de Notas se guardan localmente cuando no hay conexión.");
        estadoConexion.setTooltip(estadoTooltip);
        Timeline refrescarEstado = new Timeline(new KeyFrame(Duration.seconds(2), ev -> {
            var offline = com.optem.logic.OfflineSyncService.get();
            var st = offline.status();
            int n = offline.pending();
            estadoConexion.setText(st == com.optem.logic.OfflineSyncService.Status.CONECTADO
                ? "● Conectado" + (n>0 ? " | " + n + " pendiente(s)" : "")
                : st == com.optem.logic.OfflineSyncService.Status.SIN_CONEXION
                    ? "● Sin conexión" + (n>0 ? " | " + n + " guardado(s)" : "")
                    : "● Comprobando conexión...");
            estadoConexion.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: "
                +(st==com.optem.logic.OfflineSyncService.Status.CONECTADO ? "#20A36A" :
                  st==com.optem.logic.OfflineSyncService.Status.SIN_CONEXION ? "#E47B72" : "#94A3B8")+";");
        }));
        refrescarEstado.setCycleCount(Animation.INDEFINITE);
        refrescarEstado.play();
        stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_HIDDEN, ev -> refrescarEstado.stop());
        titleBar.getChildren().add(0, estadoConexion);

        Button btnMinimizar = new Button("—"); btnMinimizar.setStyle(btnControlStyle);
        btnMinimizar.setOnMouseEntered(e -> btnMinimizar.setStyle(btnControlStyle + btnControlHover)); btnMinimizar.setOnMouseExited(e -> btnMinimizar.setStyle(btnControlStyle));
        btnMinimizar.setOnAction(e -> stage.setIconified(true));

        btnMaxim.setStyle(btnControlStyle);
        btnMaxim.setOnMouseEntered(e -> btnMaxim.setStyle(btnControlStyle + btnControlHover)); btnMaxim.setOnMouseExited(e -> btnMaxim.setStyle(btnControlStyle));
        btnMaxim.setOnAction(e -> {
            Screen screen = Screen.getPrimary(); Rectangle2D bounds = screen.getVisualBounds();
            if (isMaximized) {
                // Si la pantalla es muy pequeña, forzamos un mínimo
                double w = Math.max(1024, bounds.getWidth() * 0.85); 
                double h = Math.max(700, bounds.getHeight() * 0.85);
                stage.setWidth(w); stage.setHeight(h);
                stage.setX(bounds.getMinX() + (bounds.getWidth() - w) / 2); stage.setY(bounds.getMinY() + (bounds.getHeight() - h) / 2);
                isMaximized = false; btnMaxim.setText("🗖"); 
            } else {
                stage.setX(bounds.getMinX()); stage.setY(bounds.getMinY()); 
                stage.setWidth(bounds.getWidth()); stage.setHeight(bounds.getHeight());
                isMaximized = true; btnMaxim.setText("🗗");
            }
        });

        Button btnCerrar = new Button("✕"); btnCerrar.setStyle(btnControlStyle);
        btnCerrar.setOnMouseEntered(e -> btnCerrar.setStyle(btnControlStyle + "-fx-background-color: #D32F2F; -fx-text-fill: white; -fx-background-radius: 5;")); btnCerrar.setOnMouseExited(e -> btnCerrar.setStyle(btnControlStyle));
        btnCerrar.setOnAction(e -> { Platform.exit(); System.exit(0); });
        
        Button btnRefrescar=new Button("↻");
        btnRefrescar.setTooltip(new Tooltip("Actualizar esta sección sin cambiar de página"));
        btnRefrescar.setStyle(btnControlStyle);
        btnRefrescar.setOnMouseEntered(e->btnRefrescar.setStyle(btnControlStyle+btnControlHover));
        btnRefrescar.setOnMouseExited(e->btnRefrescar.setStyle(btnControlStyle));
        btnRefrescar.setOnAction(e->{
            viewCache.clear(); // fuerza una consulta nueva a MySQL para la sección solicitada
            try{
                if(menuSeleccionado==btnInicio && "ESTUDIANTE".equals(controller.getRolUsuario())){
                    construirInicioEstudiante(controller.isAnimacionesActivas(),controller.getColorFuerte(),stage);
                    root.setCenter(scrollAlumno);
                }else if(menuSeleccionado!=null)menuSeleccionado.fire();
            }catch(RuntimeException ex){
                new OptemAlert(Alert.AlertType.ERROR,"No se pudo actualizar esta pantalla: "+ex.getMessage()).showAndWait();
            }
        });
        titleBar.getChildren().addAll(btnRefrescar,btnMinimizar, btnMaxim, btnCerrar);

        VBox sidebar = new VBox(10);
        sidebar.setPrefWidth(EXPANDED_WIDTH); sidebar.setMinWidth(COLLAPSED_WIDTH); sidebar.setMaxWidth(EXPANDED_WIDTH);
        sidebar.setPadding(new Insets(15, 20, 15, 20));
        sidebar.setStyle("-fx-background-color: linear-gradient(to bottom right, -color-bg-panel, derive(-color-bg-panel,-3%)); -fx-border-color: rgba(38,112,89,.17); -fx-border-width: 0 1 0 0; -fx-effect: dropshadow(three-pass-box, rgba(23,64,70,.16), 15, 0, 3, 0);");
        sidebar.getStyleClass().add("optem-sidebar");

        btnToggle = new Button("≡"); btnToggle.setStyle("-fx-background-color: transparent; -fx-font-size: 20px; -fx-cursor: hand; -fx-text-fill: -color-text-main; -fx-effect: null;"); btnToggle.setAlignment(Pos.CENTER_LEFT);

        VBox userBox = new VBox(10); userBox.setAlignment(Pos.CENTER); userBox.setPadding(new Insets(10, 0, 20, 0));
        userPic = new Circle(35); userPic.setFill(Color.web(colorFuerte)); userPic.setStyle("-fx-cursor: hand;");
        
        String savedPhotoPath = controller.getFotoPerfil();
        if (!savedPhotoPath.isEmpty()) {
            File photoFile = new File(savedPhotoPath);
            if (photoFile.exists()) { try { Image rawImg = new Image(photoFile.toURI().toString()); userPic.setFill(new ImagePattern(getSquareImage(rawImg))); } catch (Exception ex) {} }
        }

        lblName = new Label(nombreEstudianteActivo.split(" ")[0]); 
        lblName.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: -color-text-main; -fx-effect: null;");
        userBox.getChildren().addAll(userPic, lblName);

        ContextMenu profileMenu = new ContextMenu(); profileMenu.getStyleClass().add("menu-dominio-oro"); 
        MenuItem mnuCambiarFoto = new MenuItem("📷  Cambiar foto de perfil"); MenuItem mnuCambiarCuenta = new MenuItem("🔄  Cambiar de usuario"); MenuItem mnuCerrarSesion = new MenuItem("🚪  Cerrar sesión");
        profileMenu.getItems().addAll(mnuCambiarFoto, mnuCambiarCuenta, mnuCerrarSesion);
        // Un ContextMenu utiliza un PopupWindow propio; heredar solo el CSS de la escena no basta.
        if (StudentPremiumTheme.isDarkMode()) {
            profileMenu.setStyle("-fx-background-color: #202735; -fx-border-color: #48556A; -fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 5;");
            for (MenuItem item : profileMenu.getItems())
                item.setStyle("-fx-background-color: #202735; -fx-text-fill: #F1F5FF;");
            profileMenu.getStyleClass().add("optem-dark-profile-menu");
        }

        mnuCambiarFoto.setOnAction(e -> { if (btnAjustes != null) btnAjustes.fire(); else if (btnAdConfig != null) btnAdConfig.fire(); });
        mnuCambiarCuenta.setOnAction(e -> mostrarVentanaMultiCuentas(stage, correoUsuario, fileKey));
        mnuCerrarSesion.setOnAction(e -> { try { controller.cerrarSesion(); new LoginView().show(stage); } catch (Exception ex) {} });
        userPic.setOnMouseClicked(evt -> {
            profileMenu.show(userPic, evt.getScreenX(), evt.getScreenY());
            if (profileMenu.getScene() != null) StudentPremiumTheme.install(profileMenu.getScene());
        });

        VBox menuButtonsBox = new VBox(8);
        Runnable themeUpdater = () -> show(stage, correoUsuario, fileKey); 
        String rol = controller.getRolUsuario();
        if ("ESTUDIANTE".equals(rol) || "MAESTRO".equals(rol) || "ADMINISTRADOR".equals(rol)) {
            sidebar.setStyle("-fx-background-color: linear-gradient(to bottom right, -color-bg-panel, derive(-color-bg-panel,-3%));"+
                    " -fx-border-color: derive(-color-accent,65%); -fx-border-width: 0 1 0 0;"+
                    " -fx-effect: dropshadow(three-pass-box, rgba(23,64,70,.16),15,0,3,0);");
        }

        if (rol.equals("ADMINISTRADOR")) {
            btnAdStats = createMenuButton("📊", "Estadísticas"); 
            btnAdStats.setOnAction(e -> { mostrarPagina(() -> new AdminEstadisticasView().getView(), btnAdStats); });
            
            btnAdPerfiles = createMenuButton("👤", "Gestión de Perfiles"); 
            btnAdPerfiles.setOnAction(e -> { mostrarPagina(() -> new AdminPerfilesView().getView(), btnAdPerfiles); });
            
            btnAdDocs = createMenuButton("📁", "Docs y Trayectoria"); 
            btnAdDocs.setOnAction(e -> { mostrarPagina(() -> new AdminDocsView().getView(), btnAdDocs); });
            
            btnAdIA = createMenuButton("🤖", "Inteligencia Artificial"); 
            btnAdIA.setOnAction(e -> { mostrarPagina(() -> new AdminIAView().getView(), btnAdIA); });

            btnAdReinscripcion = createMenuButton("📑", "Reinscripción"); 
            btnAdReinscripcion.setOnAction(e -> { mostrarPagina(() -> new AdminReinscripcionView().getView(), btnAdReinscripcion); });
            
            btnAdConfig = createMenuButton("⚙", "Configuración Sistema"); 
            btnAdConfig.setOnAction(e -> { mostrarPagina(() -> new AjustesView(themeUpdater, correoUsuario).getView(), btnAdConfig); });
            
            menuButtonsBox.getChildren().addAll(btnAdStats, btnAdPerfiles, btnAdDocs, btnAdIA, btnAdReinscripcion, btnAdConfig);
            btnAdStats.fire();
        } 
        else if (rol.equals("MAESTRO")) {
            btnInicio = createMenuButton("🏠", "Inicio"); btnInicio.setOnAction(e -> { mostrarPagina(() -> new InicioMaestroView().getView(), btnInicio); });
            btnAgenda = createMenuButton("📅", "Mi Agenda"); btnAgenda.setOnAction(e -> { try { mostrarPagina(() -> new AgendaMaestroView().getView(), btnAgenda); }
                catch(Exception ex) {new OptemAlert(javafx.scene.control.Alert.AlertType.ERROR,
                    "No se pudo abrir la agenda docente: "+ex.getMessage()).showAndWait();} });
            btnNotas = createMenuButton("📝", "Cuaderno Docente"); 
            btnNotas.setOnAction(e -> { mostrarPagina(() -> new NotasMaestroView().getView(), btnNotas); });
            
            btnActividades = createMenuButton("✅", "Mis Actividades"); 
            btnActividades.setOnAction(e -> { mostrarPagina(() -> new ActividadesMaestroView().getView(), btnActividades); });
            
            btnTareas = createMenuButton("📤", "Asignar Tareas"); btnTareas.setId("optem-menu-tareas"); 
            btnTareas.setOnAction(e -> { mostrarPagina(() -> new TareasMaestroView().getView(), btnTareas); });
            
            btnReuniones = createMenuButton("🎥", "Reuniones"); btnReuniones.setOnAction(e -> { mostrarPagina(() -> new ReunionesMaestroView().getView(), btnReuniones); });
            btnGrupos = createMenuButton("👥", "Mis Grupos"); btnGrupos.setOnAction(e -> { mostrarPagina(() -> new GruposMaestroView(), btnGrupos); });
            btnPomodoro = createMenuButton("⏱", "Pomodoro"); btnPomodoro.setOnAction(e -> { mostrarPagina(() -> new PomodoroMaestroView().getView(), btnPomodoro); });
            btnLogros = createMenuButton("🏆", "Logros"); btnLogros.setOnAction(e -> { mostrarPagina(() -> new LogrosMaestroView().getView(), btnLogros); });
            btnAjustes = createMenuButton("⚙", "Ajustes"); btnAjustes.setOnAction(e -> { mostrarPagina(() -> new AjustesView(themeUpdater, correoUsuario).getView(), btnAjustes); });

            menuButtonsBox.getChildren().addAll(btnInicio, btnAgenda, btnNotas, btnActividades, btnTareas, btnReuniones, btnGrupos, btnPomodoro, btnLogros, btnAjustes);
            btnInicio.fire();
        }
        else { 
            construirInicioEstudiante(animacionesActivas, colorFuerte, stage);
            
            btnInicio = createMenuButton("🏠", "Inicio"); btnInicio.setOnAction(e -> { mostrarPagina(() -> scrollAlumno, btnInicio); });
            btnAgenda = createMenuButton("📅", "Mi Agenda"); btnAgenda.setOnAction(e -> { try { mostrarPagina(() -> new AgendaView().getView(), btnAgenda); }
                catch(Exception ex) {new OptemAlert(javafx.scene.control.Alert.AlertType.ERROR,
                    "No se pudo abrir la agenda: "+ex.getMessage()).showAndWait();} });
            btnNotas = createMenuButton("📝", "Notas"); btnNotas.setOnAction(e -> { mostrarPagina(() -> new NotasView().getView(), btnNotas); });
            btnTareas = createMenuButton("✅", "Tareas"); btnTareas.setOnAction(e -> { mostrarPagina(() -> new TareasView().getView(), btnTareas); });
            btnReuniones = createMenuButton("🎥", "Reuniones"); btnReuniones.setOnAction(e -> { mostrarPagina(() -> new ReunionesView().getView(), btnReuniones); });
            btnActividades = createMenuButton("🎯", "Actividades"); btnActividades.setOnAction(e -> { mostrarPagina(() -> new ActividadesView(), btnActividades); });
            btnPomodoro = createMenuButton("⏱", "Pomodoro"); btnPomodoro.setOnAction(e -> { mostrarPagina(() -> new PomodoroView(), btnPomodoro); });
            btnLogros = createMenuButton("🏆", "Logros"); btnLogros.setOnAction(e -> { mostrarPagina(() -> new LogrosView(), btnLogros); });
            btnReinscripcion = createMenuButton("📑", "Reinscripción"); btnReinscripcion.setOnAction(e -> { mostrarPagina(() -> new ReinscripcionView(), btnReinscripcion); });
            btnAjustes = createMenuButton("⚙", "Ajustes"); btnAjustes.setOnAction(e -> { mostrarPagina(() -> new AjustesView(themeUpdater, correoUsuario).getView(), btnAjustes); });

            menuButtonsBox.getChildren().addAll(btnInicio, btnAgenda, btnNotas, btnTareas, btnReuniones, btnActividades, btnPomodoro, btnLogros, btnReinscripcion, btnAjustes);
            btnInicio.fire(); 
        }

        sidebar.getChildren().addAll(btnToggle, userBox, menuButtonsBox);

        btnToggle.setOnAction(e -> {
            isSidebarExpanded = !isSidebarExpanded; double targetWidth = isSidebarExpanded ? EXPANDED_WIDTH : COLLAPSED_WIDTH;
            sidebar.setCache(true); sidebar.setCacheHint(CacheHint.SPEED);
            if (root.getCenter() != null) { root.getCenter().setCache(true); root.getCenter().setCacheHint(CacheHint.SPEED); }
            Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(sidebar.prefWidthProperty(), sidebar.getWidth()), new KeyValue(userPic.radiusProperty(), userPic.getRadius())),
                new KeyFrame(Duration.millis(200), new KeyValue(sidebar.prefWidthProperty(), targetWidth), new KeyValue(userPic.radiusProperty(), isSidebarExpanded ? 35 : 22))
            );
            timeline.setOnFinished(evt -> {
                if (!isSidebarExpanded) { sidebar.setPadding(new Insets(15, 10, 15, 10)); userBox.getChildren().remove(lblName); toggleButtonTexts(false, btnInicio, btnAgenda, btnNotas, btnTareas, btnReuniones, btnActividades, btnGrupos, btnPomodoro, btnLogros, btnReinscripcion, btnAjustes, btnAdStats, btnAdPerfiles, btnAdDocs, btnAdIA, btnAdReinscripcion, btnAdConfig); } 
                else { sidebar.setPadding(new Insets(15, 20, 15, 20)); if (!userBox.getChildren().contains(lblName)) userBox.getChildren().add(lblName); toggleButtonTexts(true, btnInicio, btnAgenda, btnNotas, btnTareas, btnReuniones, btnActividades, btnGrupos, btnPomodoro, btnLogros, btnReinscripcion, btnAjustes, btnAdStats, btnAdPerfiles, btnAdDocs, btnAdIA, btnAdReinscripcion, btnAdConfig); }
                sidebar.setCache(false); if (root.getCenter() != null) root.getCenter().setCache(false);
            }); timeline.play();
        });

        root.setLeft(sidebar); 

        VBox appLayout = new VBox(0, titleBar, root);
        VBox.setVgrow(root, Priority.ALWAYS);
        VBox.setVgrow(titleBar, Priority.NEVER);

        VBox chatContainer = new VBox();
        chatContainer.setMaxSize(360, 520);
        chatContainer.setVisible(false);
        chatContainer.setManaged(false);

        ChatBotView widgetIA = new ChatBotView();
        chatContainer.getChildren().add(widgetIA.getView(() -> {
            FadeTransition ftOut = new FadeTransition(Duration.millis(200), chatContainer);
            ftOut.setToValue(0);
            ftOut.setOnFinished(ev -> { chatContainer.setVisible(false); chatContainer.setManaged(false); });
            ftOut.play();
        }));

        Button btnBurbujaIA = new Button("🤖");
        btnBurbujaIA.setStyle("-fx-background-color: linear-gradient(to bottom right, derive(" + colorFuerte + ", 22%), " + colorFuerte + ", derive(" + colorFuerte + ", -41%)); -fx-text-fill: white; -fx-font-size: 28px; -fx-background-radius: 50; -fx-border-color: #b9e6c2; -fx-border-radius: 50; -fx-border-width: 1.5; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(2,55,52,.37), 15, 0, 0, 7);");
        btnBurbujaIA.setPrefSize(65, 65);
        btnBurbujaIA.setMinSize(65, 65);
        btnBurbujaIA.setMaxSize(65, 65);

        btnBurbujaIA.setOnAction(e -> {
            boolean estaVisible = chatContainer.isVisible();
            if (!estaVisible) {
                chatContainer.setVisible(true);
                chatContainer.setManaged(true);
                FadeTransition ftIn = new FadeTransition(Duration.millis(200), chatContainer);
                ftIn.setFromValue(0); ftIn.setToValue(1); ftIn.play();
            } else {
                FadeTransition ftOut = new FadeTransition(Duration.millis(200), chatContainer);
                ftOut.setToValue(0);
                ftOut.setOnFinished(ev -> { chatContainer.setVisible(false); chatContainer.setManaged(false); });
                ftOut.play();
            }
        });

        StackPane.setAlignment(btnBurbujaIA, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(btnBurbujaIA, new Insets(0, 30, 30, 0));
        StackPane.setAlignment(chatContainer, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(chatContainer, new Insets(0, 30, 110, 0)); 

        mainRoot.getChildren().addAll(appLayout, navigationOverlay, chatContainer, btnBurbujaIA);
        mainRoot.setStyle("-fx-background-color: -color-bg-base;");

        // --- FIX DEFINITIVO RESPONSIVIDAD ---
        Screen screen = Screen.getPrimary();
        Rectangle2D bounds = screen.getVisualBounds();
        
        Scene scene = new Scene(mainRoot, bounds.getWidth(), bounds.getHeight()); OptemLook.install(scene);
        aplicarTemaGlobal(scene, correoUsuario);
        configurarNavegacionPorTecladoGlobal(scene, stage);
        stage.setScene(scene); 
        
        Platform.runLater(() -> { 
            if (!wasShowing) {
                stage.setX(bounds.getMinX()); 
                stage.setY(bounds.getMinY());
                stage.setWidth(bounds.getWidth()); 
                stage.setHeight(bounds.getHeight());
                
                isMaximized = true; 
                btnMaxim.setText("🗗"); 
                
                stage.show(); 
                stage.toFront(); 
            } else {
                stage.show(); 
            }
        });
    }
    
    private void mostrarPagina(Supplier<Node> construir, Button boton) {
        if (root == null || boton == null) return;
        actualizarSeleccionMenu(boton);
        final String clave = boton.getId();
        if("Inicio".equals(clave) && xpLabel!=null){
            javafx.concurrent.Task<Integer> actualizarXP=new javafx.concurrent.Task<>(){
                @Override protected Integer call(){return com.optem.logic.XPService.xp(correoActualGlobal);}
            };
            actualizarXP.setOnSucceeded(ev->{
                int actual=actualizarXP.getValue();
                int nivel=(actual/200)+1;
                if(xpLabel!=null)xpLabel.setText("Nivel "+nivel+" | "+actual+" XP de "+(nivel*200)+" XP");
                if(xpBar!=null)xpBar.setProgress((actual%200)/200.0);
            });
            Thread hilo=new Thread(actualizarXP,"optem-actualizar-xp");hilo.setDaemon(true);hilo.start();
        }
        if ("Logros".equals(clave) || "Reinscripción".equals(clave)) viewCache.remove(clave);
        CachedPage cached = viewCache.get(clave);
        long now = System.nanoTime();
        if (cached != null && now - cached.createdNs() <= PAGE_CACHE_NS) {
            mostrarNodo(cached.content(), false);
            return;
        }
        if (root.getScene() == null) {
            construirYMostrar(clave, construir);
            return;
        }
        if (navigationOverlay != null) {
            navigationOverlay.setVisible(true);
            navigationOverlay.setManaged(true);
            navigationOverlay.toFront();
        }
        PauseTransition inicio = new PauseTransition(Duration.millis(65));
        inicio.setOnFinished(event -> construirYMostrar(clave, construir));
        inicio.play();
    }

    private void construirYMostrar(String clave, Supplier<Node> construir) {
        try {
            Node pagina = construir.get();
            if (pagina == null) {
                throw new IllegalStateException(
                    "La pantalla no devolvió contenido"
                );
            }
            OptemLook.decorate(pagina);
            viewCache.put(
                clave,
                new CachedPage(pagina, System.nanoTime())
            );
            while (viewCache.size() > MAX_CACHED_PAGES) {
                viewCache.remove(
                    viewCache.keySet().iterator().next()
                );
            }
            mostrarNodo(pagina, true);
        } catch (Exception ex) {
            String mensaje = ex.getMessage() == null
                ? ex.getClass().getSimpleName()
                : ex.getMessage();
            System.err.println(
                "OPTEM - Error al abrir sección: " + mensaje
            );
            // Mostrar aviso sin bloquear la animación de JavaFX
            javafx.application.Platform.runLater(() -> {
                new OptemAlert(
                    Alert.AlertType.ERROR,
                    "No se pudo abrir esta sección: " + mensaje
                ).show();
            });
        } finally {
            if (navigationOverlay != null) {
                navigationOverlay.setVisible(false);
                navigationOverlay.setManaged(false);
            }
        }
    }

    private void mostrarNodo(Node pagina, boolean animar) {
        Node contenido = ("ESTUDIANTE".equals(controller.getRolUsuario()) || "MAESTRO".equals(controller.getRolUsuario()) || "ADMINISTRADOR".equals(controller.getRolUsuario()))
                ? StudentPremiumTheme.wrap(pagina) : pagina;
        if (root.getCenter() != contenido) root.setCenter(contenido);
        if (animar && root.getScene() != null) {
            FadeTransition fade = new FadeTransition(Duration.millis(180), contenido);
            fade.setFromValue(0.65);
            fade.setToValue(1);
            fade.play();
        } else contenido.setOpacity(1);
    }

    private StackPane crearCapaCarga() {
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(52, 52);
        Label aviso = new Label("Cargando sección de OPTEM...");
        VBox tarjeta = new VBox(14, progress, aviso);
        tarjeta.setAlignment(Pos.CENTER);
        tarjeta.setMaxSize(300, 165);
        tarjeta.getStyleClass().add("optem-loading-card");
        StackPane shade = new StackPane(tarjeta);
        shade.getStyleClass().add("optem-loading-shade");
        shade.setVisible(false);
        shade.setManaged(false);
        return shade;
    }

    private void construirInicioEstudiante(boolean animacionesActivas, String colorFuerte, Stage parentStage) {
        VBox mainContent = new VBox(25);
        mainContent.setPadding(new Insets(30, 45, 30, 45));
        mainContent.setStyle("-fx-background-color: transparent;");
        scrollAlumno = new ScrollPane(mainContent);
        scrollAlumno.setFitToWidth(true);
        scrollAlumno.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        Map<String, String> perfil = controller.getPerfilUsuario();
        String primerNombre = perfil.get("nombre_completo").split(" ")[0];
        
        // --- NUEVA LÓGICA DE XP REAL ---
        Preferences prefs = Preferences.userNodeForPackage(DashboardView.class);
        int xpTotal = com.optem.logic.XPService.xp(correoActualGlobal);
        
        // Calculamos el nivel (cada 200 XP es un nivel)
        int nivelActual = (xpTotal / 200) + 1;
        int xpNivelBase = (nivelActual - 1) * 200;
        int xpSiguienteNivel = nivelActual * 200;
        
        // Avance porcentual en el nivel actual
        double avanceReal = (double) (xpTotal - xpNivelBase) / 200.0;

        String colorBanner = StudentPremiumTheme.strongAccent();
        StackPane bannerContainer = new StackPane(); bannerContainer.setPrefHeight(160); 
        Pane bannerBackground = new Pane(); 
        bannerBackground.setStyle("-fx-background-color: linear-gradient(to bottom right, derive(" + colorBanner + ", 8%) 0%, " + colorBanner + " 36%, derive(" + colorBanner + ", -12%) 58%, derive(" + colorBanner + ", -32%) 100%); -fx-background-radius: 27; -fx-border-color: derive("+colorFuerte+",60%), derive("+colorFuerte+",25%); -fx-border-width: 2, 1; -fx-border-insets: 0, 4; -fx-border-radius: 27, 24;");
        bannerBackground.getStyleClass().add("optem-hero");
        
        HBox topBannerContent = new HBox(); topBannerContent.setPadding(new Insets(25, 40, 25, 40)); topBannerContent.setAlignment(Pos.CENTER_LEFT);
        VBox greetBox = new VBox(10); greetBox.setAlignment(Pos.CENTER_LEFT);
        
        Label lblGreet = new Label(getDynamicGreetingText() + ", " + primerNombre); 
        lblGreet.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: white; -fx-font-family: 'Segoe UI';");
        
        Label lblCareer = new Label(perfil.get("carrera") + " | Promedio: " + perfil.get("promedio_general"));
        lblCareer.setStyle("-fx-font-size: 14px; -fx-text-fill: rgba(255, 255, 255, 0.8);");
        
        HBox progressBox = new HBox(15); progressBox.setAlignment(Pos.CENTER_LEFT);
        xpBar = new ProgressBar(0); 
        xpBar.setPrefWidth(250); xpBar.setPrefHeight(8); 
        xpBar.setStyle("-fx-accent: " + colorFuerte + "; -fx-control-inner-background: rgba(255, 255, 255, 0.2);");
        
        // Mostramos el nivel y XP reales
        Label lblAvance = new Label("Nivel " + nivelActual + " | " + xpTotal + " XP de " + xpSiguienteNivel + " XP");
        xpLabel=lblAvance;
        lblAvance.setStyle("-fx-font-size: 12px; -fx-text-fill: white; -fx-font-weight: bold;");
        progressBox.getChildren().addAll(xpBar, lblAvance);

        if (animacionesActivas) {
            Timeline xpAnim = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(xpBar.progressProperty(), 0)), new KeyFrame(Duration.seconds(1.5), new KeyValue(xpBar.progressProperty(), avanceReal, Interpolator.EASE_OUT)));
            xpAnim.play();
        } else { xpBar.setProgress(avanceReal); }

        greetBox.getChildren().addAll(lblGreet, lblCareer, progressBox);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        
        VBox clockContainer = new VBox(-5); clockContainer.setAlignment(Pos.CENTER_RIGHT);
        Label lblClock = new Label(); 
        lblClock.setStyle("-fx-font-size: 42px; -fx-font-weight: bold; -fx-text-fill: white;"); 
        Label lblDateGlass = new Label(); 
        lblDateGlass.setStyle("-fx-font-size: 16px; -fx-text-fill: rgba(255, 255, 255, 0.8);");
        
        initClock(lblClock, lblDateGlass); clockContainer.getChildren().addAll(lblClock, lblDateGlass); 
        
        topBannerContent.getChildren().addAll(greetBox, spacer, clockContainer); 
        bannerContainer.getChildren().addAll(bannerBackground, topBannerContent);
        bannerContainer.setEffect(new DropShadow(23, 0, 7, Color.rgb(0, 80, 56, 0.26)));

        GridPane galleryGrid = new GridPane(); galleryGrid.setHgap(25); galleryGrid.setVgap(25); galleryGrid.setAlignment(Pos.CENTER);
        VBox cardNotas = createSectionCard("Notas", "Tus apuntes de clase", "/images/card_notes.jpg", -50); cardNotas.setOnMouseClicked(e -> { if(btnNotas!=null) btnNotas.fire(); });
        VBox cardAgenda = createSectionCard("Agenda", "Horario semanal", "/images/card_agenda.jpg", -140); cardAgenda.setOnMouseClicked(e -> { if(btnAgenda!=null) btnAgenda.fire(); });
        VBox cardTareas = createSectionCard("Tareas", "Lista de pendientes", "/images/card_tasks.jpg", -90); cardTareas.setOnMouseClicked(e -> { if(btnTareas!=null) btnTareas.fire(); });
        galleryGrid.add(cardNotas, 0, 0); galleryGrid.add(cardAgenda, 1, 0); galleryGrid.add(cardTareas, 2, 0);

        VBox materiasPane = new VBox(15); 
        materiasPane.setStyle("-fx-background-color: linear-gradient(to bottom, -color-bg-panel, derive(-color-bg-panel,-3%)); -fx-padding: 25; -fx-background-radius: 22; -fx-border-color: derive(" + colorFuerte + ", 43%); -fx-border-radius: 22; -fx-border-width: 1.4; -fx-effect: dropshadow(three-pass-box, rgba(27,49,66,.16), 16, 0, 0, 7);");
        Label lblMateriasTitle = new Label("📚 Mis Clases Registradas"); lblMateriasTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: -color-text-main; -fx-effect: null;"); 
        lblMateriasTitle.getStyleClass().add("lbl-titulos-seccion");

        VBox listaTarjetas = new VBox(10);
        // --- LEER MATERIAS REALES DEL MOCKDB PARA EL DASHBOARD ---
        List< Map < String , String >> clasesReal = MockDB.obtenerMateriasDelAlumno(correoActualGlobal);
        
        java.util.Set<String> urgentes=com.optem.database.GrupoMuroDAO.urgentesPendientes(correoActualGlobal);
        if (clasesReal != null) {
            for (Map<String, String> clase : clasesReal) {
                HBox classCard = new HBox(15); classCard.setAlignment(Pos.CENTER_LEFT);
                classCard.setStyle("-fx-background-color: linear-gradient(to bottom, -color-bg-panel, derive(-color-bg-panel,-6%)); -fx-padding: 15; -fx-background-radius: 15; -fx-border-color: derive(" + colorFuerte + ", 48%); -fx-border-radius: 15; -fx-border-width: 1px; -fx-effect: dropshadow(three-pass-box, rgba(18,56,62,.14), 10, 0, 0, 3); -fx-cursor: hand;");
                
                StackPane iconPane = new StackPane(); Circle circle = new Circle(20, Color.web(colorFuerte, 0.15));
                Label initial = new Label(clase.get("nombre").substring(0, 1)); initial.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: " + colorFuerte + ";");
                iconPane.getChildren().addAll(circle, initial);
                
                VBox details = new VBox(3);
                Label lblNom = new Label(clase.get("clave") + " - " + clase.get("nombre")); lblNom.setStyle("-fx-font-weight: bold; -fx-text-fill: -color-text-main; -fx-effect: null;"); lblNom.getStyleClass().add("lbl-text-normal");
                Label lblProf = new Label("👨‍🏫 " + clase.get("profesor")); lblProf.setStyle("-fx-text-fill: -color-text-muted; -fx-effect: null;"); lblProf.getStyleClass().add("lbl-text-pequeno");
                details.getChildren().addAll(lblNom, lblProf);
                
                Region spc = new Region(); HBox.setHgrow(spc, Priority.ALWAYS);
                
                String horarioRaw = clase.get("horario"); 
                // Evitamos un error si el horario viene diferente
                String diasCompletos = horarioRaw;
                if(horarioRaw.contains("/")) {
                    String[] partesHorario = horarioRaw.split("/"); 
                    diasCompletos = partesHorario[0].split(" ")[0];
                    if (partesHorario.length > 1) diasCompletos += " Y " + partesHorario[1].trim().split(" ")[0]; 
                }
                
                VBox timeBox = new VBox(3); timeBox.setAlignment(Pos.CENTER_RIGHT);
                Label lblHorario = new Label(diasCompletos); lblHorario.setStyle("-fx-font-weight: bold; -fx-text-fill: " + colorFuerte + "; -fx-background-color: " + colorFuerte + "15; -fx-padding: 4 8; -fx-background-radius: 6; -fx-effect: null;"); lblHorario.getStyleClass().add("lbl-text-pequeno");
                Label lblGrupo = new Label("Grupo: " + clase.get("grupo")); lblGrupo.setStyle("-fx-text-fill: -color-text-muted; -fx-effect: null;"); lblGrupo.getStyleClass().add("lbl-text-pequeno");
                timeBox.getChildren().addAll(lblHorario, lblGrupo);
                
                if(urgentes.contains(clase.get("id_grupo"))){
                    Label urgente=new Label("❗");
                    urgente.setStyle("-fx-font-size: 30px; -fx-text-fill: #dc2626; -fx-font-weight: bold;");
                    classCard.getChildren().add(urgente);
                }
                classCard.getChildren().addAll(iconPane, details, spc, timeBox);
                classCard.setOnMouseEntered(e -> classCard.setStyle("-fx-background-color: -color-bg-panel; -fx-padding: 15; -fx-background-radius: 10; -fx-border-color: " + colorFuerte + "; -fx-border-radius: 10; -fx-border-width: 1px; -fx-cursor: hand;"));
                classCard.setOnMouseExited(e -> classCard.setStyle("-fx-background-color: linear-gradient(to bottom, -color-bg-panel, derive(-color-bg-panel,-6%)); -fx-padding: 15; -fx-background-radius: 15; -fx-border-color: derive(" + colorFuerte + ", 48%); -fx-border-radius: 15; -fx-border-width: 1px; -fx-effect: dropshadow(three-pass-box, rgba(18,56,62,.14), 10, 0, 0, 3);"));
                
                classCard.setOnMouseClicked(e -> {
                    GrupoMuroView.abrir(parentStage,Integer.parseInt(clase.get("id_grupo")),clase.get("nombre")+" · "+clase.get("grupo"));
                    classCard.getChildren().removeIf(n->n instanceof Label l && "❗".equals(l.getText()));
                });
                listaTarjetas.getChildren().add(classCard);
            }
        }

        materiasPane.getChildren().addAll(lblMateriasTitle, listaTarjetas);
        VBox institutionalFooter = createAnimatedShieldFooter(animacionesActivas);
        mainContent.getChildren().addAll(bannerContainer, galleryGrid, materiasPane, institutionalFooter);
    }

    private void mostrarMuroTeams(Map<String, String> clase, String colorFuerte, Window owner) {
        Stage popup = new Stage(); popup.initOwner(owner); popup.initStyle(StageStyle.UNDECORATED);
        
        VBox layout = new VBox(); 
        layout.setStyle("-fx-background-color: -color-bg-panel; -fx-border-color: -color-border; -fx-border-width: 1; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 20, 0, 0, 8);");
        
        HBox header = new HBox(15); header.setPadding(new Insets(15, 20, 15, 20)); header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-background-color: " + colorFuerte + ";");
        
        StackPane iconPane = new StackPane(); Circle circle = new Circle(18, Color.web("#ffffff", 0.2));
        Label initial = new Label(clase.get("nombre").substring(0, 1)); initial.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: white; -fx-effect: null;");
        iconPane.getChildren().addAll(circle, initial);
        
        VBox headerText = new VBox(2);
        Label lblTitulo = new Label(clase.get("nombre")); lblTitulo.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white; -fx-effect: null;");
        Label lblSubtitle = new Label("Grupo: " + clase.get("grupo") + " | General"); lblSubtitle.setStyle("-fx-text-fill: rgba(255,255,255,0.8); -fx-font-size: 12px; -fx-effect: null;");
        headerText.getChildren().addAll(lblTitulo, lblSubtitle);
        
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Button btnCerrar = new Button("✕"); btnCerrar.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-font-size: 18px; -fx-cursor: hand; -fx-effect: null;");
        btnCerrar.setOnAction(e -> popup.close());
        header.getChildren().addAll(iconPane, headerText, spacer, btnCerrar);
        
        VBox muro = new VBox(15); muro.setPadding(new Insets(20));
        muro.setStyle("-fx-background-color: -color-bg-base;");
        
        String msj = clase.get("mensaje");
        if(msj != null && !msj.isEmpty()) {
            muro.getChildren().add(crearPostTeams(clase.get("profesor"), "Aviso General", msj, colorFuerte));
        }
        
        String tarea = clase.get("tarea");
        if(tarea != null && !tarea.isEmpty()) {
            muro.getChildren().add(crearPostTeams(clase.get("profesor"), "Asignación", "Se ha asignado un nuevo trabajo: " + tarea, "#D32F2F"));
        }
        
        ScrollPane scrollMuro = new ScrollPane(muro);
        scrollMuro.setFitToWidth(true); scrollMuro.setPrefHeight(400);
        scrollMuro.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        
        VBox footerChat = new VBox(10); footerChat.setPadding(new Insets(15, 20, 15, 20));
        footerChat.setStyle("-fx-background-color: -color-bg-panel; -fx-border-color: -color-border; -fx-border-width: 1 0 0 0;");
        
        TextField txtRedactar = new TextField(); txtRedactar.setPromptText("Iniciar una publicación nueva...");
        txtRedactar.setStyle("-fx-background-color: -color-bg-base; -fx-text-fill: -color-text-main; -fx-padding: 10; -fx-background-radius: 8; -fx-border-color: -color-border; -fx-border-radius: 8;");
        
        Button btnPublicar = new Button("Publicar en el canal");
        btnPublicar.setStyle("-fx-background-color: " + colorFuerte + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5; -fx-cursor: hand; -fx-effect: null;");
        
        btnPublicar.setOnAction(e -> {
            if(!txtRedactar.getText().trim().isEmpty()) {
                muro.getChildren().add(crearPostTeams(controller.getNombreEstudiante(), "Comentario de alumno", txtRedactar.getText(), "#64748b"));
                txtRedactar.clear();
                scrollMuro.setVvalue(1.0);
            }
        });
        
        footerChat.getChildren().addAll(txtRedactar, btnPublicar);
        layout.getChildren().addAll(header, scrollMuro, footerChat);
        
        Scene scene = new Scene(layout, 650, 550); OptemLook.install(scene); scene.setFill(Color.TRANSPARENT);
        aplicarTemaGlobal(scene, correoActualGlobal);
        popup.setScene(scene); OptemDialogStyle.apply(popup); 
        popup.show();
        popup.setX(owner.getX() + (owner.getWidth() - popup.getWidth()) / 2);
        popup.setY(owner.getY() + (owner.getHeight() - popup.getHeight()) / 2);
    }
    
    private VBox crearPostTeams(String autor, String titulo, String contenido, String colorHighlight) {
        VBox post = new VBox(10); post.setPadding(new Insets(15));
        post.setStyle("-fx-background-color: -color-bg-panel; -fx-background-radius: 8; -fx-border-color: -color-border; -fx-border-radius: 8; -fx-border-width: 1;");
        
        HBox header = new HBox(10); header.setAlignment(Pos.CENTER_LEFT);
        Circle avatar = new Circle(15, Color.web(colorHighlight, 0.2));
        Label inicial = new Label(autor.substring(0, 1)); inicial.setStyle("-fx-text-fill: " + colorHighlight + "; -fx-font-weight: bold; -fx-effect: null;");
        StackPane avatarBox = new StackPane(avatar, inicial);
        
        Label lblAutor = new Label(autor); lblAutor.setStyle("-fx-font-weight: bold; -fx-text-fill: -color-text-main; -fx-effect: null;");
        Label lblFecha = new Label("Hoy"); lblFecha.setStyle("-fx-text-fill: -color-text-muted; -fx-font-size: 12px; -fx-effect: null;");
        header.getChildren().addAll(avatarBox, lblAutor, lblFecha);
        
        Label lblTitulo = new Label(titulo); lblTitulo.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: -color-text-main; -fx-effect: null;");
        Label lblContenido = new Label(contenido); lblContenido.setStyle("-fx-text-fill: -color-text-main; -fx-wrap-text: true; -fx-effect: null;");
        
        post.getChildren().addAll(header, lblTitulo, lblContenido);
        return post;
    }

    public void aplicarTemaGlobal(Scene scene, String correoUsuario) {
        Preferences prefs = Preferences.userNodeForPackage(DashboardView.class);
        boolean isDark = prefs.getBoolean("PREF_DARK_MODE_" + correoUsuario, false);
        String colorFuerte = prefs.get("PREF_ACCENT_COLOR_" + correoUsuario, "#3D5A4D");
        String fontPref = prefs.get("PREF_FONT_SIZE_" + correoUsuario, "Normal");
        
        String bgBase = isDark ? "#121212" : "#f1f5f9"; String bgPanel = isDark ? "#1e1e1e" : "#ffffff"; 
        String textMain = isDark ? "#f8f9fa" : "#1e293b"; String textMuted = isDark ? "#94a3b8" : "#64748b"; 
        String borderCol = isDark ? "#333333" : "#e2e8f0"; 
        
        String fontBase = "14px";
        if (fontPref.equals("Pequeño")) fontBase = "12px";
        else if (fontPref.equals("Grande")) fontBase = "17px";

        scene.getRoot().setStyle(
            "-color-bg-base: " + bgBase + "; " +
            "-color-bg-panel: " + bgPanel + "; " +
            "-color-text-main: " + textMain + "; " +
            "-color-text-muted: " + textMuted + "; " +
            "-color-border: " + borderCol + "; " +
            "-color-accent: " + colorFuerte + "; " +
            "-fx-background-color: linear-gradient(to bottom right, -color-bg-base 0%, derive(-color-bg-base,-3%) 100%); " +
            "-fx-font-size: " + fontBase + ";"
        );
        // Sistema premium compartido entre los tres roles; usa el tema activo por cuenta.
        if ("ESTUDIANTE".equals(prefs.get("ROLE_" + correoUsuario, ""))
                || "MAESTRO".equals(prefs.get("ROLE_" + correoUsuario, ""))
                || "ADMINISTRADOR".equals(prefs.get("ROLE_" + correoUsuario, "")))
            StudentPremiumTheme.install(scene);
    }

    // --- FIX SOMBREADO: Agregado -fx-effect: null; al botón del menú ---
    private Button createMenuButton(String icon, String text) {
        Button btn = new Button(icon + "  " + text); btn.setId(text); btn.setUserData(icon); btn.setMaxWidth(Double.MAX_VALUE); btn.setAlignment(Pos.CENTER_LEFT);
        String fondoHover = ("ESTUDIANTE".equals(controller.getRolUsuario()) || "MAESTRO".equals(controller.getRolUsuario()) || "ADMINISTRADOR".equals(controller.getRolUsuario()))
                ? "-student-accent-soft" : "rgba(61,160,97,.14)";
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-text-muted; -fx-font-weight: 600; -fx-padding: 12 15; -fx-background-radius: 12; -fx-cursor: hand;");
        
        btn.setOnMouseEntered(e -> { 
            if (!btn.getStyleClass().contains("menu-btn-active")) btn.setStyle("-fx-background-color: " + fondoHover + "; -fx-text-fill: -color-text-main; -fx-font-weight: 600; -fx-padding: 12 15; -fx-cursor: hand; -fx-background-radius: 12;");
        });
        
        btn.setOnMouseExited(e -> { if (!btn.getStyleClass().contains("menu-btn-active")) btn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-text-muted; -fx-font-weight: 600; -fx-padding: 12 15; -fx-background-radius: 12; -fx-cursor: hand;"); });
        return btn;
    }

    // --- FIX SOMBREADO: Agregado -fx-effect: null; a la selección del menú ---
    private void actualizarSeleccionMenu(Button botonSeleccionado) {
        this.menuSeleccionado=botonSeleccionado;
        Button[] botones = {btnInicio, btnAgenda, btnNotas, btnTareas, btnReuniones, btnActividades, btnGrupos, btnPomodoro, btnLogros, btnReinscripcion, btnAjustes, btnAdStats, btnAdPerfiles, btnAdDocs, btnAdIA, btnAdReinscripcion, btnAdConfig};
        String colorFuerte = ("ESTUDIANTE".equals(controller.getRolUsuario()) || "MAESTRO".equals(controller.getRolUsuario()) || "ADMINISTRADOR".equals(controller.getRolUsuario()))
                ? StudentPremiumTheme.strongAccent() : controller.getColorFuerte();
        for (Button b : botones) {
            if (b == null) continue;
            b.getStyleClass().removeAll("menu-btn-active", "optem-menu-selected");
            if (b == botonSeleccionado) { 
                b.getStyleClass().add("menu-btn-active"); b.getStyleClass().add("optem-menu-selected");
                b.setStyle("-fx-background-color: linear-gradient(to bottom right, derive(" + colorFuerte + ", 28%), " + colorFuerte + ", derive(" + colorFuerte + ", -39%)); -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12 15; -fx-background-radius: 13; -fx-cursor: default; -fx-effect: dropshadow(three-pass-box, rgba(27,49,66,.25), 10, 0, 0, 3);");
            } else { b.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-text-muted; -fx-font-weight: 600; -fx-padding: 12 15; -fx-background-radius: 12; -fx-cursor: hand;"); }
        }
    }

    private String getDynamicGreetingText() { int hora = LocalTime.now().getHour(); if (hora >= 6 && hora < 12) return "¡Buenos días"; else if (hora >= 12 && hora < 19) return "¡Buenas tardes"; else return "¡Buenas noches"; }

    private void initClock(Label lblClock, Label lblDate) {
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm"); DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", new Locale("es", "ES"));
        Timeline clock = new Timeline(new KeyFrame(Duration.ZERO, e -> { LocalDateTime now = LocalDateTime.now(); lblClock.setText(now.format(timeFmt)); lblDate.setText(now.format(dateFmt)); }), new KeyFrame(Duration.seconds(1)));
        clock.setCycleCount(Animation.INDEFINITE);
        // No dejar relojes activos cuando el usuario sale de esta pantalla.
        lblClock.sceneProperty().addListener((obs, anterior, actual) -> {
            if (actual == null) clock.stop(); else clock.play();
        });
        clock.play();
    }

    private Image getSquareImage(Image original) {
        if (original == null || original.isError()) return original;
        int w = (int) original.getWidth(); int h = (int) original.getHeight(); int size = Math.min(w, h);
        return new WritableImage(original.getPixelReader(), (w - size) / 2, (h - size) / 2, size, size);
    }

    private void toggleButtonTexts(boolean showText, Button... buttons) {
        for (Button btn : buttons) {
            if (btn == null) continue; String icon = (String) btn.getUserData();
            if (showText) { btn.setText(icon + "  " + btn.getId()); btn.setAlignment(Pos.CENTER_LEFT); } else { btn.setText(icon); btn.setAlignment(Pos.CENTER); }
        }
    }

    private VBox createSectionCard(String title, String sub, String imgPath, double offsetY) {
        VBox card = new VBox(0); 
        card.setPrefSize(280, 200); 
        card.setMinSize(280, 200);
        card.setMaxSize(280, 200);
        card.setStyle("-fx-background-color: linear-gradient(to bottom, -color-bg-panel 0%, derive(-color-bg-panel,-4%) 100%); -fx-background-radius: 24; -fx-border-color: derive(" + controller.getColorFuerte() + ", 38%); -fx-border-radius: 24; -fx-border-width: 1.2; -fx-effect: dropshadow(three-pass-box, rgba(27,49,66,.19), 18, 0, 0, 8); -fx-cursor: hand;");
        card.getStyleClass().add("optem-feature-card");
        
        Pane imgContainer = new Pane(); 
        imgContainer.setPrefSize(280, 120);
        imgContainer.setMinSize(280, 120);
        imgContainer.setMaxSize(280, 120);
        
        try { 
            InputStream is = getClass().getResourceAsStream(imgPath); 
            if (is != null) { 
                ImageView iv = new ImageView(new Image(is)); 
                iv.setPreserveRatio(true); 
                iv.setFitWidth(280); 
                iv.setLayoutY(offsetY); 
                Rectangle clip = new Rectangle(280, 120); 
                clip.setArcWidth(30); clip.setArcHeight(30); 
                imgContainer.setClip(clip); imgContainer.getChildren().add(iv); 
            } 
        } catch(Exception e) { imgContainer.setStyle("-fx-background-color: -color-bg-base; -fx-background-radius: 15 15 0 0;"); }
        
        VBox info = new VBox(2); info.setPadding(new Insets(15)); Label t = new Label(title); t.setStyle("-fx-font-weight: bold; -fx-text-fill: -color-text-main; -fx-effect: null;"); t.getStyleClass().add("lbl-text-normal"); Label s = new Label(sub); s.setStyle("-fx-text-fill: -color-text-muted; -fx-effect: null;"); s.getStyleClass().add("lbl-text-pequeno"); info.getChildren().addAll(t, s); card.getChildren().addAll(imgContainer, info);
        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(150), card); scaleIn.setToX(1.03); scaleIn.setToY(1.03); ScaleTransition scaleOut = new ScaleTransition(Duration.millis(150), card); scaleOut.setToX(1.0); scaleOut.setToY(1.0); card.setOnMouseEntered(e -> scaleIn.playFromStart()); card.setOnMouseExited(e -> scaleOut.playFromStart()); return card;
    }

    private void mostrarVentanaMultiCuentas(Stage mainStage, String correoActual, String fileKey) {}
    
    private VBox createAnimatedShieldFooter(boolean animacionesActivas) { 
        VBox f = new VBox(15); f.setAlignment(Pos.CENTER); f.setPadding(new Insets(35, 0, 20, 0)); 
        Label info = new Label("Optem ERP © 2026 | Universidad Autónoma del Estado de México");
        info.setStyle("-fx-text-fill: -color-text-muted; -fx-effect: null;"); info.getStyleClass().add("lbl-text-pequeno");
        f.getChildren().add(info);
        return f; 
    }
    
    private void configurarNavegacionPorTecladoGlobal(Scene scene, Stage stage) {
        AjustesController ajCtrl = new AjustesController(correoActualGlobal);
        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            teclasActivas.add(e.getCode());
            if (ajCtrl.getPrefBoolean("PREF_ACC_KEYBOARD", false)) {
                if (e.getCode() == KeyCode.TAB) { btnToggle.fire(); e.consume(); return; }
                if (e.getCode() == KeyCode.ENTER) { ajCtrl.clickIzquierdoMouse(); e.consume(); return; }
            }
            if (e.getCode() == KeyCode.F2 && ajCtrl.getPrefBoolean("PREF_ACC_VOICE", false)) { mostrarAsistenteDeVoz(stage, ajCtrl.getColorFuerte()); e.consume(); }
        });

        scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_RELEASED, e -> { teclasActivas.remove(e.getCode()); });

        if (ratonTimer != null) ratonTimer.stop();
        ratonTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!ajCtrl.getPrefBoolean("PREF_ACC_KEYBOARD", false)) return;
                if (teclasActivas.contains(KeyCode.LEFT) && teclasActivas.contains(KeyCode.RIGHT)) {
                    if (teclasActivas.contains(KeyCode.UP)) ajCtrl.scrollMouse(-1);
                    if (teclasActivas.contains(KeyCode.DOWN)) ajCtrl.scrollMouse(1);
                } else {
                    int dx = 0, dy = 0; int velocidad = 8; 
                    if (teclasActivas.contains(KeyCode.UP)) dy -= velocidad;
                    if (teclasActivas.contains(KeyCode.DOWN)) dy += velocidad;
                    if (teclasActivas.contains(KeyCode.LEFT)) dx -= velocidad;
                    if (teclasActivas.contains(KeyCode.RIGHT)) dx += velocidad;
                    if (dx != 0 || dy != 0) { ajCtrl.moverCursorMouse(dx, dy); }
                }
            }
        };
        // No ejecutar un AnimationTimer a 60 FPS cuando la accesibilidad está desactivada.
        if (ajCtrl.getPrefBoolean("PREF_ACC_KEYBOARD", false)) ratonTimer.start();
        scene.windowProperty().addListener((obs, anterior, actual) -> {
            if (actual == null && ratonTimer != null) ratonTimer.stop();
        });
    }
    
    private void mostrarAsistenteDeVoz(Stage owner, String colorFuerte) {
        Stage popup = new Stage(); popup.initOwner(owner); popup.initStyle(StageStyle.UNDECORATED);
        VBox rootBox = new VBox(15); rootBox.setPadding(new Insets(25));
        rootBox.setStyle("-fx-background-color: -color-bg-panel; -fx-border-color: " + colorFuerte + "; -fx-border-width: 2; -fx-background-radius: 12; -fx-border-radius: 12;");
        Label lblTit = new Label("🎙 Te escucho..."); lblTit.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: -color-text-main; -fx-effect: null;");
        TextField txtComando = new TextField(); txtComando.setPromptText("Dicta tu comando..."); txtComando.setStyle("-fx-background-color: -color-bg-base; -fx-text-fill: -color-text-main; -fx-padding: 10;");
        Button btnEjecutar = new Button("Procesar Comando"); btnEjecutar.setStyle("-fx-background-color: " + colorFuerte + "; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 6; -fx-effect: null;");
        
        btnEjecutar.setOnAction(e -> {
            String c = txtComando.getText().toLowerCase();
            if (c.contains("inicio") || c.contains("principal") || c.contains("home")) { if(btnInicio != null) btnInicio.fire(); }
            else if (c.contains("agenda") || c.contains("horario") || c.contains("clases hoy")) { if(btnAgenda != null) btnAgenda.fire(); }
            else if (c.contains("nota") || c.contains("apunte")) { if(btnNotas != null) btnNotas.fire(); }
            else if (c.contains("tarea") || c.contains("pendiente") || c.contains("trabajo")) { if(btnTareas != null) btnTareas.fire(); }
            else if (c.contains("reunion") || c.contains("videollamada") || c.contains("teams")) { if(btnReuniones != null) btnReuniones.fire(); }
            else if (c.contains("pomodoro") || c.contains("reloj") || c.contains("estudiar")) { if(btnPomodoro != null) btnPomodoro.fire(); }
            else if (c.contains("logro") || c.contains("medallas") || c.contains("progreso")) { if(btnLogros != null) btnLogros.fire(); }
            else if (c.contains("reinscripcion") || c.contains("semestre") || c.contains("materias nuevas")) { if(btnReinscripcion != null) btnReinscripcion.fire(); else if (btnAdReinscripcion != null) btnAdReinscripcion.fire(); }
            else if (c.contains("ajuste") || c.contains("configura") || c.contains("tema")) { if(btnAjustes != null) btnAjustes.fire(); else if(btnAdConfig != null) btnAdConfig.fire(); }
            popup.close();
        });
        
        rootBox.getChildren().addAll(lblTit, txtComando, btnEjecutar);
        Scene s = new Scene(rootBox, 350, 180); OptemLook.install(s); s.setFill(Color.TRANSPARENT);
        aplicarTemaGlobal(s, correoActualGlobal); popup.setScene(s); OptemDialogStyle.apply(popup); popup.show();
        popup.setX(owner.getX() + (owner.getWidth() - popup.getWidth()) / 2); popup.setY(owner.getY() + (owner.getHeight() - popup.getHeight()) / 2);
    }
}