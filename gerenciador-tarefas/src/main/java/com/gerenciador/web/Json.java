package com.gerenciador.web;

import com.gerenciador.modelo.Resumo;
import com.gerenciador.modelo.Tarefa;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Gera JSON na mão, para o projeto não depender de bibliotecas externas. */
final class Json {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private Json() {
    }

    static String de(Tarefa t) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"id\":").append(t.id());
        sb.append(",\"titulo\":").append(texto(t.titulo()));
        sb.append(",\"descricao\":").append(texto(t.descricao()));
        sb.append(",\"prioridade\":").append(texto(t.prioridade().name()));
        sb.append(",\"status\":").append(texto(t.status().name()));
        sb.append(",\"prazo\":").append(t.prazo() == null ? "null" : texto(t.prazo().toString()));
        sb.append(",\"atrasada\":").append(t.atrasada());
        sb.append(",\"criadaEm\":").append(texto(DATA_HORA.format(t.criadaEm())));
        sb.append(",\"atualizadaEm\":").append(texto(DATA_HORA.format(t.atualizadaEm())));
        return sb.append('}').toString();
    }

    static String deLista(List<Tarefa> tarefas) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < tarefas.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(de(tarefas.get(i)));
        }
        return sb.append(']').toString();
    }

    static String de(Resumo r) {
        return "{\"total\":" + r.total()
                + ",\"aFazer\":" + r.aFazer()
                + ",\"emAndamento\":" + r.emAndamento()
                + ",\"concluidas\":" + r.concluidas()
                + ",\"atrasadas\":" + r.atrasadas() + "}";
    }

    static String erro(String mensagem) {
        return "{\"erro\":" + texto(mensagem) + "}";
    }

    /** Texto entre aspas, com os caracteres especiais protegidos. */
    private static String texto(String valor) {
        StringBuilder sb = new StringBuilder(valor.length() + 2);
        sb.append('"');
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
