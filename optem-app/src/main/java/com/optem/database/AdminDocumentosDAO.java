package com.optem.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consultas autorizadas para documentos y reporte administrativo V6.
 *  No modifica el esquema ni exporta credenciales o contraseñas.
 */
public final class AdminDocumentosDAO {
    private AdminDocumentosDAO() {}
    public record Documento(String correo, String nombreArchivo, String fechaSubida) {}
    public record Resumen(Map<String,Long> totales, List<Documento> documentos) {}

    private static void verificarAdministrador(Connection c) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
            "SELECT 1 FROM USUARIOS u JOIN ROLES r ON r.id_rol=u.id_rol " +
            "WHERE u.correo=? AND u.activo=1 AND r.nombre_rol='Administrador' LIMIT 1")) {
            p.setString(1, MockDB.sesion());
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) throw new SQLException("Solo el administrador puede consultar los documentos institucionales.");
            }
        }
    }

    public static Resumen consultar() throws SQLException {
        try (Connection c=MySQL.conectar()) {
            verificarAdministrador(c);
            LinkedHashMap<String,Long> totales=new LinkedHashMap<>();
            totales.put("Estudiantes activos", contar(c,"SELECT COUNT(*) FROM USUARIOS u JOIN ROLES r ON r.id_rol=u.id_rol WHERE r.nombre_rol='Estudiante' AND u.activo=1"));
            totales.put("Docentes activos", contar(c,"SELECT COUNT(*) FROM USUARIOS u JOIN ROLES r ON r.id_rol=u.id_rol WHERE r.nombre_rol='Docente' AND u.activo=1"));
            totales.put("Grupos activos", contar(c,"SELECT COUNT(*) FROM GRUPOS WHERE archivado=0"));
            totales.put("Materias", contar(c,"SELECT COUNT(*) FROM MATERIAS"));
            totales.put("Inscripciones vigentes", contar(c,"SELECT COUNT(*) FROM CARGA_ACADEMICA WHERE estado_inscripcion='inscrito'"));
            totales.put("Tareas publicadas", contar(c,"SELECT COUNT(*) FROM OPTEM_ASIGNACIONES_DOCENTE"));
            totales.put("Trabajos recibidos", contar(c,"SELECT COUNT(*) FROM OPTEM_ENTREGAS_ASIGNACION"));
            totales.put("Solicitudes de reinscripción", contar(c,"SELECT COUNT(*) FROM SOLICITUD_REINSCRIPCION"));
            totales.put("Hojas de pago PDF", contar(c,"SELECT COUNT(*) FROM REINSCRIPCION_DOCUMENTOS"));
            List<Documento> docs=new ArrayList<>();
            try (PreparedStatement p=c.prepareStatement(
                "SELECT correo_estudiante,archivo_nombre,CAST(fecha_subida AS CHAR) " +
                "FROM REINSCRIPCION_DOCUMENTOS ORDER BY fecha_subida DESC LIMIT 30");
                 ResultSet r=p.executeQuery()) {
                while(r.next()) docs.add(new Documento(r.getString(1),r.getString(2),r.getString(3)));
            }
            return new Resumen(totales,docs);
        }
    }

    private static long contar(Connection c,String query) throws SQLException {
        try (PreparedStatement p=c.prepareStatement(query);ResultSet r=p.executeQuery()){
            return r.next()?r.getLong(1):0;
        }
    }

    public static byte[] leerDocumento(String correo) throws SQLException {
        try(Connection c=MySQL.conectar()){
            verificarAdministrador(c);
            try(PreparedStatement p=c.prepareStatement(
                "SELECT archivo_pdf FROM REINSCRIPCION_DOCUMENTOS WHERE correo_estudiante=?")){
                p.setString(1,correo);
                try(ResultSet r=p.executeQuery()){
                    if(!r.next())throw new SQLException("La hoja de pago ya no está disponible.");
                    byte[] data=r.getBytes(1);
                    if(data==null||data.length==0)throw new SQLException("El documento está vacío.");
                    return data;
                }
            }
        }
    }
}
