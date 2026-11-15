package com.optem.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;

/** Avisos visualmente consistentes en todas las pantallas V6. */
public class OptemAlert extends Alert {
    public OptemAlert(AlertType type) {
        super(type);
        apply();
    }
    public OptemAlert(AlertType type, String contentText, ButtonType... buttons) {
        super(type, contentText, buttons);
        apply();
    }
    private void apply() {
        OptemDialogStyle.apply(this);
        final String titulo;
        final String simbolo;
        switch (getAlertType()) {
            case ERROR -> { titulo="No se pudo completar"; simbolo="✕"; }
            case WARNING -> { titulo="Atención"; simbolo="!"; }
            case CONFIRMATION -> { titulo="Confirmar acción"; simbolo="?"; }
            case INFORMATION -> { titulo="OPTEM · Información"; simbolo="✓"; }
            default -> { titulo="OPTEM"; simbolo="i"; }
        }
        setTitle(titulo);
        setHeaderText(titulo);
        Label icono = new Label(simbolo);
        icono.getStyleClass().add("optem-v6-alert-icon");
        if (getAlertType()==AlertType.ERROR) icono.getStyleClass().add("optem-error-icon");
        else if (getAlertType()==AlertType.WARNING) icono.getStyleClass().add("optem-warning-icon");
        getDialogPane().setGraphic(icono);
    }
}
