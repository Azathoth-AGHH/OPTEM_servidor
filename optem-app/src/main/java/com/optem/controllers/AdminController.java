package com.optem.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.optem.database.MockDB;

public class AdminController {

    private static boolean reinscripcionActiva = false;

    public String obtenerEstadoServidor() {
        try(java.sql.Connection c=com.optem.database.MySQL.conectar()){
            return c.isValid(3)?"🟢 MySQL: Conectado":"🔴 MySQL: No disponible";
        }catch(Exception ex){return "🔴 MySQL: "+ex.getMessage();}
    }

    public boolean ejecutarRespaldoBD() {
        // No fingir que existe un respaldo cuando no se generó.
        System.err.println("Respaldo aún no implementado desde esta pantalla. Usa mysqldump en Ubuntu Server.");
        return false;
    }

    public boolean isReinscripcionActiva() {
        try(java.sql.Connection c=com.optem.database.MySQL.conectar();java.sql.PreparedStatement p=c.prepareStatement("SELECT valor FROM CONFIG_SISTEMA WHERE clave='REINSCRIPCION_ABIERTA'")){
            try(java.sql.ResultSet r=p.executeQuery()){return r.next() && ("1".equals(r.getString(1)) || "true".equalsIgnoreCase(r.getString(1)));}
        }catch(Exception ex){throw new IllegalStateException("No se pudo consultar reinscripción: "+ex.getMessage(),ex);}
    }
    public void toggleReinscripcion(boolean estado) {
        try(java.sql.Connection c=com.optem.database.MySQL.conectar();java.sql.PreparedStatement p=c.prepareStatement("INSERT INTO CONFIG_SISTEMA(clave,valor) VALUES('REINSCRIPCION_ABIERTA',?) ON DUPLICATE KEY UPDATE valor=VALUES(valor)")){
            p.setString(1,estado?"1":"0");p.executeUpdate();
        }catch(Exception ex){throw new IllegalStateException("No se pudo actualizar reinscripción: "+ex.getMessage(),ex);}
    }
    
    public boolean crearGrupoOficial(String materia, String maestro, String grupo, String horario) {
        if(materia == null || materia.isBlank()) throw new IllegalArgumentException("Selecciona una materia válida");
        if(maestro == null || maestro.isBlank()) throw new IllegalArgumentException("Selecciona un docente");
        if(horario == null || !horariosDisponibles(materia).contains(horario))
            throw new IllegalArgumentException("Selecciona uno de los cinco horarios disponibles");
        
        try{
            String codigo=(grupo==null||grupo.isBlank())?generarClaveGrupo(materia):grupo.trim();
            try(java.sql.Connection c=com.optem.database.MySQL.conectar();
                java.sql.PreparedStatement p=c.prepareStatement(
                    "INSERT INTO GRUPOS(clave_materia,nombre_grupo,correo_docente,horario_texto,archivado,codigo_unico) " +
                    "SELECT m.clave_materia,?,u.correo,?,0,? FROM MATERIAS m JOIN USUARIOS u ON u.correo=? " +
                    "JOIN ROLES r ON r.id_rol=u.id_rol AND r.nombre_rol='Docente' " +
                    "WHERE m.clave_materia=? AND u.activo=1 AND NOT EXISTS (SELECT 1 FROM GRUPOS WHERE nombre_grupo=?)")){
                p.setString(1,codigo);p.setString(2,horario);p.setString(3,codigo);p.setString(4,maestro);
                p.setString(5,materia);p.setString(6,codigo);
                if(p.executeUpdate()!=1)throw new IllegalArgumentException("Grupo repetido, materia o docente no válido");
                return true;
            }
        }catch(Exception ex){throw new IllegalStateException("No se pudo registrar el grupo en MySQL: "+ex.getMessage(),ex);}
    }

    /** Cinco turnos de oferta compatibles con los horarios del alumno. */
    public java.util.List<String> horariosDisponibles(String claveMateria) {
        return java.util.List.of(
            "Lun/Mié 07:00-09:00",
            "Mar/Jue 09:00-11:00",
            "Lun/Mié 11:00-13:00",
            "Mar/Jue 13:00-15:00",
            "Vie 15:00-17:00");
    }

    /** Solo muestra el docente DEMO asignado a esta materia y docentes existentes de la carrera. */
    public java.util.Map<String,String> docentesPorMateria(String claveMateria) {
        java.util.Map<String,String> out = new java.util.LinkedHashMap<>();
        if (claveMateria == null || claveMateria.isBlank()) return out;
        String sql = "SELECT u.correo, CONCAT(u.nombre,' ',u.apellidos) AS nombre " +
            "FROM USUARIOS u JOIN ROLES r ON r.id_rol=u.id_rol " +
            "LEFT JOIN OPTEM_PERFILES_PERSONAL p ON p.correo_usuario=u.correo " +
            "WHERE r.nombre_rol='Docente' AND u.activo=1 " +
            "AND (p.departamento IS NULL OR p.departamento NOT LIKE 'MATERIA:%' " +
            "OR p.departamento=CONCAT('MATERIA:',?)) " +
            "AND (p.carrera_docente IS NULL OR p.carrera_docente='Ingeniería de Software') " +
            "ORDER BY CASE WHEN p.departamento=CONCAT('MATERIA:',?) THEN 0 ELSE 1 END, u.apellidos";
        try (java.sql.Connection c=com.optem.database.MySQL.conectar();
             java.sql.PreparedStatement q=c.prepareStatement(sql)) {
            q.setString(1, claveMateria);q.setString(2, claveMateria);
            try (java.sql.ResultSet r=q.executeQuery()) {
                while(r.next()) out.put(r.getString(1),r.getString(2));
            }
        }catch(java.sql.SQLException e){throw new IllegalStateException("No se pudieron cargar docentes: "+e.getMessage(),e);}
        return out;
    }

    public String generarClaveGrupo(String claveMateria){
        return "G"+UUID.randomUUID().toString().replace("-","").substring(0,8).toUpperCase();
    }
    public java.util.List<Integer> semestresDisponibles(){
        java.util.List<Integer> out=new java.util.ArrayList<>();
        try(java.sql.Connection c=com.optem.database.MySQL.conectar();
            java.sql.PreparedStatement p=c.prepareStatement("SELECT DISTINCT semestre_sugerido FROM OPTEM_PLAN_MATERIAS ORDER BY semestre_sugerido");
            java.sql.ResultSet r=p.executeQuery()){
            while(r.next())out.add(r.getInt(1));
        }catch(Exception ex){throw new IllegalStateException(ex);}return out;
    }
    public java.util.Map<String,String> materiasPorSemestre(int semestre){
        java.util.Map<String,String> out=new java.util.LinkedHashMap<>();
        try(java.sql.Connection c=com.optem.database.MySQL.conectar();
            java.sql.PreparedStatement p=c.prepareStatement("SELECT m.clave_materia,m.nombre_materia FROM MATERIAS m JOIN OPTEM_PLAN_MATERIAS plan ON plan.clave_materia=m.clave_materia WHERE plan.semestre_sugerido=? ORDER BY m.nombre_materia")){
            p.setInt(1,semestre);try(java.sql.ResultSet r=p.executeQuery()){while(r.next())out.put(r.getString(1),r.getString(2));}
        }catch(Exception ex){throw new IllegalStateException(ex);}return out;
    }
    public java.util.Map<String,String> docentes(){
        java.util.Map<String,String> out=new java.util.LinkedHashMap<>();
        try(java.sql.Connection c=com.optem.database.MySQL.conectar();
            java.sql.PreparedStatement p=c.prepareStatement("SELECT u.correo,CONCAT(u.nombre,' ',u.apellidos) FROM USUARIOS u JOIN ROLES r ON r.id_rol=u.id_rol " +
                "JOIN OPTEM_PERFILES_PERSONAL p ON p.correo_usuario=u.correo WHERE r.nombre_rol='Docente' AND u.activo=1 " +
                "AND p.carrera_docente='Ingeniería de Software' ORDER BY u.apellidos");
            java.sql.ResultSet r=p.executeQuery()){
            while(r.next())out.put(r.getString(1),r.getString(2));
        }catch(Exception ex){throw new IllegalStateException(ex);}return out;
    }
    public String[] obtenerInfoContacto(String numeroCuenta) {
        if(numeroCuenta.isEmpty()) return null;
        
        // Consultamos al MockDB
        Map<String, String> estudiante = MockDB.obtenerEstudiantePorCuenta(numeroCuenta);
        
        if (estudiante != null) {
            return new String[]{
                estudiante.get("nombre_completo"),
                estudiante.get("telefono"),
                estudiante.get("correo"),
                estudiante.get("calle"),
                estudiante.get("colonia"),
                estudiante.get("municipio"),
                estudiante.get("cp"),
                estudiante.get("pais"),
                estudiante.get("estado"),
                estudiante.get("ciudad")
            };
        }
        return null; // Si no existe el estudiante
    }

    public List<String> obtenerDocumentosIngreso(String numeroCuenta) {
        // Consultamos al MockDB
        return MockDB.obtenerDocumentos(numeroCuenta);
    }

    public String[] obtenerIndicadoresRiesgo(String numeroCuenta) {
        // Consultamos al MockDB
        Map<String, String> estudiante = MockDB.obtenerEstudiantePorCuenta(numeroCuenta);
        
        if (estudiante != null) {
            return new String[]{ 
                estudiante.get("avance"), 
                estudiante.get("promedio_general"), 
                estudiante.get("promedio_ultimo"), 
                estudiante.get("situacion"), 
                estudiante.get("fecha_actualizacion"), 
                estudiante.get("riesgo") 
            };
        }
        return new String[]{"0%", "0.0", "0.0", "DESCONOCIDO", "N/A", "Desconocido"}; // Valores por defecto
    }

    public String generarHorarioInteligente(String numeroCuenta) {
        // Consultamos al MockDB
        Map<String, String> estudiante = MockDB.obtenerEstudiantePorCuenta(numeroCuenta);
        if (estudiante != null && estudiante.containsKey("analisis_ia")) {
            return estudiante.get("analisis_ia");
        }
        return "No hay análisis de IA disponible para esta cuenta.";
    }

    public String evaluarRecompensas() {
        // Consultamos al MockDB
        return MockDB.obtenerRecompensasGenerales();
    }
}