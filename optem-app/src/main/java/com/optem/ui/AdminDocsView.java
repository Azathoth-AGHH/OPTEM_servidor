package com.optem.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.prefs.Preferences;

import com.optem.database.AdminDocumentosDAO;
import com.optem.database.AdminDocumentosDAO.Documento;
import com.optem.database.AdminDocumentosDAO.Resumen;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/** Reemplaza las descargas y cantidades ficticias por documentos reales. */
public class AdminDocsView {
    private String colorFuerte, colorBgPanel, colorTextMain, colorTextMuted, colorBorder;
    private VBox lista;
    private Label estado;
    private Resumen ultimoResumen;

    public VBox getView() {
        leerPreferencias();
        VBox content = new VBox(20);
        content.setPadding(new Insets(30,40,30,40));
        Label titulo=new Label("📁 Documentos y Trayectorias");
        titulo.setStyle("-fx-font-size: 27px; -fx-font-weight: bold; -fx-text-fill: "+colorTextMain+";");
        Label subtitulo=new Label("Documentos reales de MySQL y reporte administrativo descargable.");
        subtitulo.setStyle("-fx-text-fill: "+colorTextMuted+";");
        Button btnGenerar=new Button("📄 Generar reporte PDF");
        Button btnActualizar=new Button("↻ Actualizar documentos");
        btnGenerar.setStyle("-fx-background-color: "+colorFuerte+"; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10 17;");
        btnActualizar.setStyle(btnGenerar.getStyle());
        btnGenerar.setOnAction(e->guardarReporte(btnGenerar));
        btnActualizar.setOnAction(e->cargar());
        Region espacio=new Region();HBox.setHgrow(espacio,Priority.ALWAYS);
        HBox cabecera=new HBox(12,new VBox(5,titulo,subtitulo),espacio,btnActualizar,btnGenerar);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        AdminPremiumVisual.header(cabecera);
        AdminPremiumVisual.primary(btnGenerar);
        AdminPremiumVisual.primary(btnActualizar);

        estado=new Label("Consultando documentos de MySQL…");
        estado.setStyle("-fx-text-fill: "+colorTextMuted+";");
        lista=new VBox(12);
        VBox panel=new VBox(15,new Label("Hojas de pago registradas"),lista);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: "+colorBgPanel+"; -fx-background-radius: 16; -fx-border-color: "+colorBorder+"; -fx-border-radius: 16;");
        AdminPremiumVisual.panel(panel);
        content.getChildren().addAll(cabecera,estado,panel);
        ScrollPane scroll=new ScrollPane(content);scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox resultado=new VBox(scroll);VBox.setVgrow(scroll,Priority.ALWAYS);
        cargar();
        return resultado;
    }

    private void cargar(){
        estado.setText("Consultando documentos de MySQL…");
        Task<Resumen> work=new Task<>(){
            @Override protected Resumen call() throws Exception {return AdminDocumentosDAO.consultar();}
        };
        work.setOnSucceeded(e->{
            ultimoResumen=work.getValue();
            lista.getChildren().clear();
            var totales=ultimoResumen.totales();
            estado.setText("Registros reales · "+totales.get("Estudiantes activos")+" estudiantes · "
                +totales.get("Docentes activos")+" docentes · "+totales.get("Grupos activos")+" grupos · "
                +totales.get("Hojas de pago PDF")+" hojas de pago");
            if(ultimoResumen.documentos().isEmpty()){
                lista.getChildren().add(new Label("Todavía no hay hojas de pago guardadas en MySQL."));
            }
            for(Documento d:ultimoResumen.documentos()){
                Label nombre=new Label(d.nombreArchivo());
                nombre.setWrapText(true);nombre.setStyle("-fx-font-weight: bold; -fx-text-fill: "+colorTextMain+";");
                Label detalle=new Label(d.correo()+" · "+d.fechaSubida());
                detalle.setStyle("-fx-font-size: 11px; -fx-text-fill: "+colorTextMuted+";");
                Region separador=new Region();HBox.setHgrow(separador,Priority.ALWAYS);
                Button btn=new Button("⬇ Descargar PDF");
                btn.setStyle("-fx-background-color: "+colorFuerte+"; -fx-text-fill: white; -fx-font-weight: bold;");
                AdminPremiumVisual.primary(btn);
                btn.setOnAction(ev->guardarDocumento(d,btn));
                HBox fila=new HBox(14,new VBox(5,nombre,detalle),separador,btn);
                fila.setAlignment(Pos.CENTER_LEFT);fila.setPadding(new Insets(14));
                fila.setStyle("-fx-background-color: #f0f6f5; -fx-background-radius: 12; -fx-border-color: #cce0d8; -fx-border-radius: 12;");
                AdminPremiumVisual.row(fila);
                lista.getChildren().add(fila);
            }
        });
        work.setOnFailed(e->{
            estado.setText("No fue posible consultar documentos: "+work.getException().getMessage());
        });
        enSegundoPlano(work,"optem-documentos-lista");
    }

    private void guardarDocumento(Documento d,Button button){
        FileChooser chooser=new FileChooser();chooser.setTitle("Guardar hoja de pago");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));
        String seguro=d.nombreArchivo()==null?"hoja_pago.pdf":d.nombreArchivo().replaceAll("[^a-zA-Z0-9._-]","_");
        chooser.setInitialFileName(seguro.toLowerCase().endsWith(".pdf")?seguro:seguro+".pdf");
        var dest=chooser.showSaveDialog(button.getScene().getWindow());if(dest==null)return;
        button.setDisable(true);
        Task<Void> work=new Task<>(){
            @Override protected Void call() throws Exception {
                byte[] pdf=AdminDocumentosDAO.leerDocumento(d.correo());
                Files.write(dest.toPath(),pdf);return null;
            }
        };
        work.setOnSucceeded(e->{button.setDisable(false);
            new OptemAlert(Alert.AlertType.INFORMATION,"Documento descargado correctamente.").showAndWait();});
        work.setOnFailed(e->{button.setDisable(false);
            new OptemAlert(Alert.AlertType.ERROR,"No se pudo descargar: "+work.getException().getMessage()).showAndWait();});
        enSegundoPlano(work,"optem-documentos-descarga");
    }

    private void guardarReporte(Button button){
        FileChooser chooser=new FileChooser();chooser.setTitle("Guardar reporte de OPTEM");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF","*.pdf"));
        chooser.setInitialFileName("Reporte_OPTEM_"+java.time.LocalDate.now()+".pdf");
        var dest=chooser.showSaveDialog(button.getScene().getWindow());if(dest==null)return;
        button.setDisable(true);
        Task<Void> work=new Task<>(){
            @Override protected Void call() throws Exception {
                Resumen resumen=AdminDocumentosDAO.consultar();
                generarPdf(dest.toPath(),resumen.totales());return null;
            }
        };
        work.setOnSucceeded(e->{button.setDisable(false);
            new OptemAlert(Alert.AlertType.INFORMATION,"Reporte PDF generado correctamente.").showAndWait();});
        work.setOnFailed(e->{button.setDisable(false);
            new OptemAlert(Alert.AlertType.ERROR,"No se pudo generar el reporte: "+work.getException().getMessage()).showAndWait();});
        enSegundoPlano(work,"optem-reportes-pdf");
    }

    private static void generarPdf(Path destino,Map<String,Long> datos) throws IOException {
        try(PDDocument pdf=new PDDocument()){
            PDPage pagina=new PDPage(PDRectangle.A4);pdf.addPage(pagina);
            try(PDPageContentStream out=new PDPageContentStream(pdf,pagina)){
                float y=775;
                textoPdf(out,"OPTEM - Reporte administrativo",17,PDType1Font.HELVETICA_BOLD,50,y);y-=35;
                textoPdf(out,"Generado: "+LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),11,PDType1Font.HELVETICA,50,y);
                y-=45;
                for(var e:datos.entrySet()){
                    textoPdf(out,e.getKey()+":  "+e.getValue(),12,PDType1Font.HELVETICA,55,y);
                    y-=30;
                }
                textoPdf(out,"Datos consultados desde MySQL. No incluye archivos ni credenciales.",10,PDType1Font.HELVETICA,50,65);
            }
            pdf.save(destino.toFile());
        }
    }

    private static void textoPdf(PDPageContentStream out,String contenido,int tamanio,PDType1Font fuente,float x,float y) throws IOException {
        String limpio=Normalizer.normalize(contenido,Normalizer.Form.NFD)
            .replaceAll("\\p{M}","").replaceAll("[^\\x20-\\x7E]","?");
        out.beginText();out.setFont(fuente,tamanio);out.newLineAtOffset(x,y);out.showText(limpio);out.endText();
    }

    private static void enSegundoPlano(Task<?> work,String name){
        Thread hilo=new Thread(work,name);hilo.setDaemon(true);hilo.start();
    }
    private void leerPreferencias(){
        Preferences prefs=Preferences.userNodeForPackage(DashboardView.class);
        String correo=prefs.get("LAST_ACTIVE_ACCOUNT","");
        boolean oscuro=prefs.getBoolean("PREF_DARK_MODE_"+correo,false);
        colorFuerte=prefs.get("PREF_ACCENT_COLOR_"+correo,"#3D5A4D");
        colorBgPanel=oscuro?"#1e1e1e":"#ffffff";
        colorTextMain=oscuro?"#f8f9fa":"#1e293b";
        colorTextMuted=oscuro?"#94a3b8":"#64748b";
        colorBorder=oscuro?"#333333":"#d6e5df";
    }
}
