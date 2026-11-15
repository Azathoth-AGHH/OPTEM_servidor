package com.optem.controllers;

import com.optem.database.MockDB;
import com.optem.database.MySQL;
import java.sql.*;
import java.util.*;

/** Consultas de pantalla administrativa agrupadas, sin alterar el controlador de horarios. */
public class SolicitudesAgrupadasDAO {
    /** Solo agrupa la vista administrativa: los registros originales siguen por materia en MySQL. */
    public List<Map<String,String>> obtenerSolicitudesAgrupadas(){
        Map<String,Map<String,String>> grupos=new LinkedHashMap<>();
        String sql="SELECT s.correo_estudiante,s.id_periodo,u.nombre,u.apellidos,"+
            "p.nombre_periodo,s.estado,m.nombre_materia,g.nombre_grupo " +
            "FROM SOLICITUD_REINSCRIPCION s JOIN USUARIOS u ON u.correo=s.correo_estudiante " +
            "JOIN PERIODOS_ESCOLARES p ON p.id_periodo=s.id_periodo " +
            "JOIN GRUPOS g ON g.id_grupo=s.id_grupo JOIN MATERIAS m ON m.clave_materia=g.clave_materia " +
            "ORDER BY s.fecha_solicitud DESC,s.id_solicitud DESC";
        try(Connection c=MySQL.conectar();PreparedStatement q=c.prepareStatement(sql);ResultSet r=q.executeQuery()){
            while(r.next()){
                String correo=r.getString(1),periodo=String.valueOf(r.getInt(2)),key=correo+"|"+periodo;
                Map<String,String> grupo=grupos.get(key);
                if(grupo==null){
                    grupo=new LinkedHashMap<>();grupo.put("correo",correo);grupo.put("id_periodo",periodo);
                    grupo.put("nombre",r.getString(3)+" "+r.getString(4));grupo.put("periodo_nombre",r.getString(5));
                    grupo.put("materias","");grupo.put("cantidad","0");grupo.put("estado","HOJA ENVIADA");
                    grupos.put(key,grupo);
                }
                grupo.put("materias",grupo.get("materias")+(grupo.get("materias").isEmpty()?"":"\n")+
                    "• "+r.getString(7)+" — "+r.getString(8));
                grupo.put("cantidad",String.valueOf(Integer.parseInt(grupo.get("cantidad"))+1));
                if(!"HOJA ENVIADA".equalsIgnoreCase(r.getString(6)))grupo.put("estado","pendiente");
            }
        }catch(SQLException e){throw new IllegalStateException("No se pudieron agrupar solicitudes: "+e.getMessage(),e);}
        return new ArrayList<>(grupos.values());
    }

    /** Elimina solo las solicitudes del estudiante y periodo, sin borrar CARGA_ACADEMICA. */
    public void eliminarSolicitudesAlumno(String correo,int periodo){
        try(Connection c=MySQL.conectar();PreparedStatement p=c.prepareStatement(
            "DELETE FROM SOLICITUD_REINSCRIPCION WHERE correo_estudiante=? AND id_periodo=?")){
            p.setString(1,correo);p.setInt(2,periodo);
            if(p.executeUpdate()==0)throw new IllegalArgumentException("No se encontraron solicitudes para eliminar");
        }catch(SQLException e){throw new IllegalStateException("No se pudieron eliminar las solicitudes: "+e.getMessage(),e);}
    }

    /** Guarda el PDF real y cambia el estado de las materias del periodo en una transacción. */
    public void subirHojaDePago(String correo,int periodo,String ruta){
        try{
            java.nio.file.Path archivo=java.nio.file.Path.of(ruta);
            if(!java.nio.file.Files.isRegularFile(archivo) || !archivo.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf"))
                throw new IllegalArgumentException("Selecciona un archivo PDF válido");
            long tam=java.nio.file.Files.size(archivo);
            if(tam<5 || tam>20_000_000L)throw new IllegalArgumentException("El PDF debe tener entre 5 bytes y 20 MB");
            byte[] contenido=java.nio.file.Files.readAllBytes(archivo);
            if(contenido.length<4 || contenido[0]!='%' || contenido[1]!='P' || contenido[2]!='D' || contenido[3]!='F')
                throw new IllegalArgumentException("El archivo no tiene formato PDF");
            try(Connection c=MySQL.conectar()){
                c.setAutoCommit(false);
                try{
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO REINSCRIPCION_DOCUMENTOS " +
                        "(correo_estudiante,archivo_nombre,archivo_pdf,subido_por) VALUES (?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE archivo_nombre=VALUES(archivo_nombre), archivo_pdf=VALUES(archivo_pdf),subido_por=VALUES(subido_por),fecha_subida=CURRENT_TIMESTAMP")){
                        p.setString(1,correo);p.setString(2,archivo.getFileName().toString());p.setBytes(3,contenido);p.setString(4,MockDB.sesion());p.executeUpdate();
                    }
                    try(PreparedStatement p=c.prepareStatement("UPDATE SOLICITUD_REINSCRIPCION SET estado='HOJA ENVIADA' WHERE correo_estudiante=? AND id_periodo=?")){
                        p.setString(1,correo);p.setInt(2,periodo);
                        if(p.executeUpdate()==0)throw new IllegalArgumentException("El estudiante no tiene solicitudes en este periodo");
                    }
                    c.commit();
                }catch(Exception ex){c.rollback();throw ex;}
            }
        }catch(Exception ex){throw new IllegalStateException("No se pudo guardar la hoja de pago: "+ex.getMessage(),ex);}
    }

}
