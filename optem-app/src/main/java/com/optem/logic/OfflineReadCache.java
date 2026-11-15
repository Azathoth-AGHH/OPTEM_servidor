package com.optem.logic;

import java.nio.file.*;
import java.sql.*;

/** Copia local de solo lectura, por cuenta y categoria, en SQLite. */
public final class OfflineReadCache {
    private OfflineReadCache() {}
    private static final Object LOCK = new Object();
    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".optem", "offline", "optem-cache.db");
    private static Connection open() throws SQLException {
        try { Files.createDirectories(FILE.getParent()); }
        catch (java.io.IOException e) { throw new SQLException("No se pudo crear el cache", e); }
        Connection c=DriverManager.getConnection("jdbc:sqlite:"+FILE);
        try(Statement s=c.createStatement()) {
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("CREATE TABLE IF NOT EXISTS read_snapshots (account TEXT NOT NULL, category TEXT NOT NULL, payload TEXT NOT NULL, updated_at TEXT NOT NULL, PRIMARY KEY(account,category))");
        }
        return c;
    }
    public static void save(String account, String category, String payload) {
        if(account==null || account.isBlank() || category==null || category.isBlank())return;
        synchronized(LOCK) {
            try(Connection c=open();PreparedStatement q=c.prepareStatement("INSERT INTO read_snapshots(account,category,payload,updated_at) VALUES(?,?,?,?) ON CONFLICT(account,category) DO UPDATE SET payload=excluded.payload,updated_at=excluded.updated_at")) {
                q.setString(1,account.toLowerCase(java.util.Locale.ROOT));q.setString(2,category);q.setString(3,payload);
                q.setString(4,java.time.Instant.now().toString());q.executeUpdate();
            }catch(SQLException ex){System.err.println("OPTEM: no se pudo almacenar cache de "+category+": "+ex.getMessage());}
        }
    }
    /** Devuelve null si no existe instantanea previa, nunca sustituye autorizacion. */
    public static String load(String account, String category) {
        if(account==null || account.isBlank())return null;
        synchronized(LOCK) {
            try(Connection c=open();PreparedStatement q=c.prepareStatement("SELECT payload FROM read_snapshots WHERE account=? AND category=?")) {
                q.setString(1,account.toLowerCase(java.util.Locale.ROOT));q.setString(2,category);
                try(ResultSet r=q.executeQuery()){return r.next()?r.getString(1):null;}
            }catch(SQLException ex){throw new IllegalStateException("Error leyendo datos locales",ex);}
        }
    }
}
