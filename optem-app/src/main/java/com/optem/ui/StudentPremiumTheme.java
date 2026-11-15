package com.optem.ui;

import java.util.prefs.Preferences;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Sistema visual compartido Estudiante/Docente/Administrador: reutiliza las preferencias existentes de OPTEM.
 * No consulta MySQL, no crea listeners de datos ni cambia eventos.
 * Las ondas son estáticas, con mouseTransparent=true.
 */
public final class StudentPremiumTheme {
    private static final String CSS = "/css/optem-student-premium.css";
    private static final String DECORATED = "optem-student-premium-processed";
    private static final String WRAPPER = "optem-student-premium-wrapper";
    private StudentPremiumTheme() {}

    private static Preferences prefs() { return Preferences.userNodeForPackage(DashboardView.class); }
    private static String account() { return prefs().get("LAST_ACTIVE_ACCOUNT", ""); }

    public static boolean active() {
        String mail=account();
        String role = prefs().get("ROLE_"+mail,"");
        return !mail.isBlank() && ("ESTUDIANTE".equalsIgnoreCase(role) || "MAESTRO".equalsIgnoreCase(role) || "ADMINISTRADOR".equalsIgnoreCase(role));
    }

    private static Color accent() {
        try { return Color.web(prefs().get("PREF_ACCENT_COLOR_"+account(),"#3D5A4D")); }
        catch (RuntimeException ex) { return Color.web("#3D5A4D"); }
    }
    private static boolean dark() { return prefs().getBoolean("PREF_DARK_MODE_"+account(),false); }
    public static boolean isDarkMode() { return dark(); }
    public static String accentHex() { return hex(accent()); }
    private static String hex(Color c) {
        return String.format(java.util.Locale.ROOT,"#%02X%02X%02X",
             (int)Math.round(c.getRed()*255),(int)Math.round(c.getGreen()*255),(int)Math.round(c.getBlue()*255));
    }
    private static Color blend(Color a,Color b,double share) { return a.interpolate(b,share); }
    /** Misma tonalidad elegida, oscurecida solo cuando el texto blanco lo requiere. */
    public static String strongAccent() {
        Color c=accent();
        for(int k=0;k<12;k++) {
            double r=c.getRed(),g=c.getGreen(),b=c.getBlue();
            double L=0.2126*channel(r)+0.7152*channel(g)+0.0722*channel(b);
            if((1.05)/(L+0.05)>=4.5) break;
            c=c.interpolate(Color.BLACK,0.15);
        }
        return hex(c);
    }
    private static double channel(double v) { return v<=0.04045 ? v/12.92 : Math.pow((v+0.055)/1.055,2.4); }
    private static String tokenStyle() {
        Color a=accent(); boolean d=dark();
        String size=prefs().get("PREF_FONT_SIZE_"+account(),"Normal");
        String fontSize="Grande".equals(size)?"17px":("Pequeño".equals(size)?"12px":"14px");
        Color surface=d ? Color.web("#20252E") : Color.WHITE;
        Color alt=d ? Color.web("#252D39") : Color.web("#F5F9FC");
        Color border=d ? Color.web("#3C4754") : Color.web("#CADBE2");
        Color main=d ? Color.web("#F2F6FC") : Color.web("#1A2A3E");
        Color muted=d ? Color.web("#BDCBD8") : Color.web("#52677E");
        Color deep=Color.web(strongAccent());
        Color soft=blend(a, surface, d ? .78 : .88);
        Color backdrop=d ? Color.web("#141B25") : Color.web("#EDF4FA");
        return "-fx-font-size:"+fontSize+";"+
               "-student-accent:"+hex(a)+";"+
               "-student-accent-deep:"+hex(deep)+";"+
               "-student-accent-soft:"+hex(soft)+";"+
               "-student-surface:"+hex(surface)+";"+
               "-student-surface-alt:"+hex(alt)+";"+
               "-student-border:"+hex(border)+";"+
               "-student-text:"+hex(main)+";"+
               "-student-muted:"+hex(muted)+";"+
               "-student-backdrop:"+hex(backdrop)+";";
    }

    public static void install(Scene scene) {
        if(scene==null || !active()) return;
        var url=StudentPremiumTheme.class.getResource(CSS);
        if(url!=null && !scene.getStylesheets().contains(url.toExternalForm()))
            scene.getStylesheets().add(url.toExternalForm());
        Parent root=scene.getRoot();
        if(!root.getStyleClass().contains("optem-student-premium"))
            root.getStyleClass().add("optem-student-premium");
        String role = prefs().get("ROLE_"+account(),"");
        if("MAESTRO".equalsIgnoreCase(role)) root.getStyleClass().add("teacher-premium");
        else root.getStyleClass().remove("teacher-premium");
        if("ADMINISTRADOR".equalsIgnoreCase(role)) root.getStyleClass().add("admin-premium");
        else root.getStyleClass().remove("admin-premium");
        if(dark()) root.getStyleClass().add("student-dark");
        else root.getStyleClass().remove("student-dark");
        // Looked-up JavaFX colors; no web CSS variables and no new theme preferences.
        String original=root.getStyle().replaceAll("(?s)-student-[a-z-]+\\s*:\\s*[^;]*;", "");
        root.setStyle(original+";"+tokenStyle());
        decorate(root);
    }

    public static void install(Dialog<?> dialog) {
        if(dialog==null || !active()) return;
        var pane=dialog.getDialogPane();
        var url=StudentPremiumTheme.class.getResource(CSS);
        if(url!=null && !pane.getStylesheets().contains(url.toExternalForm()))
            pane.getStylesheets().add(url.toExternalForm());
        if(!pane.getStyleClass().contains("optem-student-premium"))
            pane.getStyleClass().add("optem-student-premium");
        if(!pane.getStyleClass().contains("student-premium-dialog"))
            pane.getStyleClass().add("student-premium-dialog");
        String role = prefs().get("ROLE_"+account(),"");
        if("MAESTRO".equalsIgnoreCase(role)) pane.getStyleClass().add("teacher-premium");
        else pane.getStyleClass().remove("teacher-premium");
        if("ADMINISTRADOR".equalsIgnoreCase(role)) pane.getStyleClass().add("admin-premium");
        else pane.getStyleClass().remove("admin-premium");
        if(dark()) pane.getStyleClass().add("student-dark");
        else pane.getStyleClass().remove("student-dark");
        pane.setStyle(pane.getStyle()+";"+tokenStyle());
    }

    /** Mantiene el mismo Node y por lo tanto su estado/caché al volver a la página. */
    public static Node wrap(Node page) {
        if(page==null || !active()) return page;
        Object existing=page.getProperties().get(WRAPPER);
        if(existing instanceof StackPane) return (StackPane)existing;
        StackPane holder=new StackPane();
        holder.getStyleClass().add("student-premium-page");
        Pane waves=new Pane();
        waves.setMouseTransparent(true);
        waves.setPickOnBounds(false);
        waves.setMinSize(0,0);
        waves.prefWidthProperty().bind(holder.widthProperty());
        waves.prefHeightProperty().bind(holder.heightProperty());
        waves.setStyle("-fx-background-color: transparent;");
        Color a=accent();
        Color base=dark()? Color.web("#A8BFDE") : Color.web("#7D9DAF");
        SVGPath top=new SVGPath();
        top.setContent("M0 135 C165 20 395 155 700 0 L700 0 L0 0 Z");
        top.setFill(new Color(a.getRed(),a.getGreen(),a.getBlue(),dark()?.12:.10));
        top.translateXProperty().bind(waves.widthProperty().subtract(700));
        SVGPath top2=new SVGPath();
        top2.setContent("M0 192 C230 88 385 228 760 24 L760 0 L0 0 Z");
        top2.setFill(new Color(base.getRed(),base.getGreen(),base.getBlue(),dark()?.08:.10));
        top2.translateXProperty().bind(waves.widthProperty().subtract(760));
        SVGPath bottom=new SVGPath();
        bottom.setContent("M0 188 C225 35 485 190 810 55 L810 360 L0 360 Z");
        bottom.setFill(new Color(a.getRed(),a.getGreen(),a.getBlue(),dark()?.10:.085));
        bottom.translateYProperty().bind(waves.heightProperty().subtract(360));
        SVGPath bottom2=new SVGPath();
        bottom2.setContent("M0 175 C215 70 445 246 760 70 L760 360 L0 360 Z");
        bottom2.setFill(new Color(base.getRed(),base.getGreen(),base.getBlue(),dark()?.07:.085));
        bottom2.translateYProperty().bind(waves.heightProperty().subtract(360));
        waves.getChildren().addAll(top,top2,bottom2,bottom);
        holder.getChildren().addAll(waves,page);
        StackPane.setAlignment(page,Pos.TOP_LEFT);
        // Decorative shapes don't consume clicks; the underlying page retains its handlers.
        page.getProperties().put(WRAPPER,holder);
        decorate(page);
        return holder;
    }

    /** Adds CSS hooks to programmatic views while preserving all callbacks and data. */
    public static void decorate(Node node) {
        if(node==null || !active() || Boolean.TRUE.equals(node.getProperties().get(DECORATED))) return;
        node.getProperties().put(DECORATED,Boolean.TRUE);
        if(node instanceof ButtonBase) {
            add(node,"student-premium-button");
        } else if(node instanceof ComboBoxBase || node instanceof Spinner || node instanceof TextInputControl) {
            add(node,"student-premium-input");
        }
        if(node instanceof Label) {
            String s=node.getStyle();
            if(s!=null && (s.contains("-fx-font-size: 28") || s.contains("-fx-font-size: 30") || s.contains("-fx-font-size: 32")))
                add(node,"student-premium-heading");
        }
        if(node instanceof Pane && !(node instanceof ScrollPane)) {
            String s=node.getStyle();
            if(s!=null && !s.contains("-fx-background-color: transparent") &&
                    (s.contains("-fx-background-radius") || s.contains("-fx-border-radius"))) {
                String panel=dark()?"#1e1e1e":"#ffffff";
                // Only neutral surfaces, never colored Post-Its, warnings, badges or the Pomodoro dial.
                if(s.toLowerCase(java.util.Locale.ROOT).contains("-fx-background-color: "+panel) ||
                   s.contains("-fx-background-color: -color-bg-panel") ||
                   s.contains("-fx-background-color: white") ||
                   s.contains("linear-gradient(to bottom right, " + panel) ||
                   s.contains("linear-gradient(to bottom right, -color-bg-panel")) {
                    add(node,"student-premium-card");
                    boolean sizable=s.contains("-fx-padding: 20") || s.contains("-fx-padding: 25") ||
                                    s.contains("-fx-padding: 30") || s.contains("-fx-padding: 35") ||
                                    s.contains("-fx-padding: 40");
                    if(sizable) {
                        // Style append intentionally overrides ONLY decorative properties.
                        node.setStyle(s+"; -fx-background-radius: 18; -fx-border-radius: 18;"+
                            (!s.contains("-fx-effect") ? " -fx-effect: dropshadow(three-pass-box, rgba(22,43,66,0.13), 14,0,0,4);" : ""));
                    }
                }
            }
        }
        if(node instanceof Parent) for(Node child: ((Parent)node).getChildrenUnmodifiable()) decorate(child);
    }
    private static void add(Node n,String cls) { if(!n.getStyleClass().contains(cls)) n.getStyleClass().add(cls); }
}
