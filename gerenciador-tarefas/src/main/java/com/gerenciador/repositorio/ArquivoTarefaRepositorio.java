package com.gerenciador.repositorio;

import com.gerenciador.modelo.Prioridade;
import com.gerenciador.modelo.Status;
import com.gerenciador.modelo.Tarefa;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Guarda as tarefas em um arquivo de texto (uma tarefa por linha, campos separados por TAB).
 * Os dados ficam em memória e o arquivo inteiro é regravado a cada alteração.
 * A gravação é feita em um arquivo temporário que depois substitui o original,
 * para nunca deixar o arquivo pela metade.
 */
public class ArquivoTarefaRepositorio implements TarefaRepositorio {

    private static final int CAMPOS = 8;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Path arquivo;
    private final Map<Long, Tarefa> tarefas = new TreeMap<>();
    private long proximoId = 1;

    public ArquivoTarefaRepositorio(Path arquivo) {
        this.arquivo = arquivo.toAbsolutePath();
        carregar();
    }

    @Override
    public synchronized List<Tarefa> listarTodas() {
        return new ArrayList<>(tarefas.values());
    }

    @Override
    public synchronized Optional<Tarefa> buscarPorId(long id) {
        return Optional.ofNullable(tarefas.get(id));
    }

    @Override
    public synchronized Tarefa salvar(Tarefa tarefa) {
        Tarefa paraSalvar = tarefa.id() == 0 ? tarefa.comId(proximoId) : tarefa;
        Tarefa anterior = tarefas.put(paraSalvar.id(), paraSalvar);
        try {
            gravar();
        } catch (RuntimeException e) {
            // Se não conseguiu gravar no disco, desfaz a mudança em memória.
            if (anterior == null) {
                tarefas.remove(paraSalvar.id());
            } else {
                tarefas.put(paraSalvar.id(), anterior);
            }
            throw e;
        }
        proximoId = Math.max(proximoId, paraSalvar.id() + 1);
        return paraSalvar;
    }

    @Override
    public synchronized boolean excluir(long id) {
        Tarefa removida = tarefas.remove(id);
        if (removida == null) {
            return false;
        }
        try {
            gravar();
        } catch (RuntimeException e) {
            tarefas.put(id, removida);
            throw e;
        }
        return true;
    }

    // ---------------------------------------------------------------- persistência

    private void carregar() {
        try {
            Path pasta = arquivo.getParent();
            if (pasta != null) {
                Files.createDirectories(pasta);
            }
            if (!Files.exists(arquivo)) {
                return;
            }
            int numero = 0;
            for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                numero++;
                if (linha.isBlank()) {
                    continue;
                }
                try {
                    Tarefa tarefa = lerLinha(linha);
                    tarefas.put(tarefa.id(), tarefa);
                    proximoId = Math.max(proximoId, tarefa.id() + 1);
                } catch (RuntimeException e) {
                    throw new IllegalStateException(
                            "Arquivo de dados inválido na linha " + numero + " de " + arquivo + ": " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler " + arquivo, e);
        }
    }

    private void gravar() {
        List<String> linhas = new ArrayList<>();
        for (Tarefa tarefa : tarefas.values()) {
            linhas.add(paraLinha(tarefa));
        }
        Path temporario = arquivo.resolveSibling(arquivo.getFileName() + ".tmp");
        try {
            Files.write(temporario, linhas, StandardCharsets.UTF_8);
            try {
                Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporario, arquivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar " + arquivo, e);
        }
    }

    private static String paraLinha(Tarefa t) {
        return String.join("\t",
                String.valueOf(t.id()),
                escapar(t.titulo()),
                escapar(t.descricao()),
                t.prioridade().name(),
                t.status().name(),
                t.prazo() == null ? "" : t.prazo().toString(),
                DATA_HORA.format(t.criadaEm()),
                DATA_HORA.format(t.atualizadaEm()));
    }

    private static Tarefa lerLinha(String linha) {
        String[] c = linha.split("\t", -1);
        if (c.length != CAMPOS) {
            throw new IllegalArgumentException("esperava " + CAMPOS + " campos, encontrou " + c.length);
        }
        return new Tarefa(
                Long.parseLong(c[0]),
                desescapar(c[1]),
                desescapar(c[2]),
                Prioridade.valueOf(c[3]),
                Status.valueOf(c[4]),
                c[5].isEmpty() ? null : LocalDate.parse(c[5]),
                LocalDateTime.parse(c[6]),
                LocalDateTime.parse(c[7]));
    }

    /** Protege \ TAB e quebras de linha, que têm significado no formato do arquivo. */
    private static String escapar(String texto) {
        StringBuilder sb = new StringBuilder(texto.length() + 8);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String desescapar(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            i++;
            if (i >= texto.length()) {
                throw new IllegalArgumentException("sequência de escape incompleta");
            }
            char seguinte = texto.charAt(i);
            switch (seguinte) {
                case '\\' -> sb.append('\\');
                case 't' -> sb.append('\t');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                default -> throw new IllegalArgumentException("sequência de escape inválida: \\" + seguinte);
            }
        }
        return sb.toString();
    }
}
