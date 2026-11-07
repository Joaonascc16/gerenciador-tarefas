package com.gerenciador.web;

import com.gerenciador.modelo.Tarefa;
import com.gerenciador.servico.DadosTarefa;
import com.gerenciador.servico.NaoEncontradoException;
import com.gerenciador.servico.TarefaServico;
import com.gerenciador.servico.ValidacaoException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * API REST sob /api/. Recebe dados de formulário (application/x-www-form-urlencoded)
 * e responde sempre em JSON.
 *
 * <pre>
 * GET    /api/tarefas?q=&amp;prioridade=&amp;status=   lista (com filtros opcionais)
 * POST   /api/tarefas                          cria
 * GET    /api/tarefas/{id}                     busca uma
 * PUT    /api/tarefas/{id}                     atualiza
 * PATCH  /api/tarefas/{id}/status              muda só o status
 * DELETE /api/tarefas/{id}                     exclui
 * GET    /api/resumo                           contagens para o cabeçalho
 * </pre>
 */
public class ApiHandler implements HttpHandler {

    private static final String PREFIXO = "/api/";
    private static final int LIMITE_CORPO = 64 * 1024;

    private final TarefaServico servico;

    public ApiHandler(TarefaServico servico) {
        this.servico = servico;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            rotear(ex);
        } catch (ApiException e) {
            erro(ex, e.status(), e.getMessage());
        } catch (ValidacaoException e) {
            erro(ex, 400, e.getMessage());
        } catch (NaoEncontradoException e) {
            erro(ex, 404, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            erro(ex, 500, "Erro interno do servidor.");
        } finally {
            ex.close();
        }
    }

    private void rotear(HttpExchange ex) throws IOException {
        String metodo = ex.getRequestMethod();
        String[] partes = partesDoCaminho(ex.getRequestURI().getPath());

        if (partes.length == 1 && partes[0].equals("tarefas")) {
            switch (metodo) {
                case "GET" -> listar(ex);
                case "POST" -> criar(ex);
                default -> throw metodoNaoPermitido(ex, "GET, POST");
            }
        } else if (partes.length == 1 && partes[0].equals("resumo")) {
            if (!metodo.equals("GET")) {
                throw metodoNaoPermitido(ex, "GET");
            }
            Resposta.json(ex, 200, Json.de(servico.resumo()));
        } else if (partes.length == 2 && partes[0].equals("tarefas")) {
            long id = lerId(partes[1]);
            switch (metodo) {
                case "GET" -> Resposta.json(ex, 200, Json.de(servico.buscar(id)));
                case "PUT" -> atualizar(ex, id);
                case "DELETE" -> excluir(ex, id);
                default -> throw metodoNaoPermitido(ex, "GET, PUT, DELETE");
            }
        } else if (partes.length == 3 && partes[0].equals("tarefas") && partes[2].equals("status")) {
            if (!metodo.equals("PATCH")) {
                throw metodoNaoPermitido(ex, "PATCH");
            }
            mudarStatus(ex, lerId(partes[1]));
        } else {
            throw new ApiException(404, "Recurso não encontrado.");
        }
    }

    // ---------------------------------------------------------------- operações

    private void listar(HttpExchange ex) throws IOException {
        Map<String, String> filtros = Formulario.analisar(ex.getRequestURI().getRawQuery());
        List<Tarefa> tarefas = servico.listar(filtros.get("q"), filtros.get("prioridade"), filtros.get("status"));
        Resposta.json(ex, 200, Json.deLista(tarefas));
    }

    private void criar(HttpExchange ex) throws IOException {
        Tarefa criada = servico.criar(lerDados(ex));
        ex.getResponseHeaders().set("Location", PREFIXO + "tarefas/" + criada.id());
        Resposta.json(ex, 201, Json.de(criada));
    }

    private void atualizar(HttpExchange ex, long id) throws IOException {
        Resposta.json(ex, 200, Json.de(servico.atualizar(id, lerDados(ex))));
    }

    private void mudarStatus(HttpExchange ex, long id) throws IOException {
        Map<String, String> campos = Formulario.analisar(lerCorpo(ex));
        Resposta.json(ex, 200, Json.de(servico.mudarStatus(id, campos.get("status"))));
    }

    private void excluir(HttpExchange ex, long id) throws IOException {
        servico.excluir(id);
        Resposta.enviar(ex, 204, "application/json; charset=utf-8", null);
    }

    // ---------------------------------------------------------------- utilidades

    private static DadosTarefa lerDados(HttpExchange ex) throws IOException {
        Map<String, String> campos = Formulario.analisar(lerCorpo(ex));
        return new DadosTarefa(
                campos.get("titulo"),
                campos.get("descricao"),
                campos.get("prioridade"),
                campos.get("status"),
                campos.get("prazo"));
    }

    private static String lerCorpo(HttpExchange ex) throws IOException {
        try (InputStream entrada = ex.getRequestBody()) {
            byte[] bytes = entrada.readNBytes(LIMITE_CORPO + 1);
            if (bytes.length > LIMITE_CORPO) {
                throw new ApiException(413, "Requisição grande demais.");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static String[] partesDoCaminho(String caminho) {
        if (!caminho.startsWith(PREFIXO)) {
            return new String[0];
        }
        return Arrays.stream(caminho.substring(PREFIXO.length()).split("/"))
                .filter(parte -> !parte.isEmpty())
                .toArray(String[]::new);
    }

    private static long lerId(String texto) {
        long id;
        try {
            id = Long.parseLong(texto);
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Identificador inválido.");
        }
        if (id <= 0) {
            throw new ApiException(400, "Identificador inválido.");
        }
        return id;
    }

    private static ApiException metodoNaoPermitido(HttpExchange ex, String permitidos) {
        ex.getResponseHeaders().set("Allow", permitidos);
        return new ApiException(405, "Método não permitido para este recurso.");
    }

    private static void erro(HttpExchange ex, int status, String mensagem) throws IOException {
        Resposta.json(ex, status, Json.erro(mensagem));
    }
}
