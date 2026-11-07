package com.gerenciador.servico;

/** Dados brutos (texto) recebidos da tela. A validação e a conversão acontecem no serviço. */
public record DadosTarefa(String titulo, String descricao, String prioridade, String status, String prazo) {
}
