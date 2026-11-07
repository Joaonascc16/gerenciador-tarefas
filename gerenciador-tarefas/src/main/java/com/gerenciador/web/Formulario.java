package com.gerenciador.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Lê dados no formato application/x-www-form-urlencoded (corpo de formulários e query string). */
final class Formulario {

    private Formulario() {
    }

    static Map<String, String> analisar(String texto) {
        Map<String, String> campos = new HashMap<>();
        if (texto == null || texto.isEmpty()) {
            return campos;
        }
        for (String par : texto.split("&")) {
            if (par.isEmpty()) {
                continue;
            }
            int igual = par.indexOf('=');
            String chave = igual < 0 ? par : par.substring(0, igual);
            String valor = igual < 0 ? "" : par.substring(igual + 1);
            campos.put(decodificar(chave), decodificar(valor));
        }
        return campos;
    }

    private static String decodificar(String texto) {
        try {
            return URLDecoder.decode(texto, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Dados mal formatados.");
        }
    }
}
