package com.optem.controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.Preferences;

import com.optem.database.MockDB;
import com.optem.ui.DashboardView;

public class NotasController {

    private final String usuarioActual;
    private final Map<String, String> mapaColores = new HashMap<>();
    
    // Paleta premium de colores pastel para notas tipo "Sticky Notes"
    private final String[] coloresKeep = {"#FFADAD", "#FFD6A5", "#FDFFB6", "#CAFFBF", "#9BF6FF", "#A0C4FF", "#BDB2FF", "#FFC6FF"};
    
    private final String archivoNotas;

    public NotasController() {
        Preferences p = Preferences.userNodeForPackage(DashboardView.class);
        this.usuarioActual = p.get("LAST_ACTIVE_ACCOUNT", "default");
        
        // Se genera un archivo de notas exclusivo para este alumno
        String cuentaLimpia = this.usuarioActual.replaceAll("[^a-zA-Z0-9]", "_");
        this.archivoNotas = "notas_alumno_" + cuentaLimpia + ".dat";
    }

    public static class ApunteDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        public String id, titulo, materia, contenido, fecha, colorHex;
        public ApunteDTO(String id, String titulo, String materia, String contenido, String fecha, String colorHex) {
            this.id = id; this.titulo = titulo; this.materia = materia;
            this.contenido = contenido; this.fecha = fecha; this.colorHex = colorHex;
        }
    }

    public List<String> getMateriasDisponibles() {
        List<String> nombresMaterias = new ArrayList<>();
        
        nombresMaterias.add("Nota Personal");
        nombresMaterias.add("Recordatorio Urgente");
        
        mapaColores.put("Nota Personal", "#E0E0E0"); // Gris muy suave
        mapaColores.put("Recordatorio Urgente", "#FFB4A2"); // Salmón suave
        
        List< Map<String, String> > clases;
        try { clases = MockDB.obtenerMateriasDelAlumno(usuarioActual); }
        catch (RuntimeException ex) { // Permite abrir Notas si se pierde conexión.
            clases = java.util.Collections.emptyList();
        }
        
        int colorIndex = 0;
        if (clases != null) {
            for(Map<String, String> clase : clases) {
                String nombre = clase.get("nombre");
                nombresMaterias.add(nombre);
                if (!mapaColores.containsKey(nombre)) {
                    mapaColores.put(nombre, coloresKeep[colorIndex % coloresKeep.length]);
                    colorIndex++;
                }
            }
        }
        return nombresMaterias;
    }

    public String getColorParaMateria(String materia) {
        return mapaColores.getOrDefault(materia, "#F8F9FA");
    }

    public void guardarApunte(String titulo, String materia, String contenido) {
        String id=UUID.randomUUID().toString();
        String color=getColorParaMateria(materia);
        String fecha=java.time.LocalDate.now().toString();
        // Primero persistir localmente; no se pierde lo escrito ante un corte de red.
        com.optem.logic.OfflineStore.createOffline(usuarioActual,
            new com.optem.logic.OfflineStore.Note(id,titulo,materia,contenido,fecha,color));
        com.optem.logic.OfflineSyncService.get().checkSoon();
    }

    public void eliminarApunte(String id) {
        com.optem.logic.OfflineStore.removeOffline(usuarioActual,id);
        com.optem.logic.OfflineSyncService.get().checkSoon();
    }

    public List<ApunteDTO> cargarApuntes(){
        List<com.optem.logic.OfflineStore.Note> copia = new ArrayList<>();
        String q="SELECT id,titulo,materia,contenido,DATE(fecha_actualizacion),color_hex FROM OPTEM_NOTAS_PERSONALES WHERE correo_usuario=? ORDER BY fecha_actualizacion DESC";
        if(com.optem.logic.OfflineSyncService.get().status()==com.optem.logic.OfflineSyncService.Status.SIN_CONEXION) {
            List<ApunteDTO> almacenadas = new ArrayList<>();
            for(com.optem.logic.OfflineStore.Note n:com.optem.logic.OfflineStore.readNotes(usuarioActual))
                almacenadas.add(new ApunteDTO(n.id(),n.titulo(),n.materia(),n.contenido(),n.fecha(),n.color()));
            return almacenadas;
        }
        try(java.sql.Connection c=com.optem.database.MySQL.conectar();java.sql.PreparedStatement st=c.prepareStatement(q)){
            st.setString(1,usuarioActual);
            try(java.sql.ResultSet r=st.executeQuery()){
                while(r.next())copia.add(new com.optem.logic.OfflineStore.Note(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)));
            }
            com.optem.logic.OfflineStore.refresh(usuarioActual,copia);
        }catch(java.sql.SQLException ex){
            if(!com.optem.logic.OfflineSyncService.connectionFailure(ex))
                throw new IllegalStateException("Error al consultar notas: "+ex.getMessage(),ex);
            com.optem.logic.OfflineSyncService.get().markOffline();
        }
        List<ApunteDTO> out=new ArrayList<>();
        for(com.optem.logic.OfflineStore.Note n:com.optem.logic.OfflineStore.readNotes(usuarioActual))
            out.add(new ApunteDTO(n.id(),n.titulo(),n.materia(),n.contenido(),n.fecha(),n.color()));
        return out;
    }
}
