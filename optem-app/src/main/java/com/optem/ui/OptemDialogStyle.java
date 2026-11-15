package com.optem.ui;

import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;

/** Tema V6 para dialogos JavaFX y ventanas secundarias, sin cambiar la logica. */
public final class OptemDialogStyle {
    private OptemDialogStyle() {}

    public static void apply(Dialog<?> dialog) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        var css = OptemDialogStyle.class.getResource("/css/optem-v6.css");
        if (css != null && !pane.getStylesheets().contains(css.toExternalForm()))
            pane.getStylesheets().add(css.toExternalForm());
        if (!pane.getStyleClass().contains("optem-v6-dialog"))
            pane.getStyleClass().add("optem-v6-dialog");
        if (!pane.getStyleClass().contains("optem-v6"))
            pane.getStyleClass().add("optem-v6");
        StudentPremiumTheme.install(dialog);
    }

    public static void apply(Stage window) {
        if (window == null) return;
        Scene scene = window.getScene();
        if (scene == null) return;
        OptemLook.install(scene);
        StudentPremiumTheme.install(scene);
        if (!scene.getRoot().getStyleClass().contains("optem-v6-modal-root"))
            scene.getRoot().getStyleClass().add("optem-v6-modal-root");
    }
}
