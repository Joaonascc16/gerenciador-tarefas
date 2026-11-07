package com.gerenciador.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Entrega os arquivos da interface (HTML, CSS, JS) que ficam em src/main/resources/static.
 * Os arquivos são lidos do classpath, então funciona tanto na pasta quanto dentro de um .jar.
 */
public class EstaticoHandler implements HttpHandler {

    private static final String PASTA = "/static";

    private static final Map<String, String> TIPOS = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "json", "application/json; charset=utf-8",
            "png", "image/png",
            "ico", "image/x-icon");

    /** Só permite carregar CSS/fontes do próprio servidor e do Google Fonts. */
    private static final String POLITICA_DE_CONTEUDO = "default-src 'self'; "
            + "style-src 'self' https://fonts.googleapis.com; "
            + "font-src https://fonts.gstatic.com; "
            + "img-src 'self' data:; "
            + "base-uri 'none'; form-action 'self'; frame-ancestors 'none'";

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String metodo = ex.getRequestMethod();
            if (!metodo.equals("GET") && !metodo.equals("HEAD")) {
                ex.getResponseHeaders().set("Allow", "GET, HEAD");
                naoEncontrado(ex, 405, "Método não permitido.");
                return;
            }

            String caminho = ex.getRequestURI().getPath();
            if (caminho.equals("/")) {
                caminho = "/index.html";
            }

            String tipo = tipoDe(caminho);
            byte[] conteudo = tipo == null ? null : carregar(caminho);
            if (conteudo == null) {
                naoEncontrado(ex, 404, "Página não encontrada.");
                return;
            }

            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.getResponseHeaders().set("Content-Security-Policy", POLITICA_DE_CONTEUDO);
            Resposta.enviar(ex, 200, tipo, conteudo);
        } finally {
            ex.close();
        }
    }

    /** Tipo do arquivo pela extensão; {@code null} se o caminho for suspeito ou a extensão não for permitida. */
    private static String tipoDe(String caminho) {
        if (!caminho.startsWith("/") || caminho.contains("..") || caminho.contains("\\") || caminho.indexOf('\0') >= 0) {
            return null;
        }
        int barra = caminho.lastIndexOf('/');
        int ponto = caminho.lastIndexOf('.');
        if (ponto < barra + 2) { // sem extensão, ou arquivo começando com ponto
            return null;
        }
        return TIPOS.get(caminho.substring(ponto + 1).toLowerCase());
    }

    private static byte[] carregar(String caminho) throws IOException {
        try (InputStream entrada = EstaticoHandler.class.getResourceAsStream(PASTA + caminho)) {
            return entrada == null ? null : entrada.readAllBytes();
        }
    }

    private static void naoEncontrado(HttpExchange ex, int status, String mensagem) throws IOException {
        Resposta.enviar(ex, status, "text/plain; charset=utf-8", mensagem.getBytes(StandardCharsets.UTF_8));
    }
}
