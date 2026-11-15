package com.optem.ui;

import com.optem.controllers.ReinscripcionController;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.util.prefs.Preferences;

/** Visor PDF de reinscripción, integrado a JavaFX y limitado a la cuenta activa.
 * Recupera el BLOB desde MySQL fuera del hilo de JavaFX y no abre navegador.
 */
public final class HojaPagoView {
    private record Pagina(Image imagen,int numero,int total) {}
    private Stage stage;
    private ImageView imagen;
    private Label estado;
    private Button anterior,siguiente,guardar;
    private volatile byte[] pdf;
    private int numero=0,total=0,generacion=0;
    private ReinscripcionController controller;
    private String correo;

    public void mostrar(Window owner,ReinscripcionController controller,String correo){
        this.controller=controller;this.correo=correo;
        Preferences prefs=Preferences.userNodeForPackage(DashboardView.class);
        boolean oscuro=prefs.getBoolean("PREF_DARK_MODE_"+correo,false);
        String principal=prefs.get("PREF_ACCENT_COLOR_"+correo,"#3D5A4D");
        String fondo=oscuro?"#192329":"#eff5f6";
        String texto=oscuro?"#f2f6fa":"#182c38";
        String panel=oscuro?"#25343b":"#ffffff";

        stage=new Stage();stage.setTitle("Hoja de pago - OPTEM");
        if(owner!=null)stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        BorderPane raiz=new BorderPane();
        raiz.setStyle("-fx-background-color: "+fondo+";");
        Label titulo=new Label("Hoja de pago de reinscripción");
        titulo.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: white;");
        HBox cabecera=new HBox(titulo);cabecera.setPadding(new Insets(18,24,18,24));
        cabecera.setStyle("-fx-background-color: linear-gradient(to bottom right, "+principal+", #123a52); -fx-background-radius: 12 12 0 0;");
        raiz.setTop(cabecera);

        imagen=new ImageView();imagen.setPreserveRatio(true);imagen.setFitWidth(730);
        VBox pagina=new VBox(imagen);pagina.setAlignment(Pos.TOP_CENTER);
        pagina.setPadding(new Insets(18));
        pagina.setStyle("-fx-background-color: "+panel+";");
        ScrollPane desplazar=new ScrollPane(pagina);
        desplazar.setFitToWidth(true);
        desplazar.viewportBoundsProperty().addListener((obs,prev,act)->imagen.setFitWidth(Math.max(270,act.getWidth()-40)));
        raiz.setCenter(desplazar);

        anterior=new Button("‹ Página anterior");
        siguiente=new Button("Página siguiente ›");
        guardar=new Button("Guardar PDF");
        estado=new Label("Cargando documento desde MySQL...");
        estado.setStyle("-fx-text-fill: "+texto+";");
        anterior.setDisable(true);siguiente.setDisable(true);guardar.setDisable(true);
        for(Button b:new Button[]{anterior,siguiente,guardar}){
            b.setStyle("-fx-background-color: "+principal+"; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 9; -fx-padding: 9 16; -fx-cursor: hand;");
        }
        anterior.setOnAction(e->cargar(numero-1));
        siguiente.setOnAction(e->cargar(numero+1));
        guardar.setOnAction(e->guardarPdf());
        HBox barra=new HBox(12,anterior,estado,siguiente,guardar);
        barra.setAlignment(Pos.CENTER);barra.setPadding(new Insets(14));
        barra.setStyle("-fx-background-color: "+panel+";");
        raiz.setBottom(barra);
        Scene escena=new Scene(raiz,990,740);
        OptemLook.install(escena);
        stage.setScene(escena);OptemDialogStyle.apply(stage);
        stage.setOnHidden(e->{generacion++;pdf=null;});
        stage.show();
        cargar(0);
    }

    private void cargar(int indice){
        if(indice<0 || (total>0 && indice>=total))return;
        int version=++generacion;
        anterior.setDisable(true);siguiente.setDisable(true);
        estado.setText("Cargando página...");
        Task<Pagina> tarea=new Task<>(){
            @Override protected Pagina call() throws Exception{
                byte[] datos=pdf;
                if(datos==null){datos=controller.obtenerHojaDePago(correo);pdf=datos;}
                try(PDDocument documento=PDDocument.load(datos)){
                    int cantidad=documento.getNumberOfPages();
                    if(cantidad<1)throw new IllegalStateException("El PDF no tiene páginas.");
                    int pagina=Math.min(indice,cantidad-1);
                    var buffer=new PDFRenderer(documento).renderImageWithDPI(pagina,110,ImageType.RGB);
                    ByteArrayOutputStream salida=new ByteArrayOutputStream();
                    ImageIO.write(buffer,"png",salida);
                    return new Pagina(new Image(new ByteArrayInputStream(salida.toByteArray())),pagina,cantidad);
                }
            }
        };
        tarea.setOnSucceeded(e->{
            if(version!=generacion || !stage.isShowing())return;
            Pagina p=tarea.getValue();numero=p.numero();total=p.total();
            imagen.setImage(p.imagen());
            estado.setText("Página "+(numero+1)+" de "+total);
            anterior.setDisable(numero==0);siguiente.setDisable(numero>=total-1);guardar.setDisable(false);
        });
        tarea.setOnFailed(e->{
            if(version!=generacion || !stage.isShowing())return;
            Throwable ex=tarea.getException();
            estado.setText("No fue posible abrir el documento.");
            new OptemAlert(Alert.AlertType.ERROR,
                "No se pudo visualizar la hoja de pago: "+(ex==null?"Error desconocido":ex.getMessage())).showAndWait();
        });
        Thread hilo=new Thread(tarea,"optem-pdf-reinscripcion");hilo.setDaemon(true);hilo.start();
    }

    private void guardarPdf(){
        if(pdf==null)return;
        FileChooser fc=new FileChooser();fc.setTitle("Guardar hoja de pago");
        fc.setInitialFileName("hoja_pago_optem.pdf");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF","*.pdf"));
        var archivo=fc.showSaveDialog(stage);
        if(archivo==null)return;
        try{
            Files.write(archivo.toPath(),pdf);
            new OptemAlert(Alert.AlertType.INFORMATION,"Hoja de pago guardada correctamente.").showAndWait();
        }catch(Exception ex){
            new OptemAlert(Alert.AlertType.ERROR,"No se pudo guardar el PDF: "+ex.getMessage()).showAndWait();
        }
    }
}
