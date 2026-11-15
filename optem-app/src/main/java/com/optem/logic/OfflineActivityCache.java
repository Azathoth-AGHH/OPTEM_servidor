package com.optem.logic;

import com.optem.models.Actividad;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/**
 * Instantánea local de SOLO LECTURA de la agenda. No guarda contraseñas
 * ni sustituye la autorización del servidor. Los datos se separan por cuenta.
 */
public final class OfflineActivityCache {
    private OfflineActivityCache() {}
    private static final Object LOCK = new Object();
    private static final Path PATH = Paths.get(System.getProperty("user.home"),
            ".optem", "offline", "optem-cache.db");

    private static Connection open() throws SQLException {
        try { Files.createDirectories(PATH.getParent()); }
        catch (java.io.IOException e) { throw new SQLException("No se pudo crear la carpeta offline", e); }
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + PATH);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("CREATE TABLE IF NOT EXISTS activity_snapshot "
                    + "(account TEXT PRIMARY KEY, refreshed_at TEXT NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS activity_cache ("
                    + "account TEXT NOT NULL, id TEXT NOT NULL, titulo TEXT, tipo TEXT, "
                    + "descripcion TEXT, fecha TEXT, hora TEXT, estado TEXT, materia TEXT, "
                    + "PRIMARY KEY(account,id))");
        }
        return c;
    }

    /** Guarda también agendas vacías para distinguirlas de una cuenta nunca sincronizada. */
    public static void save(String account, List<Actividad> items) {
        requireAccount(account);
        synchronized (LOCK) {
            try (Connection c = open()) {
                c.setAutoCommit(false);
                try {
                    try (PreparedStatement p=c.prepareStatement(
                            "DELETE FROM activity_cache WHERE account=?")) {
                        p.setString(1, account); p.executeUpdate();
                    }
                    try (PreparedStatement p=c.prepareStatement(
                            "INSERT INTO activity_cache(account,id,titulo,tipo,descripcion,fecha,hora,estado,materia)"
                            + " VALUES(?,?,?,?,?,?,?,?,?)")) {
                        for (Actividad a : items) {
                            p.setString(1, account);
                            p.setString(2, a.getId());
                            p.setString(3, a.getNombre());
                            p.setString(4, a.getTipo());
                            p.setString(5, a.getDescripcion());
                            p.setString(6, a.getFecha() == null ? null : a.getFecha().toString());
                            p.setString(7, a.getHoraVencimiento() == null ? null : a.getHoraVencimiento().toString());
                            p.setString(8, a.getEstado());
                            p.setString(9, a.getMateria());
                            p.addBatch();
                        }
                        p.executeBatch();
                    }
                    try (PreparedStatement p=c.prepareStatement(
                            "INSERT INTO activity_snapshot(account,refreshed_at) VALUES(?,?) "
                            + "ON CONFLICT(account) DO UPDATE SET refreshed_at=excluded.refreshed_at")) {
                        p.setString(1, account); p.setString(2, java.time.Instant.now().toString());
                        p.executeUpdate();
                    }
                    c.commit();
                } catch (Exception e) { c.rollback(); throw e; }
            } catch (Exception e) { throw new IllegalStateException("No se pudo guardar la agenda local", e); }
        }
    }

    public static boolean hasSnapshot(String account) {
        if (account == null || account.isBlank()) return false;
        synchronized (LOCK) {
            try (Connection c=open(); PreparedStatement p=c.prepareStatement(
                    "SELECT 1 FROM activity_snapshot WHERE account=?")) {
                p.setString(1,account);
                try (ResultSet rs=p.executeQuery()) { return rs.next(); }
            } catch (SQLException e) {
                throw new IllegalStateException("No se pudo comprobar la agenda local",e);
            }
        }
    }

    public static List<Actividad> load(String account) {
        requireAccount(account);
        synchronized (LOCK) {
            List<Actividad> result = new ArrayList<>();
            try (Connection c=open(); PreparedStatement p=c.prepareStatement(
                    "SELECT id,titulo,tipo,descripcion,fecha,hora,estado,materia FROM activity_cache "
                    + "WHERE account=? ORDER BY fecha DESC,id DESC")) {
                p.setString(1,account);
                try (ResultSet r=p.executeQuery()) {
                    while (r.next()) {
                        String date=r.getString(5), hour=r.getString(6);
                        Actividad a=new Actividad(r.getString(1),r.getString(2),
                                r.getString(3),r.getString(4),
                                date==null?LocalDate.now():LocalDate.parse(date),
                                hour==null?LocalTime.MIDNIGHT:LocalTime.parse(hour),
                                r.getString(8));
                        a.setEstado(r.getString(7));
                        result.add(a);
                    }
                }
                return result;
            } catch (Exception e) {
                throw new IllegalStateException("No se pudo leer la agenda local",e);
            }
        }
    }

    private static void requireAccount(String account) {
        if (account == null || account.isBlank())
            throw new IllegalStateException("No hay cuenta activa para la agenda offline");
    }
}
