package com.gerenciador.servico;

/** Recurso inexistente (vira HTTP 404). */
public class NaoEncontradoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
