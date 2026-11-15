
package com.optem.database;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Preferencias de notificaciones por usuario.
 * MySQL es la fuente principal.
 * La copia local permite lectura sin conexion.
 */
public final class NotificacionesDAO {

    private NotificacionesDAO() {}

    private static final Preferences CACHE =
        Preferences.userNodeForPackage(NotificacionesDAO.class);

    private static final Set<String> MODOS =
        Set.of("APAGADO", "PRIORITARIO", "COMPLETO");

    private static String clave(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException("Correo no valido");
        }

        try {
            String normalizado =
                correo.trim().toLowerCase(Locale.ROOT);

            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(normalizado.getBytes(StandardCharsets.UTF_8));

            return "notif_" + HexFormat.of().formatHex(hash);

        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String leer(String correo) {

        String q = "SELECT modo FROM OPTEM_PREF_NOTIFICACIONES "
                 + "WHERE correo_usuario=?";

        try (Connection c = MySQL.conectar();
             PreparedStatement p = c.prepareStatement(q)) {

            p.setString(1, correo);

            String modo = "COMPLETO";

            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    modo = r.getString(1);
                }
            }

            if (!MODOS.contains(modo)) {
                modo = "COMPLETO";
            }

            CACHE.put(clave(correo), modo);

            return modo;

        } catch (SQLException ex) {

            String modoLocal = CACHE.get(clave(correo), null);

            if (modoLocal != null && MODOS.contains(modoLocal)) {
                System.out.println(
                    "OPTEM: mostrando preferencias de notificaciones "
                    + "guardadas localmente (solo lectura)."
                );
                return modoLocal;
            }

            System.err.println(
                "OPTEM: sin preferencias locales; "
                + "utilizando COMPLETO temporalmente."
            );

            return "COMPLETO";
        }
    }

    public static void guardar(String correo, String modo) {

        if (!MODOS.contains(modo)) {
            throw new IllegalArgumentException("Modo invalido");
        }

        String q =
            "INSERT INTO OPTEM_PREF_NOTIFICACIONES"
            + "(correo_usuario,modo) VALUES(?,?) "
            + "ON DUPLICATE KEY UPDATE modo=VALUES(modo)";

        try (Connection c = MySQL.conectar();
             PreparedStatement p = c.prepareStatement(q)) {

            p.setString(1, correo);
            p.setString(2, modo);
            p.executeUpdate();

            CACHE.put(clave(correo), modo);

        } catch (SQLException ex) {
            throw new IllegalStateException(
                "No se pudo guardar la preferencia. "
                + "Se necesita conexion con MySQL.",
                ex
            );
        }
    }
}
