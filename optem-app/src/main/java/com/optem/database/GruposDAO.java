package com.optem.database;
import java.sql.*;
import java.util.*;
/** Operaciones de grupos y matrículas contra MySQL. */
public final class GruposDAO {
    private GruposDAO(){}
    public record Alumno(String correo,String cuenta,String nombre){}
    private static void verificarGrupo(Connection c,int grupo) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM GRUPOS g WHERE g.id_grupo=? AND (g.correo_docente=? OR EXISTS(SELECT 1 FROM USUARIOS u JOIN ROLES r ON u.id_rol=r.id_rol WHERE u.correo=? AND r.nombre_rol='Administrador'))")){
            p.setInt(1,grupo);p.setString(2,MockDB.sesion());p.setString(3,MockDB.sesion());
            try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("No tienes permiso sobre este grupo.");}
        }
    }
    /** Inscripción manual segura desde la cuenta docente: correo o matrícula exactos.
     * Devuelve la identidad realmente inscrita para la confirmación de JavaFX.
     * No modifica grupos, roles ni estructura de tablas.
     */
    public static Alumno inscribirAlumno(int grupo, String busqueda) throws SQLException {
        if(busqueda==null || busqueda.isBlank())
            throw new IllegalArgumentException("Escribe el correo institucional o el número de cuenta.");
        String valor=busqueda.trim();
        try(Connection c=MySQL.conectar()){
            c.setAutoCommit(false);
            try{
                verificarGrupo(c,grupo);
                try(PreparedStatement gp=c.prepareStatement("SELECT archivado FROM GRUPOS WHERE id_grupo=?")){
                    gp.setInt(1,grupo);
                    try(ResultSet gr=gp.executeQuery()){
                        if(!gr.next() || gr.getBoolean(1))
                            throw new IllegalArgumentException("El grupo está archivado o ya no existe.");
                    }
                }
                Alumno alumno;
                try(PreparedStatement p=c.prepareStatement(
                    "SELECT u.correo,u.numero_cuenta,CONCAT(u.nombre,' ',u.apellidos) " +
                    "FROM USUARIOS u JOIN ROLES r ON r.id_rol=u.id_rol " +
                    "WHERE r.nombre_rol='Estudiante' AND u.activo=1 " +
                    "AND (LOWER(u.correo)=LOWER(?) OR u.numero_cuenta=?)")){
                    p.setString(1,valor);p.setString(2,valor);
                    try(ResultSet rs=p.executeQuery()){
                        if(!rs.next())throw new IllegalArgumentException(
                            "No existe un estudiante activo con ese correo o número de cuenta.");
                        alumno=new Alumno(rs.getString(1),rs.getString(2),rs.getString(3));
                        if(rs.next())throw new IllegalArgumentException(
                            "La búsqueda coincide con varias cuentas; usa el correo completo.");
                    }
                }
                // Se usa el periodo activo; si no existe, el último periodo registrado
                // para permitir la inscripción manual durante las pruebas de la demo.
                int periodo;
                try(PreparedStatement q=c.prepareStatement(
                    "SELECT id_periodo FROM PERIODOS_ESCOLARES " +
                    "ORDER BY (LOWER(estado)='activo') DESC,id_periodo DESC LIMIT 1");
                    ResultSet rs=q.executeQuery()){
                    if(!rs.next())throw new IllegalStateException(
                        "No existen periodos escolares. Administración debe registrar uno primero.");
                    periodo=rs.getInt(1);
                }
                int carga=-1;String estado=null;
                try(PreparedStatement q=c.prepareStatement(
                    "SELECT id_carga,estado_inscripcion FROM CARGA_ACADEMICA " +
                    "WHERE correo_estudiante=? AND id_grupo=? AND id_periodo=? FOR UPDATE")){
                    q.setString(1,alumno.correo());q.setInt(2,grupo);q.setInt(3,periodo);
                    try(ResultSet rs=q.executeQuery()){
                        if(rs.next()){
                            carga=rs.getInt(1);estado=rs.getString(2);
                            if(rs.next())throw new IllegalStateException(
                                "Hay inscripciones duplicadas para el estudiante y grupo. Revisa CARGA_ACADEMICA.");
                        }
                    }
                }
                if("inscrito".equalsIgnoreCase(estado))
                    throw new IllegalArgumentException("Este alumno ya está inscrito en el grupo.");
                if(carga>=0){
                    try(PreparedStatement q=c.prepareStatement(
                        "UPDATE CARGA_ACADEMICA SET estado_inscripcion='inscrito' WHERE id_carga=?")){
                        q.setInt(1,carga);q.executeUpdate();
                    }
                }else{
                    try(PreparedStatement q=c.prepareStatement(
                        "INSERT INTO CARGA_ACADEMICA(correo_estudiante,id_grupo,id_periodo,estado_inscripcion) " +
                        "VALUES (?,?,?,'inscrito')")){
                        q.setString(1,alumno.correo());q.setInt(2,grupo);q.setInt(3,periodo);
                        q.executeUpdate();
                    }
                }
                c.commit();return alumno;
            }catch(Exception ex){
                c.rollback();
                if(ex instanceof SQLException se)throw se;
                if(ex instanceof RuntimeException re)throw re;
                throw new IllegalStateException(ex);
            }
        }
    }

    public static List<Alumno> alumnos(int id) throws SQLException {
        List<Alumno> out=new ArrayList<>();
        try(Connection c=MySQL.conectar()){
            verificarGrupo(c,id);
            try(PreparedStatement p=c.prepareStatement("SELECT DISTINCT u.correo,u.numero_cuenta,CONCAT(u.nombre,' ',u.apellidos) AS nombre FROM CARGA_ACADEMICA ca JOIN USUARIOS u ON u.correo=ca.correo_estudiante WHERE ca.id_grupo=? AND ca.estado_inscripcion='inscrito' ORDER BY nombre")) {
                p.setInt(1,id);try(ResultSet rs=p.executeQuery()){while(rs.next())out.add(new Alumno(rs.getString(1),rs.getString(2),rs.getString(3)));}
            }
        }
        return out;
    }
    public static void quitarAlumno(int id,String correo) throws SQLException {
        try(Connection c=MySQL.conectar()){
            verificarGrupo(c,id);
            try(PreparedStatement p=c.prepareStatement("UPDATE CARGA_ACADEMICA SET estado_inscripcion='baja' WHERE id_grupo=? AND correo_estudiante=? AND estado_inscripcion='inscrito'")) {
                p.setInt(1,id);p.setString(2,correo);if(p.executeUpdate()<1)throw new SQLException("El alumno no estaba inscrito.");
            }
        }
    }
    public static void horario(int id,String h) throws SQLException {
        if(h==null||h.isBlank())throw new SQLException("Escribe un horario.");
        try(Connection c=MySQL.conectar()){
            verificarGrupo(c,id);try(PreparedStatement p=c.prepareStatement("UPDATE GRUPOS SET horario_texto=? WHERE id_grupo=?")) {
                p.setString(1,h.trim());p.setInt(2,id);if(p.executeUpdate()!=1)throw new SQLException("No se actualizó el grupo.");
            }
        }
    }
    public static void archivar(int id) throws SQLException {
        try(Connection c=MySQL.conectar()){
            verificarGrupo(c,id);try(PreparedStatement p=c.prepareStatement("UPDATE GRUPOS SET archivado=1 WHERE id_grupo=?")) {
                p.setInt(1,id);if(p.executeUpdate()!=1)throw new SQLException("No se archivó el grupo.");
            }
        }
    }
    public static List<Map<String,String>> gruposArchivados() throws SQLException{
        List<Map<String,String>> out=new ArrayList<>();
        String q="SELECT g.id_grupo,m.nombre_materia,g.nombre_grupo,COALESCE(g.horario_texto,'') FROM GRUPOS g JOIN MATERIAS m ON m.clave_materia=g.clave_materia WHERE g.correo_docente=? AND g.archivado=1 ORDER BY g.id_grupo DESC";
        try(Connection c=MySQL.conectar();PreparedStatement p=c.prepareStatement(q)){
            p.setString(1,MockDB.sesion());try(ResultSet r=p.executeQuery()){
                while(r.next()){
                    Map<String,String> x=new HashMap<>();x.put("id",r.getString(1));x.put("materia",r.getString(2));
                    x.put("grupo",r.getString(3));x.put("horario",r.getString(4));out.add(x);
                }
            }
        }return out;
    }
    public static void desarchivar(int id) throws SQLException{
        try(Connection c=MySQL.conectar();PreparedStatement p=c.prepareStatement("UPDATE GRUPOS SET archivado=0 WHERE id_grupo=? AND correo_docente=?")){
            p.setInt(1,id);p.setString(2,MockDB.sesion());
            if(p.executeUpdate()!=1)throw new SQLException("Grupo archivado no encontrado");
        }
    }
    public static int contarInscritos() throws SQLException {
        String q="SELECT COUNT(DISTINCT ca.correo_estudiante) FROM CARGA_ACADEMICA ca JOIN GRUPOS g ON g.id_grupo=ca.id_grupo WHERE ca.estado_inscripcion='inscrito' AND g.archivado=0 AND (g.correo_docente=? OR EXISTS(SELECT 1 FROM USUARIOS u JOIN ROLES r ON r.id_rol=u.id_rol WHERE u.correo=? AND r.nombre_rol='Administrador'))";
        try(Connection c=MySQL.conectar();PreparedStatement p=c.prepareStatement(q)){
            p.setString(1,MockDB.sesion());p.setString(2,MockDB.sesion());try(ResultSet rs=p.executeQuery()){rs.next();return rs.getInt(1);}
        }
    }
}
