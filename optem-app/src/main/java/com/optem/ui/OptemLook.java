package com.optem.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Spinner;
import javafx.scene.layout.Pane;

/** Apariencia centralizada V6: solo modifica propiedades visuales de JavaFX. */
public final class OptemLook {
    private static final String CSS = "/css/optem-v6.css";
    private static final String TAG = "optem-v6-decorated";
    private static final Pattern BG = Pattern.compile("(?i)(-fx-background-color\\s*:\\s*)(#[0-9a-f]{6}|-color-bg-panel|white)(\\s*;)");
    private static final Pattern STRONG_BUTTON = Pattern.compile("(?i)(-fx-background-color\\s*:\\s*)(#[0-9a-f]{6}|-color-accent)(\\s*;)");
    private OptemLook() { }

    public static void install(Scene scene) {
        if (scene == null) return;
        java.net.URL url = OptemLook.class.getResource(CSS);
        if (url != null && !scene.getStylesheets().contains(url.toExternalForm())) {
            scene.getStylesheets().add(url.toExternalForm());
        }
        if (!scene.getRoot().getStyleClass().contains("optem-v6"))
            scene.getRoot().getStyleClass().add("optem-v6");
        decorate(scene.getRoot());
    }

    /** Se invoca al abrir una vista; no instala listeners pesados sobre miles de controles. */
    public static void decorate(Node node) {
        if (node == null || Boolean.TRUE.equals(node.getProperties().get(TAG))) return;
        node.getProperties().put(TAG, Boolean.TRUE);
        if (node instanceof ButtonBase) {
            node.getStyleClass().add("optem-v6-button");
            String style = node.getStyle();
            if (style != null && style.contains("-fx-background-color") && style.contains("-fx-text-fill: white")) {
                Matcher match = STRONG_BUTTON.matcher(style);
                if (match.find() && !style.contains("linear-gradient")) {
                    String c = match.group(2);
                    node.setStyle(match.replaceFirst(Matcher.quoteReplacement(match.group(1)
                        + "linear-gradient(to bottom right, derive(" + c + ", 17%), " + c
                        + " 52%, derive(" + c + ", -17%))" + match.group(3))));
                }
            }
        }
        if (node instanceof ComboBoxBase || node instanceof Spinner)
            node.getStyleClass().add("optem-v6-select");
        if (node instanceof TextInputControl)
            node.getStyleClass().add("optem-v6-field");
        if (node instanceof Pane && !(node instanceof ScrollPane)) {
            String style = node.getStyle();
            if (style != null && (style.contains("-fx-background-radius") || style.contains("-fx-border-radius"))
                    && !style.contains("linear-gradient") && !style.contains("-fx-background-color: transparent")
                    && !style.contains("-fx-background-color: -color-bg-base")) {
                Matcher match = BG.matcher(style);
                if (match.find() && (!match.group(2).startsWith("#")
                        || isLight(match.group(2)))) {
                    node.getStyleClass().add("optem-v6-panel");
                    String base = match.group(2);
                    String fill = "linear-gradient(to bottom right, " + base + " 0%, derive(" + base + ", -3%) 100%)";
                    String changed = match.replaceFirst(Matcher.quoteReplacement(match.group(1) + fill + match.group(3)));
                    // Solo refuerzo de borde: no quita colores ni controles funcionales.
                    if (!changed.contains("-fx-border-color")) changed += " -fx-border-color: rgba(32, 118, 87, .12); -fx-border-width: 1;";
                    if (!changed.contains("-fx-effect") && (style.contains("-fx-padding: 20")
                            || style.contains("-fx-padding: 25") || style.contains("-fx-padding: 30")))
                        changed += " -fx-effect: dropshadow(three-pass-box, rgba(22, 43, 67, .11), 13, 0, 0, 4);";
                    node.setStyle(changed);
                }
            }
        }
        if (node instanceof Parent) {
            for (Node child : ((Parent) node).getChildrenUnmodifiable()) decorate(child);
        }
    }

    private static boolean isLight(String color) {
        if (color == null || color.length() != 7) return false;
        try {
            int r = Integer.parseInt(color.substring(1, 3), 16);
            int g = Integer.parseInt(color.substring(3, 5), 16);
            int b = Integer.parseInt(color.substring(5, 7), 16);
            return r > 215 && g > 215 && b > 215;
        } catch (NumberFormatException ignored) { return false; }
    }
}
