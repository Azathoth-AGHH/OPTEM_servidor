package com.optem.logic;

import com.optem.database.MySQL;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Puntos y rachas autoritativos en MySQL; operaciones idempotentes por asignacion. */
public final class XPService {
    private XPService() {}
    public static int xp(String correo) {
        String sql="SELECT COALESCE(SUM(GREATEST(puntos,0)),0) FROM OPTEM_XP_EVENTOS WHERE correo_alumno=? AND tipo<>'PENALIZACION'";
        try(Connection c=MySQL.conectar();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,correo);try(ResultSet r=p.executeQuery()){
                int total=r.next()?Math.max(0,r.getInt(1)):0;
                OfflineReadCache.save(correo,"xp",Integer.toString(total));
                return total;
            }
        }catch(SQLException e){
            String local=OfflineReadCache.load(correo,"xp");
            if(local!=null)return Integer.parseInt(local);
            throw new IllegalStateException("No se pudo leer XP: sin conexión y sin datos guardados localmente.",e);
        }
    }
    public static int racha(String correo){
        try(Connection c=MySQL.conectar();PreparedStatement p=c.prepareStatement("SELECT racha_dias,ultima_conexion FROM USUARIOS WHERE correo=?")){
            p.setString(1,correo);try(ResultSet r=p.executeQuery()){
                if(!r.next()) {
                    OfflineReadCache.save(correo,"racha","0|");
                    return 0;
                }
                int dias=r.getInt(1);
                Timestamp ultimo=r.getTimestamp(2);
                // Guardar el dato original para aplicar la misma regla de 24 horas offline.
                OfflineReadCache.save(correo,"racha",dias+"|"+(ultimo==null?"":ultimo.toLocalDateTime()));
                if(ultimo!=null && Duration.between(ultimo.toLocalDateTime(),LocalDateTime.now()).toHours()>=24)return 0;
                return dias;
            }
        }catch(SQLException ex){
            String local=OfflineReadCache.load(correo,"racha");
            if(local==null)throw new IllegalStateException("No hay racha guardada localmente. Abre Logros una vez con Tailscale conectado.",ex);
            try {
                String[] partes=local.split("\\|",-1);
                int dias=Math.max(0,Integer.parseInt(partes[0]));
                if(partes.length>1 && !partes[1].isBlank()) {
                    LocalDateTime ultimo=LocalDateTime.parse(partes[1]);
                    if(Duration.between(ultimo,LocalDateTime.now()).toHours()>=24)return 0;
                }
                System.err.println("OPTEM: mostrando racha guardada localmente (solo lectura).");
                return dias;
            }catch(RuntimeException parseError){
                throw new IllegalStateException("Los datos locales de la racha no se pudieron leer.",parseError);
            }
        }
    }
    /** Actualiza la racha una sola vez al acceder, no por cada cambio de pantalla. */
    public static void registrarAcceso(String correo) {
        try(Connection c=MySQL.conectar()){
            c.setAutoCommit(false);
            try(PreparedStatement p=c.prepareStatement("SELECT ultima_conexion,racha_dias FROM USUARIOS WHERE correo=? FOR UPDATE")){
                p.setString(1,correo);
                try(ResultSet r=p.executeQuery()){
                    if(!r.next())throw new IllegalArgumentException("Usuario inexistente");
                    Timestamp fecha=r.getTimestamp(1);int dias=r.getInt(2);
                    LocalDateTime ahora=LocalDateTime.now();
                    if(fecha==null)dias=1;
                    else if(Duration.between(fecha.toLocalDateTime(),ahora).toMinutes()>=1440)dias=0;
                    else if(!fecha.toLocalDateTime().toLocalDate().equals(ahora.toLocalDate()))dias+=1;
                    try(PreparedStatement u=c.prepareStatement("UPDATE USUARIOS SET ultima_conexion=?,racha_dias=? WHERE correo=?")){
                        u.setTimestamp(1,Timestamp.valueOf(ahora));u.setInt(2,dias);u.setString(3,correo);u.executeUpdate();
                    }
                }
            }
            c.commit();
        }catch(SQLException ex){throw new IllegalStateException("No se pudo actualizar la racha: "+ex.getMessage(),ex);}
        // La racha es independiente del XP. Las tareas no entregadas no restan XP.
    }
    /** Sin descuentos por no entregar tareas. Se conserva por compatibilidad. */
    public static void aplicarPenalizaciones(String correo){
        // No-op: el XP acumulado no depende de la racha ni de tareas pendientes.
    }
    public static void sincronizarXP(Connection c,String correo) throws SQLException{
        try(PreparedStatement p=c.prepareStatement("UPDATE USUARIOS SET xp_total=(SELECT COALESCE(SUM(GREATEST(puntos,0)),0) FROM OPTEM_XP_EVENTOS WHERE correo_alumno=? AND tipo<>'PENALIZACION') WHERE correo=?")){
            p.setString(1,correo);p.setString(2,correo);p.executeUpdate();
        }
    }
}
