package com.optem.ui;

import com.optem.controllers.TareasMaestroController;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipFile;

/** Revisión integrada de entregas: visor PDF/DOCX y calificación en la misma ventana.
 *  Ningún archivo se abre en un navegador ni se envía a un servicio externo.
 */
public class RevisionEntregaView {
    private final TareasMaestroController controller;
    private final List<Map<String,String>> entregas;
    private final String tituloTarea, grupo;
    private final Runnable onGuardado;
    private Stage ventana;
    private Label alumno, archivo, posicionAlumno, paginaInfo, estado;
    private TextField puntos;
    private TextArea comentarios;
    private ImageView imagen;
    private TextArea texto;
    private StackPane areaDocumento;
    private Button anteriorAlumno, siguienteAlumno, anteriorPagina, siguientePagina, enviar;
    private int actual=0, pagina=0, paginas=0;
    private int generacion=0;
    private String rutaActual="";
    // Mantiene el archivo temporal por entrega durante esta revisión; evita descargar
    // el mismo BLOB de MySQL cada vez que el docente cambia de página.
    private final java.util.concurrent.ConcurrentMap<String,String> cacheArchivos=
        new java.util.concurrent.ConcurrentHashMap<>();
    private boolean guardando=false;

    private static final String BORDE="-student-border", TEXTO="-student-text";

    public RevisionEntregaView(TareasMaestroController controller,List<Map<String,String>> entregas,
                               String tituloTarea,String grupo,Runnable onGuardado) {
        this.controller=controller;this.entregas=entregas;
        this.tituloTarea=tituloTarea;this.grupo=grupo;this.onGuardado=onGuardado;
    }

    public void mostrar(Window owner,int indice){
        if(entregas.isEmpty())return;
        ventana=new Stage();ventana.initOwner(owner);ventana.initModality(Modality.APPLICATION_MODAL);
        ventana.setTitle("Revisar entrega - OPTEM");
        BorderPane principal=new BorderPane();principal.setStyle("-fx-background-color: linear-gradient(to bottom right, -student-backdrop, -student-surface-alt);");

        VBox titulo=new VBox(3);
        Label h1=new Label("Revisión de trabajos");
        h1.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: white;");
        Label h2=new Label(tituloTarea+" · "+grupo);
        h2.setStyle("-fx-text-fill: rgba(255,255,255,.85); -fx-font-size: 12px;");
        titulo.getChildren().addAll(h1,h2);
        HBox cabecera=new HBox(titulo);cabecera.setPadding(new Insets(14,22,14,22));
        cabecera.setStyle("-fx-background-color: linear-gradient(to bottom right, derive(" + StudentPremiumTheme.strongAccent() + ", 14%), " + StudentPremiumTheme.strongAccent() + " 55%, derive(" + StudentPremiumTheme.strongAccent() + ", -29%)); -fx-border-color: -student-border; -fx-border-width: 0 0 2 0;");
        principal.setTop(cabecera);

        VBox panelPDF=new VBox(10);panelPDF.setPadding(new Insets(16));
        VBox.setVgrow(panelPDF,Priority.ALWAYS);
        imagen=new ImageView();imagen.setPreserveRatio(true);imagen.setFitWidth(690);
        texto=new TextArea();texto.setWrapText(true);texto.setEditable(false);
        texto.setStyle("-fx-font-family: 'Segoe UI'; -fx-font-size: 14px; -fx-control-inner-background: -student-surface; -fx-text-fill: -student-text;");
        areaDocumento=new StackPane();areaDocumento.setAlignment(Pos.TOP_CENTER);
        areaDocumento.setStyle("-fx-background-color: -student-surface-alt; -fx-padding: 16; -fx-background-radius: 12;");
        ScrollPane lienzo=new ScrollPane(areaDocumento);lienzo.setFitToWidth(true);
        lienzo.setStyle("-fx-background: -student-surface-alt; -fx-border-color: "+BORDE+"; -fx-border-radius: 12;");
        lienzo.viewportBoundsProperty().addListener((obs,antes,actual)->
            imagen.setFitWidth(Math.max(280,actual.getWidth()-48)));
        VBox.setVgrow(lienzo,Priority.ALWAYS);
        archivo=new Label("Documento");archivo.setStyle("-fx-font-weight: bold; -fx-text-fill: "+TEXTO+";");
        anteriorPagina=botonSecundario("‹ Página");siguientePagina=botonSecundario("Página ›");
        paginaInfo=new Label("Página -");paginaInfo.setStyle("-fx-text-fill: -student-muted;");
        anteriorPagina.setOnAction(e->{pagina--;renderizarActual();});
        siguientePagina.setOnAction(e->{pagina++;renderizarActual();});
        HBox controlesPDF=new HBox(12,anteriorPagina,paginaInfo,siguientePagina);
        controlesPDF.setAlignment(Pos.CENTER);
        panelPDF.getChildren().addAll(archivo,lienzo,controlesPDF);
        HBox.setHgrow(panelPDF,Priority.ALWAYS);

        VBox lateral=new VBox(13);lateral.setPrefWidth(300);lateral.setMinWidth(285);
        lateral.setPadding(new Insets(22));
        lateral.setStyle("-fx-background-color: linear-gradient(to bottom right, -student-surface, -student-surface-alt); -fx-border-color: "+BORDE+"; -fx-border-width: 0 0 0 1;");
        Label revisor=new Label("Trabajo del estudiante");
        revisor.setStyle("-fx-font-weight: bold; -fx-text-fill: -student-text;");
        alumno=new Label();alumno.setWrapText(true);
        alumno.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: "+TEXTO+";");
        posicionAlumno=new Label();posicionAlumno.setStyle("-fx-text-fill: -student-muted;");
        anteriorAlumno=botonSecundario("‹ Anterior");
        siguienteAlumno=botonSecundario("Siguiente ›");
        anteriorAlumno.setOnAction(e->cambiarAlumno(actual-1));
        siguienteAlumno.setOnAction(e->cambiarAlumno(actual+1));
        HBox navegacion=new HBox(8,anteriorAlumno,siguienteAlumno);
        Label etiquetaComentario=new Label("Comentarios al estudiante");
        etiquetaComentario.setStyle("-fx-font-weight: bold; -fx-text-fill: "+TEXTO+";");
        comentarios=new TextArea();comentarios.setPrefRowCount(5);comentarios.setWrapText(true);
        comentarios.setPromptText("Escribe observaciones o retroalimentación...");
        Label etiquetaNota=new Label("Puntos (0 a 10)");
        etiquetaNota.setStyle("-fx-font-weight: bold; -fx-text-fill: "+TEXTO+";");
        puntos=new TextField();puntos.setPromptText("Ejemplo: 8.5");
        enviar=new Button("Enviar calificación");
        enviar.setMaxWidth(Double.MAX_VALUE);
        enviar.setStyle("-fx-background-color: linear-gradient(to bottom right, derive(" + StudentPremiumTheme.strongAccent() + ", 15%), " + StudentPremiumTheme.strongAccent() + " 55%, derive(" + StudentPremiumTheme.strongAccent() + ", -22%)); -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 12 18; -fx-background-radius: 11;");
        enviar.setOnAction(e->guardarCalificacion());
        estado=new Label("");estado.setWrapText(true);
        estado.setStyle("-fx-text-fill: -student-muted;");
        lateral.getChildren().addAll(revisor,alumno,posicionAlumno,navegacion,new Separator(),
            etiquetaComentario,comentarios,etiquetaNota,puntos,enviar,estado);
        HBox centro=new HBox(panelPDF,lateral);
        HBox.setHgrow(panelPDF,Priority.ALWAYS);
        principal.setCenter(centro);
        Scene escena=new Scene(principal,1120,760); OptemLook.install(escena);
        ventana.setScene(escena); OptemDialogStyle.apply(ventana);
        ventana.setOnCloseRequest(e->generacion++);
        cambiarAlumno(Math.max(0,Math.min(indice,entregas.size()-1)));
        ventana.showAndWait();
    }

    private Button botonSecundario(String label){
        Button b=new Button(label);
        b.setStyle("-fx-background-color: linear-gradient(to bottom, -student-surface, -student-surface-alt); -fx-text-fill: "+TEXTO+"; -fx-border-color: "+BORDE+"; -fx-border-radius: 10; -fx-background-radius: 10; -fx-cursor: hand;");
        return b;
    }

    private void cambiarAlumno(int indice){
        if(guardando || indice<0 || indice>=entregas.size())return;
        actual=indice;pagina=0;paginas=0;rutaActual="";
        Map<String,String> e=entregas.get(actual);
        alumno.setText(e.getOrDefault("alumno","Estudiante"));
        posicionAlumno.setText("Entrega "+(actual+1)+" de "+entregas.size());
        archivo.setText(e.getOrDefault("archivo_nombre","Documento"));
        // Calificaciones ya guardadas en MySQL, convertidas a escala 10 si estaban en 100.
        String cal=e.getOrDefault("calificacion","");
        if(!cal.isBlank()){
            try{
                double valor=Double.parseDouble(cal);
                if("100".equals(e.get("escala")))valor/=10.0;
                puntos.setText(String.format(Locale.US,"%.2f",valor));
            }catch(NumberFormatException ex){puntos.setText("");}
        }else puntos.clear();
        comentarios.setText(e.getOrDefault("comentario",""));
        estado.setText("");
        anteriorAlumno.setDisable(actual==0);
        siguienteAlumno.setDisable(actual==entregas.size()-1);
        renderizarActual();
    }

    private void renderizarActual(){
        pagina=Math.max(0,pagina);
        paginaInfo.setText("Cargando documento...");
        areaDocumento.getChildren().setAll(new Label("Cargando vista previa..."));
        anteriorPagina.setDisable(true);siguientePagina.setDisable(true);
        int numero=++generacion;
        String id=entregas.get(actual).get("id_entrega");
        int paginaSolicitada=pagina;
        Task<Preview> trabajo=new Task<>(){
            @Override protected Preview call() throws Exception{
                String ruta=cacheArchivos.computeIfAbsent(id,
                    key->controller.descargarEntrega(key)); // Consulta con permiso del docente.
                if(ruta==null || ruta.isBlank())return Preview.mensaje("La entrega no contiene archivo.");
                Path path=Path.of(ruta);String nombre=path.getFileName().toString().toLowerCase(Locale.ROOT);
                if(nombre.endsWith(".pdf")){
                    try(PDDocument pdf=PDDocument.load(path.toFile())){
                        int cantidad=pdf.getNumberOfPages();
                        if(cantidad==0)return Preview.mensaje("El PDF está vacío.");
                        int indice=Math.min(paginaSolicitada,cantidad-1);
                        var buff=new PDFRenderer(pdf).renderImageWithDPI(indice,110,ImageType.RGB);
                        ByteArrayOutputStream salida=new ByteArrayOutputStream();
                        ImageIO.write(buff,"png",salida);
                        Image img=new Image(new ByteArrayInputStream(salida.toByteArray()));
                        return Preview.pdf(img,cantidad,indice);
                    }
                }
                if(nombre.endsWith(".docx"))return Preview.texto(leerDocx(path));
                // Imágenes del alumnado: JPG/JPEG, PNG, GIF, BMP, TIFF y formatos
                // adicionales instalados como lectores ImageIO. Se valida el tamaño.
                String ext = nombre.contains(".") ? nombre.substring(nombre.lastIndexOf('.') + 1) : "";
                if(java.util.Set.of("jpg","jpeg","png","gif","bmp","tif","tiff","wbmp","webp").contains(ext)){
                    try(var iis=ImageIO.createImageInputStream(path.toFile())){
                        if(iis==null)return Preview.mensaje("No se pudo leer la imagen.");
                        var readers=ImageIO.getImageReaders(iis);
                        if(!readers.hasNext())return Preview.mensaje("Este formato de imagen requiere un lector compatible: "+ext.toUpperCase(Locale.ROOT));
                        var reader=readers.next();
                        try{
                            reader.setInput(iis,true,true);
                            long pixels=(long)reader.getWidth(0)*reader.getHeight(0);
                            if(pixels>30_000_000L)return Preview.mensaje("La imagen es demasiado grande para previsualizarla (más de 30 megapíxeles).");
                            var original=reader.read(0);
                            ByteArrayOutputStream salida=new ByteArrayOutputStream();
                            if(!ImageIO.write(original,"png",salida))return Preview.mensaje("No se pudo convertir la imagen para visualizarla.");
                            Image img=new Image(new ByteArrayInputStream(salida.toByteArray()));
                            return Preview.pdf(img,1,0);
                        }finally{reader.dispose();}
                    }
                }
                if(java.util.Set.of("txt","csv","json","xml","md","log","java","py","sql","html","htm","css","js","yml","yaml").contains(ext)){
                    long size=Files.size(path);
                    if(size>1_000_000L)return Preview.mensaje("El archivo de texto excede 1 MB para la vista previa.");
                    String contenido=Files.readString(path,StandardCharsets.UTF_8);
                    return Preview.texto(contenido.isBlank()?"El archivo no contiene texto.":contenido);
                }
                return Preview.mensaje("No hay vista previa interna para este tipo de archivo ("+ext+"). Se conserva la entrega y su calificación.");
            }
        };
        trabajo.setOnSucceeded(e->{
            if(numero!=generacion || ventana==null)return;
            Preview p=trabajo.getValue();paginas=p.total;pagina=p.indice;
            if(p.imagen!=null){imagen.setImage(p.imagen);areaDocumento.getChildren().setAll(imagen);}
            else {texto.setText(p.texto);areaDocumento.getChildren().setAll(texto);}
            paginaInfo.setText(p.total>0?"Página "+(pagina+1)+" de "+p.total:"Vista previa");
            anteriorPagina.setDisable(p.total<=1 || pagina==0);
            siguientePagina.setDisable(p.total<=1 || pagina>=p.total-1);
        });
        trabajo.setOnFailed(e->{
            if(numero!=generacion)return;
            areaDocumento.getChildren().setAll(new Label("No se pudo mostrar el archivo: "+trabajo.getException().getMessage()));
            paginaInfo.setText("Vista previa no disponible");
        });
        Thread hilo=new Thread(trabajo,"optem-render-documento");hilo.setDaemon(true);hilo.start();
    }

    private void guardarCalificacion(){
        final double nota;
        try{
            nota=Double.parseDouble(puntos.getText().trim().replace(',','.'));
            if(!Double.isFinite(nota)||nota<0 || nota>10)throw new NumberFormatException();
        }catch(NumberFormatException e){
            new OptemAlert(Alert.AlertType.ERROR,"La calificación debe estar entre 0 y 10.").showAndWait();return;
        }
        String id=entregas.get(actual).get("id_entrega"),comentario=comentarios.getText();
        guardando=true;enviar.setDisable(true);anteriorAlumno.setDisable(true);siguienteAlumno.setDisable(true);
        Task<Void> guardar=new Task<>(){
            @Override protected Void call(){controller.calificar(id,nota,10,comentario);return null;}
        };
        guardar.setOnSucceeded(e->{
            guardando=false;enviar.setDisable(false);
            entregas.get(actual).put("calificacion",Double.toString(nota));
            entregas.get(actual).put("escala","10");entregas.get(actual).put("comentario",comentario);
            anteriorAlumno.setDisable(actual==0);siguienteAlumno.setDisable(actual==entregas.size()-1);
            estado.setText("Calificación enviada y guardada en MySQL.");
            if(onGuardado!=null)onGuardado.run();
            new OptemAlert(Alert.AlertType.INFORMATION,"calificacion enviada").showAndWait();
        });
        guardar.setOnFailed(e->{
            guardando=false;enviar.setDisable(false);
            anteriorAlumno.setDisable(actual==0);siguienteAlumno.setDisable(actual==entregas.size()-1);
            Throwable err=guardar.getException();
            new OptemAlert(Alert.AlertType.ERROR,"No se pudo enviar la calificación: "+(err==null?"Error desconocido":err.getMessage())).showAndWait();
        });
        Thread hilo=new Thread(guardar,"optem-calificar-entrega");hilo.setDaemon(true);hilo.start();
    }

    private static String leerDocx(Path archivo) throws Exception{
        try(ZipFile z=new ZipFile(archivo.toFile())){
            var ent=z.getEntry("word/document.xml");
            if(ent==null)return "El documento DOCX no contiene texto legible.";
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            var xml=factory.newDocumentBuilder().parse(z.getInputStream(ent));
            var parrafos=xml.getElementsByTagNameNS("*","p");
            StringBuilder sb=new StringBuilder();
            for(int i=0;i<parrafos.getLength();i++){
                var partes=((org.w3c.dom.Element)parrafos.item(i)).getElementsByTagNameNS("*","t");
                for(int j=0;j<partes.getLength();j++)sb.append(partes.item(j).getTextContent());
                sb.append('\n');
                if(sb.length()>250_000)break;
            }
            return sb.length()==0?"No hay texto en este DOCX.":sb.toString();
        }
    }
    private static final class Preview{
        final Image imagen;final String texto;final int total,indice;
        private Preview(Image imagen,String texto,int total,int indice){
            this.imagen=imagen;this.texto=texto;this.total=total;this.indice=indice;
        }
        static Preview pdf(Image imagen,int n,int i){return new Preview(imagen,"",n,i);}
        static Preview texto(String s){return new Preview(null,s,0,0);}
        static Preview mensaje(String s){return texto(s);}
    }
}
