package com.optem.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.layout.Region;

/**
 * Capa de presentación específica de las vistas administrativas.
 * Reutiliza los colores buscados -student-* del sistema premium compartido.
 * No toca eventos, modelos, campos, SQL ni preferencias.
 */
public final class AdminPremiumVisual {
    private AdminPremiumVisual() {}

    private static void klass(Region region, String name) {
        if (region != null && !region.getStyleClass().contains(name))
            region.getStyleClass().add(name);
    }
    private static void paint(Region region, String tag, String style) {
        if (region == null || Boolean.TRUE.equals(region.getProperties().get(tag))) return;
        region.getProperties().put(tag, Boolean.TRUE);
        region.setStyle(region.getStyle() + ";" + style);
    }
    public static void header(Region region) {
        klass(region, "admin-premium-header");
        paint(region, "admin-p-header", "-fx-padding: 18 22;"+
            "-fx-background-color: linear-gradient(to bottom right, -student-surface 0%, -student-accent-soft 100%);"+
            "-fx-background-radius: 20; -fx-border-radius: 20; -fx-border-width: 1;"+
            "-fx-border-color: -student-border;"+
            "-fx-effect: dropshadow(three-pass-box, rgba(25,48,72,.14), 14,0,0,4);");
    }
    public static void panel(Region region) {
        klass(region, "admin-premium-panel");
        paint(region, "admin-p-panel", "-fx-background-color: linear-gradient(to bottom right, -student-surface 0%, -student-surface-alt 100%);"+
            "-fx-background-radius: 19; -fx-border-radius: 19;"+
            "-fx-border-color: -student-border; -fx-border-width: 1;"+
            "-fx-effect: dropshadow(three-pass-box, rgba(25,48,72,.13), 14,0,0,4);");
    }
    public static void row(Region region) {
        klass(region, "admin-premium-row");
        paint(region, "admin-p-row", "-fx-background-color: linear-gradient(to bottom right, -student-surface-alt, -student-surface);"+
            "-fx-background-radius: 13; -fx-border-radius: 13;"+
            "-fx-border-color: -student-border; -fx-border-width: 1;"+
            "-fx-effect: dropshadow(three-pass-box, rgba(30,52,72,.09), 8,0,0,2);");
    }
    public static void primary(Button btn) {
        klass(btn, "admin-premium-primary");
        paint(btn, "admin-p-primary", "-fx-background-color: linear-gradient(to bottom right, " +
            "derive(-student-accent-deep,24%), -student-accent-deep 52%, derive(-student-accent-deep,-16%));"+
            "-fx-text-fill: white; -fx-background-radius: 11; -fx-border-radius: 11;"+
            "-fx-effect: dropshadow(three-pass-box, rgba(24,50,68,.23), 9,0,0,3);");
    }
    public static void secondary(Button btn) {
        klass(btn, "admin-premium-secondary");
        paint(btn, "admin-p-secondary", "-fx-background-color: linear-gradient(to bottom, -student-surface, -student-surface-alt);"+
            "-fx-text-fill: -student-text; -fx-background-radius: 11; -fx-border-radius: 11;"+
            "-fx-border-color: -student-border; -fx-border-width: 1;");
    }
    public static void field(Control field) {
        klass(field, "admin-premium-field");
        // No se tocan validadores, habilitación ni contenido del control.
    }
}
