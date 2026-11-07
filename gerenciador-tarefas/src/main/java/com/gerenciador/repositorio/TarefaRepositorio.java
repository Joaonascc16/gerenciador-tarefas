package com.gerenciador.repositorio;

import com.gerenciador.modelo.Tarefa;

import java.util.List;
import java.util.Optional;

/**
 * Contrato de acesso aos dados. Para trocar o arquivo por um banco (JDBC, JPA...),
 * basta criar outra implementação desta interface.
 */
public interface TarefaRepositorio {

    List<Tarefa> listarTodas();

    Optional<Tarefa> buscarPorId(long id);

    /** Salva a tarefa. Se {@code id == 0}, gera um novo id. Retorna a tarefa como foi salva. */
    Tarefa salvar(Tarefa tarefa);

    /** @return {@code true} se a tarefa existia e foi removida */
    boolean excluir(long id);
}
