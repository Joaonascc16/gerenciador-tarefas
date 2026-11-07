package com.gerenciador.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Tarefa do sistema. Imutável: para "alterar" uma tarefa, crie uma nova com os valores desejados.
 * Uma tarefa ainda não salva tem {@code id == 0}.
 */
public record Tarefa(
        long id,
        String titulo,
        String descricao,
        Prioridade prioridade,
        Status status,
        LocalDate prazo,
        LocalDateTime criadaEm,
        LocalDateTime atualizadaEm) {

    public Tarefa {
        Objects.requireNonNull(titulo, "titulo");
        Objects.requireNonNull(descricao, "descricao");
        Objects.requireNonNull(prioridade, "prioridade");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(criadaEm, "criadaEm");
        Objects.requireNonNull(atualizadaEm, "atualizadaEm");
    }

    /** Uma tarefa está atrasada quando tem prazo anterior a hoje e ainda não foi concluída. */
    public boolean atrasada() {
        return prazo != null && status != Status.CONCLUIDA && prazo.isBefore(LocalDate.now());
    }

    public Tarefa comId(long novoId) {
        return new Tarefa(novoId, titulo, descricao, prioridade, status, prazo, criadaEm, atualizadaEm);
    }

    public Tarefa comStatus(Status novoStatus, LocalDateTime agora) {
        return new Tarefa(id, titulo, descricao, prioridade, novoStatus, prazo, criadaEm, agora);
    }
}
