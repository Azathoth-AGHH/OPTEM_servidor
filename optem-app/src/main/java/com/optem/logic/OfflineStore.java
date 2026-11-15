package com.optem.logic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Almacén local persistente: SQLite privado por instalación y registros separados por correo. */
public final class OfflineStore {
    private OfflineStore() {}
    private static final Object LOCK = new Object();
    private static final Path DB_PATH = Paths.get(System.getProperty("user.home"), ".optem", "offline", "optem-cache.db");
    private static boolean ready;

    public record Note(String id, String titulo, String materia, String contenido, String fecha, String color) {}
    public record Operation(String account, String noteId, String kind, String titulo, String materia, String contenido, String color) {}

    private static Connection open() throws SQLException {
        try { Files.createDirectories(DB_PATH.getParent()); }
        catch (java.io.IOException ex) { throw new SQLException("No se pudo crear la carpeta local OPTEM", ex); }
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        try (Statement st = c.createStatement()) { st.execute("PRAGMA busy_timeout=5000"); }
        if (!ready) {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS note_cache (account TEXT NOT NULL, id TEXT NOT NULL, titulo TEXT NOT NULL, materia TEXT, contenido TEXT, fecha TEXT, color TEXT, pending INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(account,id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS outbox (account TEXT NOT NULL, note_id TEXT NOT NULL, kind TEXT NOT NULL, titulo TEXT, materia TEXT, contenido TEXT, color TEXT, PRIMARY KEY(account,note_id))");
            }
            ready = true;
        }
        return c;
    }

    public static void createOffline(String account, Note note) {
        if (account == null || account.isBlank()) throw new IllegalStateException("Sin usuario para guardar localmente");
        synchronized (LOCK) {
            try (Connection c = open()) {
                c.setAutoCommit(false);
                try {
                    cache(c,account,note,1,0);
                    queue(c,account,note.id(),"UPSERT",note);
                    c.commit();
                } catch (Exception e) { c.rollback(); throw e; }
            } catch (Exception e) { throw new IllegalStateException("No se pudo guardar el cambio local: " + e.getMessage(),e); }
        }
    }

    public static void removeOffline(String account, String id) {
        synchronized (LOCK) {
            try (Connection c = open()) {
                c.setAutoCommit(false);
                try {
                    try (PreparedStatement st=c.prepareStatement("UPDATE note_cache SET deleted=1,pending=1 WHERE account=? AND id=?")) {
                        st.setString(1,account);st.setString(2,id);st.executeUpdate();
                    }
                    queue(c,account,id,"DELETE",null);
                    c.commit();
                } catch (Exception e) { c.rollback(); throw e; }
            } catch (Exception e) { throw new IllegalStateException("No se pudo guardar la eliminación local",e); }
        }
    }

    private static void cache(Connection c,String a,Note n,int pending,int deleted) throws SQLException {
        try (PreparedStatement st=c.prepareStatement("INSERT INTO note_cache(account,id,titulo,materia,contenido,fecha,color,pending,deleted) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(account,id) DO UPDATE SET titulo=excluded.titulo,materia=excluded.materia,contenido=excluded.contenido,fecha=excluded.fecha,color=excluded.color,pending=excluded.pending,deleted=excluded.deleted")) {
            st.setString(1,a);st.setString(2,n.id());st.setString(3,n.titulo());st.setString(4,n.materia());st.setString(5,n.contenido());st.setString(6,n.fecha());st.setString(7,n.color());st.setInt(8,pending);st.setInt(9,deleted);st.executeUpdate();
        }
    }
    private static void queue(Connection c,String a,String id,String kind,Note n) throws SQLException {
        try(PreparedStatement st=c.prepareStatement("INSERT INTO outbox(account,note_id,kind,titulo,materia,contenido,color) VALUES(?,?,?,?,?,?,?) ON CONFLICT(account,note_id) DO UPDATE SET kind=excluded.kind,titulo=excluded.titulo,materia=excluded.materia,contenido=excluded.contenido,color=excluded.color")) {
            st.setString(1,a);st.setString(2,id);st.setString(3,kind);
            st.setString(4,n==null?null:n.titulo());st.setString(5,n==null?null:n.materia());st.setString(6,n==null?null:n.contenido());st.setString(7,n==null?null:n.color());st.executeUpdate();
        }
    }
    /** Refresca instantánea remota sin sobrescribir escrituras aún pendientes. */
    public static void refresh(String account,List<Note> remote) {
        synchronized (LOCK) {
            try (Connection c=open()) {
                c.setAutoCommit(false);
                try {
                    try(PreparedStatement st=c.prepareStatement("DELETE FROM note_cache WHERE account=? AND pending=0")) {st.setString(1,account);st.executeUpdate();}
                    for(Note n:remote) {
                        try(PreparedStatement st=c.prepareStatement("SELECT 1 FROM note_cache WHERE account=? AND id=? AND pending=1")){
                            st.setString(1,account);st.setString(2,n.id());try(ResultSet r=st.executeQuery()){if(r.next())continue;}
                        }
                        cache(c,account,n,0,0);
                    }
                    c.commit();
                } catch(Exception e){c.rollback();throw e;}
            } catch(Exception e) { System.err.println("OPTEM offline: no se pudo actualizar caché de notas: "+e.getMessage()); }
        }
    }
    public static List<Note> readNotes(String account) {
        synchronized (LOCK) {
            try (Connection c=open();PreparedStatement st=c.prepareStatement("SELECT id,titulo,materia,contenido,fecha,color FROM note_cache WHERE account=? AND deleted=0 ORDER BY fecha DESC")) {
                st.setString(1,account);
                List<Note> result=new ArrayList<>();
                try(ResultSet rs=st.executeQuery()){while(rs.next())result.add(new Note(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6)));}
                return result;
            } catch(SQLException e){throw new IllegalStateException("No se pudo leer la caché local",e);}
        }
    }
    public static List<Operation> pending() {
        synchronized (LOCK) {
            try(Connection c=open();PreparedStatement st=c.prepareStatement("SELECT account,note_id,kind,titulo,materia,contenido,color FROM outbox ORDER BY rowid")){
                List<Operation> result=new ArrayList<>();
                try(ResultSet rs=st.executeQuery()){while(rs.next())result.add(new Operation(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7)));}
                return result;
            }catch(SQLException e){throw new IllegalStateException("No se pudo revisar la cola local",e);}
        }
    }
    public static int pendingCount() {
        synchronized(LOCK){try(Connection c=open();Statement st=c.createStatement();ResultSet rs=st.executeQuery("SELECT COUNT(*) FROM outbox")){return rs.next()?rs.getInt(1):0;}catch(SQLException e){return 0;}}
    }
    public static void acknowledge(Operation op) {
        synchronized(LOCK) {
            try(Connection c=open()){
                c.setAutoCommit(false);
                try {
                    // Solo confirmar si el registro no cambió desde que empezó el envío.
                    try(PreparedStatement st=c.prepareStatement("DELETE FROM outbox WHERE account=? AND note_id=? AND kind=? AND COALESCE(titulo,'')=COALESCE(?,'') AND COALESCE(contenido,'')=COALESCE(?,'')")){
                        st.setString(1,op.account());st.setString(2,op.noteId());st.setString(3,op.kind());st.setString(4,op.titulo());st.setString(5,op.contenido());
                        if(st.executeUpdate()>0){
                            if("DELETE".equals(op.kind())){
                                try(PreparedStatement d=c.prepareStatement("DELETE FROM note_cache WHERE account=? AND id=?")){d.setString(1,op.account());d.setString(2,op.noteId());d.executeUpdate();}
                            } else {
                                try(PreparedStatement d=c.prepareStatement("UPDATE note_cache SET pending=0 WHERE account=? AND id=?")){d.setString(1,op.account());d.setString(2,op.noteId());d.executeUpdate();}
                            }
                        }
                    }
                    c.commit();
                } catch(Exception e){c.rollback();throw e;}
            }catch(Exception e){System.err.println("OPTEM offline: confirmación pendiente: "+e.getMessage());}
        }
    }
}
