package com.gerenciador.servico;

/** Dados inválidos enviados pelo usuário (vira HTTP 400). */
public class ValidacaoException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ValidacaoException(String mensagem) {
        super(mensagem);
    }
}
