package com.gerenciador.web;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Montagem das respostas HTTP, com os cabeçalhos de segurança comuns. */
final class Resposta {

    private Resposta() {
    }

    static void json(HttpExchange ex, int status, String json) throws IOException {
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        enviar(ex, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    static void enviar(HttpExchange ex, int status, String tipo, byte[] corpo) throws IOException {
        Headers cabecalhos = ex.getResponseHeaders();
        cabecalhos.set("Content-Type", tipo);
        cabecalhos.set("X-Content-Type-Options", "nosniff");
        cabecalhos.set("Referrer-Policy", "no-referrer");

        boolean semCorpo = corpo == null || corpo.length == 0 || status == 204
                || "HEAD".equals(ex.getRequestMethod());
        if (semCorpo) {
            ex.sendResponseHeaders(status, -1);
            return;
        }
        ex.sendResponseHeaders(status, corpo.length);
        try (OutputStream saida = ex.getResponseBody()) {
            saida.write(corpo);
        }
    }
}
