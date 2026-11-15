package com.optem.logic;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.Locale;
import java.util.prefs.Preferences;
import com.optem.ui.DashboardView;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Cliente asíncrono OPTEM IA: sin SQL ni clave Gemini dentro de JavaFX. */
public final class OptemIAClient {
    private OptemIAClient() {}
    private static final HttpClient CLIENT=HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6)).build();
    /** Compatibilidad con llamadas existentes: usa el usuario activo de OPTEM. */
    public static CompletableFuture<String> preguntar(String pregunta) {
        String correo = Preferences.userNodeForPackage(DashboardView.class)
                .get("LAST_ACTIVE_ACCOUNT", "");
        return preguntar(correo, pregunta);
    }

    /** Modo demostración: la identidad viene de la sesión local validada por el login. */
    public static CompletableFuture<String> preguntar(String correo, String pregunta) {
        if (correo == null || correo.isBlank())
            return CompletableFuture.failedFuture(new IllegalStateException("No hay cuenta activa"));
        String correoNormalizado = correo.trim().toLowerCase(Locale.ROOT);
        String url=System.getenv().getOrDefault("OPTEM_IA_URL", "http://100.67.119.35:8765/chat");
        String token=System.getenv("OPTEM_IA_TOKEN");
        if(token==null || token.isBlank())
            return CompletableFuture.failedFuture(new IllegalStateException("Falta OPTEM_IA_TOKEN"));
        JsonObject payload=new JsonObject();
        payload.addProperty("correo", correoNormalizado);
        payload.addProperty("mensaje", pregunta);
        HttpRequest request=HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(35))
            .header("Content-Type","application/json")
            .header("Authorization","Bearer "+ token)
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
        return CLIENT.sendAsync(request,HttpResponse.BodyHandlers.ofString())
            .thenApply(response->{
                if(response.statusCode()!=200)
                    throw new IllegalStateException("Error HTTP "+response.statusCode());
                return JsonParser.parseString(response.body()).getAsJsonObject()
                    .get("respuesta").getAsString();
            });
    }
}
