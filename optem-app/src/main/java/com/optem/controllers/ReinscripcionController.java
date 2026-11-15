package com.optem.controllers;
import com.optem.database.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import java.text.Normalizer;

public class ReinscripcionController {
    public static class MateriaDTO {
        public String titulo, creditos, colorBorde, categoria;
        public boolean mixta;
        public String[] opciones;
        public MateriaDTO(String t,String c,boolean m,String cb,String cat,String[] o){titulo=t;creditos=c;mixta=m;colorBorde=cb;categoria=cat;opciones=o;}
    }
    public static class BloqueHorario {
        public String texto,colorHex;public int colDia,rowHora,duracion;
        public BloqueHorario(String t,int c,int r,int d,String h){texto=t;colDia=c;rowHora=r;duracion=d;colorHex=h;}
    }
    private boolean periodoSinConexion=false;
    public boolean periodoSinConexion(){return periodoSinConexion;}
    public boolean periodoAbierto(){
        periodoSinConexion=false;
        try(Connection c=MySQL.conectar();PreparedStatement p=c.prepareStatement("SELECT valor FROM CONFIG_SISTEMA WHERE clave='REINSCRIPCION_ABIERTA'")){
            try(ResultSet r=p.executeQuery()){return r.next() && ("1".equals(r.getString(1)) || "true".equalsIgnoreCase(r.getString(1)));}
        }catch(SQLException e){
            // La apertura del periodo exige verificacion en servidor: no habilitar solicitudes offline.
            periodoSinConexion=true;
            return false;
        }
    }
    public List<MateriaDTO> getMateriasDisponibles(){return MockDB.obtenerMateriasReinscripcion();}
    /**
     * Genera una PROPUESTA LOCAL a partir de las opciones reales cargadas desde MySQL.
     * No envía solicitudes ni guarda inscripciones. No inventa grupos ni horarios.
     * Si el estudiante ya marcó materias, se priorizan únicamente esas materias;
     * si todavía no eligió ninguna, se consideran todas las disponibles.
     */
    public Map<String,String> generarHorarioInteligente(String preferencia){
        return generarHorarioInteligente(preferencia, getMateriasDisponibles(), Collections.emptySet());
    }

    public Map<String,String> generarHorarioInteligente(String preferencia,
            List<MateriaDTO> disponibles, Set<String> seleccionadas) {
        if (!"MATUTINO".equals(preferencia) && !"TARDES".equals(preferencia))
            throw new IllegalArgumentException("Preferencia desconocida: " + preferencia);
        if (disponibles == null || disponibles.isEmpty()) return Collections.emptyMap();
        List<OpcionesMateria> materias = new ArrayList<>();
        for (MateriaDTO m : disponibles) {
            if (m == null || m.titulo == null) continue;
            if (seleccionadas != null && !seleccionadas.isEmpty()
                    && !seleccionadas.contains(m.titulo)) continue;
            List<GrupoCandidato> candidatos = new ArrayList<>();
            if (m.opciones != null) for (String opcion : m.opciones) {
                if (opcion == null || opcion.trim().isEmpty() || opcion.contains("No cursar")
                        || opcion.trim().startsWith("—")) continue;
                List<Franja> franjas = leerFranjas(opcion);
                if (!franjas.isEmpty()) candidatos.add(new GrupoCandidato(opcion, franjas));
            }
            if (!candidatos.isEmpty()) {
                candidatos.sort(Comparator.comparingLong(x -> puntuar(x.franjas, preferencia)));
                materias.add(new OpcionesMateria(m.titulo, candidatos));
            }
        }
        // Diferentes órdenes deterministas: se conserva la propuesta con más materias
        // y, a igualdad de materias, la mejor adaptada a la preferencia.
        List<List<OpcionesMateria>> ordenes = new ArrayList<>();
        List<OpcionesMateria> original = new ArrayList<>(materias);
        ordenes.add(original);
        List<OpcionesMateria> escasas = new ArrayList<>(materias);
        escasas.sort(Comparator.comparingInt(o -> o.candidatos.size()));
        ordenes.add(escasas);
        List<OpcionesMateria> inverso = new ArrayList<>(escasas);
        Collections.reverse(inverso);
        ordenes.add(inverso);
        Map<String,String> mejor = Collections.emptyMap();
        long mejorPuntaje = Long.MAX_VALUE;
        for (List<OpcionesMateria> orden : ordenes) {
            List<Franja> ocupadas = new ArrayList<>();
            Map<String,String> propuesta = new LinkedHashMap<>();
            long puntaje = 0;
            for (OpcionesMateria materia : orden) {
                for (GrupoCandidato candidato : materia.candidatos) {
                    if (seEmpalma(ocupadas, candidato.franjas)) continue;
                    propuesta.put(materia.titulo, candidato.opcion);
                    ocupadas.addAll(candidato.franjas);
                    puntaje += puntuar(candidato.franjas, preferencia);
                    break;
                }
            }
            if (propuesta.size() > mejor.size() ||
                    (propuesta.size() == mejor.size() && puntaje < mejorPuntaje)) {
                mejor = propuesta;
                mejorPuntaje = puntaje;
            }
        }
        return mejor;
    }

    private static final class Franja {
        final int dia, inicio, fin;
        Franja(int dia, int inicio, int fin) {
            this.dia = dia; this.inicio = inicio; this.fin = fin;
        }
    }
    private static final class GrupoCandidato {
        final String opcion;
        final List<Franja> franjas;
        GrupoCandidato(String opcion, List<Franja> franjas) {
            this.opcion = opcion; this.franjas = franjas;
        }
    }
    private static final class OpcionesMateria {
        final String titulo;
        final List<GrupoCandidato> candidatos;
        OpcionesMateria(String titulo, List<GrupoCandidato> candidatos) {
            this.titulo = titulo; this.candidatos = candidatos;
        }
    }

    private static boolean seEmpalma(List<Franja> ocupadas, List<Franja> nuevas) {
        for (Franja a : ocupadas) for (Franja b : nuevas)
            if (a.dia == b.dia && a.inicio < b.fin && b.inicio < a.fin) return true;
        return false;
    }

    private static long puntuar(List<Franja> franjas, String perfil) {
        long total = 0;
        for (Franja f : franjas) {
            // Matutino: terminar temprano. Mañanas libres: comenzar tarde.
            // Se prefiere mantener las franjas dentro de la cuadrícula 07:00-20:00.
            total += "MATUTINO".equals(perfil) ? f.fin : (24 * 60 - f.inicio);
        }
        return total;
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    /** Analiza sólo horarios reconocibles; nunca asigna 7:00 como valor ficticio. */
    private static List<Franja> leerFranjas(String opcion) {
        List<Franja> resultado = new ArrayList<>();
        if (opcion == null) return resultado;
        String texto = normalizar(opcion).replace('–', '-').replace('—', '-');
        int diaSeparador = texto.indexOf('·');
        if (diaSeparador >= 0) texto = texto.substring(diaSeparador + 1);
        int[] dias = new int[6];
        String[] patrones = {
            "\\blun(?:es)?\\b", "\\bmar(?:tes)?\\b", "\\bmie(?:r|rcoles)?\\b",
            "\\bjue(?:v|ves)?\\b", "\\bvie(?:rnes)?\\b", "\\bsab(?:ado)?\\b"
        };
        int cuantos = 0;
        for (int i = 0; i < patrones.length; i++)
            if (Pattern.compile(patrones[i]).matcher(texto).find()) dias[cuantos++] = i + 1;
        // Una sola franja horaria por opción; si hay más, no se presupone una interpretación.
        Pattern tiempos = Pattern.compile(
            "(?<!\\d)(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\s*(?:-|\\ba\\b)\\s*" +
            "(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b");
        Matcher m = tiempos.matcher(texto);
        if (cuantos == 0 || !m.find()) return resultado;
        int hi = Integer.parseInt(m.group(1)), mi = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        int hf = Integer.parseInt(m.group(4)), mf = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
        String pi = m.group(3), pf = m.group(6);
        if (mi > 59 || mf > 59) return resultado;
        if (pi == null && pf != null) {
            // "6 a 7pm" => 18-19; "11 a 1pm" => 11-13.
            if ("pm".equals(pf) && hi <= hf && hi <= 7) pi = "pm";
            else if ("pm".equals(pf) && hi > hf) pi = "am";
            else if ("am".equals(pf)) pi = "am";
        }
        hi = convertir24(hi, pi); hf = convertir24(hf, pf);
        if (hi < 0 || hf < 0) return resultado;
        int inicio = hi * 60 + mi, fin = hf * 60 + mf;
        if (inicio >= fin || inicio < 7 * 60 || fin > 20 * 60) return resultado;
        // No usar opciones con varios intervalos que este analizador no pueda garantizar.
        if (m.find()) return resultado;
        for (int i = 0; i < cuantos; i++) resultado.add(new Franja(dias[i], inicio, fin));
        return resultado;
    }

    private static int convertir24(int h, String periodo) {
        if (periodo == null) return h >= 0 && h <= 23 ? h : -1;
        if (h < 1 || h > 12) return -1;
        return h % 12 + ("pm".equals(periodo) ? 12 : 0);
    }


    /** Registra solicitud real para grupo existente; no crea inscripciones ficticias. */
    public void inscribirAlumnoMateria(String correo,String materia,String opcion){
        if(opcion==null || opcion.startsWith("—"))return;
        String nombreGrupo=opcion.split(" · ",2)[0].trim();
        String sql="INSERT INTO SOLICITUD_REINSCRIPCION(correo_estudiante,id_grupo,id_periodo,estado) " +
          "SELECT ?,g.id_grupo,p.id_periodo,'pendiente' FROM GRUPOS g JOIN MATERIAS m ON m.clave_materia=g.clave_materia " +
          "JOIN PERIODOS_ESCOLARES p ON p.estado='activo' " +
          "JOIN USUARIOS u ON u.correo=? JOIN OPTEM_PLAN_MATERIAS plan ON plan.clave_materia=g.clave_materia " +
          "LEFT JOIN OPTEM_MATERIAS_ADELANTABLES ad ON ad.clave_materia=g.clave_materia " +
          "WHERE UPPER(m.nombre_materia)=UPPER(?) AND g.nombre_grupo=? AND g.archivado=0 AND " +
          "(plan.semestre_sugerido=u.semestre+1 OR (plan.semestre_sugerido>u.semestre+1 AND ad.autorizada=1)) " +
          "AND (SELECT valor FROM CONFIG_SISTEMA WHERE clave='REINSCRIPCION_ABIERTA')='1' ORDER BY p.id_periodo DESC LIMIT 1";
        try(Connection c=MySQL.conectar();PreparedStatement ps=c.prepareStatement(sql)){
            ps.setString(1,correo);ps.setString(2,correo);ps.setString(3,materia);ps.setString(4,nombreGrupo);
            if(ps.executeUpdate()!=1)throw new IllegalArgumentException("No hay grupo/periodo activo para "+materia);
        }catch(SQLIntegrityConstraintViolationException ex){throw new IllegalArgumentException("Ya habías solicitado inscripción a "+materia,ex);}
        catch(SQLException ex){throw new IllegalStateException("No se pudo registrar "+materia+": "+ex.getMessage(),ex);}
    }
    public void inscribirAlumnoMateria(String correo,String materia){throw new IllegalArgumentException("Selecciona un grupo para la materia "+materia);}
    /** Guarda todas las materias elegidas de forma atómica. */
    public void registrarSeleccion(String correo,Map<String,String> elecciones){
        if(elecciones==null || elecciones.isEmpty())throw new IllegalArgumentException("No elegiste materias");
        if(!periodoAbierto())throw new IllegalStateException("El periodo de reinscripción está cerrado");
        try(Connection c=MySQL.conectar()){
            c.setAutoCommit(false);
            try{
                int periodo;
                try(PreparedStatement q=c.prepareStatement("SELECT id_periodo FROM PERIODOS_ESCOLARES WHERE estado='activo' ORDER BY id_periodo DESC LIMIT 1");ResultSet r=q.executeQuery()){
                    if(!r.next())throw new IllegalStateException("No existe un periodo escolar activo.");
                    periodo=r.getInt(1);
                }
                for(Map.Entry<String,String> e:elecciones.entrySet()){
                    String opcion=e.getValue();
                    if(opcion==null || opcion.startsWith("—"))continue;
                    String grupo=opcion.split(" · ",2)[0].trim();
                    int idGrupo;
                    try(PreparedStatement q=c.prepareStatement(
                        "SELECT g.id_grupo FROM GRUPOS g JOIN MATERIAS m ON m.clave_materia=g.clave_materia " +
                        "JOIN OPTEM_PLAN_MATERIAS plan ON plan.clave_materia=g.clave_materia JOIN USUARIOS u ON u.correo=? " +
                        "LEFT JOIN OPTEM_MATERIAS_ADELANTABLES ad ON ad.clave_materia=g.clave_materia " +
                        "WHERE UPPER(m.nombre_materia)=UPPER(?) AND g.nombre_grupo=? AND g.archivado=0 AND " +
                        "(plan.semestre_sugerido=u.semestre+1 OR (plan.semestre_sugerido>u.semestre+1 AND ad.autorizada=1)) LIMIT 1")){
                        q.setString(1,correo);q.setString(2,e.getKey());q.setString(3,grupo);
                        try(ResultSet r=q.executeQuery()){
                            if(!r.next())throw new IllegalArgumentException("El grupo seleccionado no existe: "+e.getKey());
                            idGrupo=r.getInt(1);
                        }
                    }
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO SOLICITUD_REINSCRIPCION(correo_estudiante,id_grupo,id_periodo,estado) VALUES (?,?,?,'pendiente')")){
                        p.setString(1,correo);p.setInt(2,idGrupo);p.setInt(3,periodo);p.executeUpdate();
                    }
                }
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }catch(Exception e){throw new IllegalStateException("No se pudo guardar la reinscripción: "+e.getMessage(),e);}
    }
    public void registrarSolicitudCompleta(String correo,String nombre){
        // Las solicitudes individuales fueron guardadas en SOLICITUD_REINSCRIPCION.
    }
    public List<Map<String,String>> obtenerTodasLasSolicitudes(){
        List<Map<String,String>> out=new ArrayList<>();
        String sql="SELECT sr.id_solicitud,sr.correo_estudiante,u.nombre,u.apellidos,sr.estado,m.nombre_materia,g.nombre_grupo " +
            "FROM SOLICITUD_REINSCRIPCION sr JOIN USUARIOS u ON sr.correo_estudiante=u.correo " +
            "JOIN GRUPOS g ON g.id_grupo=sr.id_grupo JOIN MATERIAS m ON m.clave_materia=g.clave_materia " +
            "ORDER BY sr.fecha_solicitud DESC";
        try(Connection c=MySQL.conectar();PreparedStatement p=c.prepareStatement(sql);ResultSet r=p.executeQuery()){
            while(r.next()){
                Map<String,String> m=new HashMap<>();m.put("id",r.getString(1));
                m.put("correo",r.getString(2));m.put("nombre",r.getString(3)+" "+r.getString(4));
                m.put("estado",r.getString(5));m.put("materia",r.getString(6));
                m.put("grupo",r.getString(7));m.put("ruta_hoja_pago","");out.add(m);
            }
        }catch(SQLException ex){throw new IllegalStateException(ex);}return out;
    }
    /** Cancela una sola solicitud, su carga asociada para el mismo grupo/periodo y el acceso al horario. */
    public void eliminarSolicitud(long id){
        try(Connection c=MySQL.conectar()){
            c.setAutoCommit(false);
            try{
                String correo;int grupo,periodo;
                try(PreparedStatement p=c.prepareStatement("SELECT correo_estudiante,id_grupo,id_periodo FROM SOLICITUD_REINSCRIPCION WHERE id_solicitud=? FOR UPDATE")){
                    p.setLong(1,id);try(ResultSet r=p.executeQuery()){
                        if(!r.next())throw new IllegalArgumentException("Solicitud inexistente");
                        correo=r.getString(1);grupo=r.getInt(2);periodo=r.getInt(3);
                    }
                }
                try(PreparedStatement p=c.prepareStatement("DELETE FROM SOLICITUD_REINSCRIPCION WHERE id_solicitud=?")){
                    p.setLong(1,id);p.executeUpdate();
                }
                try(PreparedStatement p=c.prepareStatement("DELETE FROM CARGA_ACADEMICA WHERE correo_estudiante=? AND id_grupo=? AND id_periodo=?")){
                    p.setString(1,correo);p.setInt(2,grupo);p.setInt(3,periodo);p.executeUpdate();
                }
                c.commit();
            }catch(Exception ex){c.rollback();throw ex;}
        }catch(Exception ex){throw new IllegalStateException("No se eliminó la reinscripción: "+ex.getMessage(),ex);}
    }
    public void subirHojaDePago(String correo,String ruta){
        try(Connection c=MySQL.conectar();PreparedStatement p=c.prepareStatement("UPDATE SOLICITUD_REINSCRIPCION SET estado='HOJA ENVIADA' WHERE correo_estudiante=?")){
            p.setString(1,correo);if(p.executeUpdate()==0)throw new IllegalArgumentException("El alumno no tiene solicitudes");
        }catch(SQLException ex){throw new IllegalStateException(ex);}
    }
    public Map<String,String> verificarEstadoInscripcion(String correo){
        String sql="SELECT sr.estado,d.archivo_nombre FROM SOLICITUD_REINSCRIPCION sr " +
            "LEFT JOIN REINSCRIPCION_DOCUMENTOS d ON d.correo_estudiante=sr.correo_estudiante " +
            "WHERE sr.correo_estudiante=? ORDER BY sr.fecha_solicitud DESC,sr.id_solicitud DESC LIMIT 1";
        try(Connection c=MySQL.conectar();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,correo);
            try(ResultSet r=p.executeQuery()){
                if(!r.next()){
                    com.optem.logic.OfflineReadCache.save(correo,"estado_reinscripcion","null");
                    return null;
                }
                Map<String,String> m=new HashMap<>();
                m.put("correo",correo);m.put("estado",r.getString(1));
                m.put("nombre_archivo",Objects.toString(r.getString(2),""));
                m.put("archivo_disponible",r.getString(2)==null?"0":"1");
                com.optem.logic.OfflineReadCache.save(correo,"estado_reinscripcion",new com.google.gson.Gson().toJson(m));
                return m;
            }
        }catch(SQLException ex){
            String local=com.optem.logic.OfflineReadCache.load(correo,"estado_reinscripcion");
            if(local==null || "null".equals(local))return null;
            java.lang.reflect.Type tipo=new com.google.gson.reflect.TypeToken<Map<String,String>>(){}.getType();
            return new com.google.gson.Gson().fromJson(local,tipo);
        }
    }
    /** Recupera únicamente el PDF del estudiante activo desde el BLOB de MySQL. */
    public byte[] obtenerHojaDePago(String correo){
        if(correo==null || correo.isBlank() || !correo.equalsIgnoreCase(MockDB.sesion()))
            throw new IllegalArgumentException("Solo puedes consultar documentos de tu cuenta activa.");
        String sql="SELECT d.archivo_pdf FROM REINSCRIPCION_DOCUMENTOS d " +
            "WHERE d.correo_estudiante=? AND EXISTS(SELECT 1 FROM SOLICITUD_REINSCRIPCION sr " +
            "WHERE sr.correo_estudiante=d.correo_estudiante AND sr.estado='HOJA ENVIADA')";
        try(Connection c=MySQL.conectar();PreparedStatement p=c.prepareStatement(sql)){
            p.setString(1,correo);
            try(ResultSet r=p.executeQuery()){
                if(!r.next())throw new IllegalArgumentException(
                    "Administración todavía no ha enviado un PDF para esta cuenta.");
                byte[] contenido=r.getBytes(1);
                if(contenido==null || contenido.length<5 || contenido.length>20_000_000 ||
                   contenido[0]!='%' || contenido[1]!='P' || contenido[2]!='D' || contenido[3]!='F')
                    throw new IllegalStateException("El PDF de reinscripción está vacío o dañado.");
                return contenido;
            }
        }catch(SQLException ex){throw new IllegalStateException(
            "No se pudo abrir el PDF de reinscripción: "+ex.getMessage(),ex);}
    }
    public List<BloqueHorario> procesarSeleccion(String materia, String seleccion, String color) {
        List<BloqueHorario> bloques = new ArrayList<>();
        if (seleccion == null || seleccion.contains("No cursar")) return bloques;
        String nombreCorto = materia.split("\\(")[0].trim();
        for (Franja f : leerFranjas(seleccion)) {
            int fila = (f.inicio / 60) - 7;
            int filaFinal = ((f.fin + 59) / 60) - 7;
            int duracion = filaFinal - fila;
            if (fila < 0 || duracion <= 0 || fila + duracion > 13) continue;
            bloques.add(new BloqueHorario(nombreCorto, f.dia, fila, duracion, color));
        }
        return bloques;
    }

    /** Valida horarios originales a precisión de minutos (no sólo celdas de la vista). */
    public boolean hayEmpalmeReal(Map<String,String> seleccion) {
        List<Franja> ocupadas = new ArrayList<>();
        for (String opcion : seleccion.values()) {
            if (opcion == null || opcion.contains("No cursar")) continue;
            List<Franja> f = leerFranjas(opcion);
            if (f.isEmpty() || seEmpalma(ocupadas, f)) return true;
            ocupadas.addAll(f);
        }
        return false;
    }

    public Map<String,List<String>> getMapaCurricularEstudiante(){
        Map<String,List<String>> mapa=new LinkedHashMap<>();
        for(int i=1;i<=10;i++)mapa.put("Periodo "+i,new ArrayList<>());
        for(MateriaDTO m:getMateriasDisponibles()){
            if(m.categoria.startsWith("SEMESTRE ")){
                String periodo="Periodo "+m.categoria.substring(9);
                mapa.computeIfAbsent(periodo,k->new ArrayList<>()).add(m.titulo+"|transparent");
            }
        }
        return mapa;
    }
}
