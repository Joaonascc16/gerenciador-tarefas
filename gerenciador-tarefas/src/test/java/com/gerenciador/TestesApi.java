package com.gerenciador;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Testes de ponta a ponta, sem JUnit: sobem o servidor de verdade numa porta livre,
 * usam um arquivo de dados temporário e fazem requisições HTTP reais.
 *
 * Como rodar (depois de compilar o projeto na pasta out/):
 *   javac -encoding UTF-8 -cp out -d out-test src/test/java/com/gerenciador/TestesApi.java
 *   java -cp out:out-test com.gerenciador.TestesApi        (no Windows, troque : por ;)
 */
public final class TestesApi {

    private static final HttpClient CLIENTE = HttpClient.newHttpClient();
    private static int verificacoes = 0;
    private static final List<String> falhas = new ArrayList<>();

    private record Resp(int status, String corpo, HttpResponse<String> bruta) {
        String cabecalho(String nome) {
            return bruta.headers().firstValue(nome).orElse("");
        }
    }

    private static String base;

    public static void main(String[] args) throws Exception {
        // Garante acentos corretos no console, independentemente da configuração do sistema.
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        Path pasta = Files.createTempDirectory("tarefas-teste");
        Path arquivo = pasta.resolve("dados").resolve("tarefas.tsv");

        Servidor servidor = Servidor.iniciar("127.0.0.1", 0, arquivo);
        base = "http://127.0.0.1:" + servidor.porta();
        try {
            testarArquivosEstaticos();
            testarValidacoes();
            testarCrud();
            testarFiltrosEOrdem();
            testarResumoEAtraso();
            testarMetodosEIds();
            testarConcorrencia();
            testarCorpoGrande();
        } finally {
            servidor.parar();
        }

        testarPersistencia(arquivo);
        testarArquivoCorrompido(pasta);

        System.out.println();
        System.out.println(verificacoes + " verificações, " + falhas.size() + " falha(s).");
        for (String falha : falhas) {
            System.out.println("  FALHOU: " + falha);
        }
        System.exit(falhas.isEmpty() ? 0 : 1);
    }

    // ------------------------------------------------------------------ cenários

    private static void testarArquivosEstaticos() throws Exception {
        Resp indice = get("/");
        verificar(indice.status() == 200, "GET / deve responder 200");
        verificar(indice.corpo().contains("Gerenciador de Tarefas"), "GET / deve devolver o index.html");
        verificar(indice.cabecalho("Content-Type").startsWith("text/html"), "index.html com Content-Type html");
        verificar(indice.cabecalho("Content-Security-Policy").contains("default-src 'self'"), "CSP presente");

        Resp css = get("/css/style.css");
        verificar(css.status() == 200 && css.cabecalho("Content-Type").startsWith("text/css"), "style.css servido como css");
        Resp js = get("/js/app.js");
        verificar(js.status() == 200 && js.cabecalho("Content-Type").startsWith("text/javascript"), "app.js servido como javascript");
        verificar(get("/favicon.svg").status() == 200, "favicon.svg servido");

        verificar(get("/nao-existe.css").status() == 404, "arquivo inexistente -> 404");
        verificar(get("/css").status() == 404, "pasta -> 404");
        verificar(get("/%2e%2e/%2e%2e/etc/passwd").status() == 404, "path traversal bloqueado");
        verificar(get("/css/%2e%2e/index.html").status() == 404, "path traversal com .. bloqueado");
        verificar(get("/com/gerenciador/Main.class").status() == 404, "arquivos .class não são servidos");
        verificar(enviar("POST", "/", "").status() == 405, "POST em arquivo estático -> 405");
        verificar(enviar("HEAD", "/", null).status() == 200, "HEAD / -> 200");
    }

    private static void testarValidacoes() throws Exception {
        Resp semTitulo = enviar("POST", "/api/tarefas", form("titulo", "   "));
        verificar(semTitulo.status() == 400, "título em branco -> 400");
        verificar(semTitulo.corpo().contains("O título é obrigatório."), "mensagem de título obrigatório");
        verificar(semTitulo.cabecalho("Content-Type").startsWith("application/json"), "erro é JSON");

        verificar(enviar("POST", "/api/tarefas", form("titulo", "x".repeat(101))).status() == 400, "título > 100 -> 400");
        verificar(enviar("POST", "/api/tarefas", form("titulo", "x".repeat(100))).status() == 201, "título com 100 caracteres é aceito");
        verificar(enviar("POST", "/api/tarefas", form("titulo", "ok", "descricao", "d".repeat(501))).status() == 400, "descrição > 500 -> 400");
        verificar(enviar("POST", "/api/tarefas", form("titulo", "ok", "prioridade", "URGENTE")).status() == 400, "prioridade inválida -> 400");
        verificar(enviar("POST", "/api/tarefas", form("titulo", "ok", "status", "FEITA")).status() == 400, "status inválido -> 400");
        verificar(enviar("POST", "/api/tarefas", form("titulo", "ok", "prazo", "31/12/2026")).status() == 400, "data fora do formato -> 400");
        verificar(enviar("POST", "/api/tarefas", form("titulo", "ok", "prazo", "2026-02-31")).status() == 400, "data inexistente -> 400");
        verificar(enviar("POST", "/api/tarefas", "titulo=%E0%A4%A").status() == 400, "percent-encoding quebrado -> 400");
        verificar(enviar("POST", "/api/tarefas", "").status() == 400, "corpo vazio -> 400");
    }

    private static void testarCrud() throws Exception {
        String descricao = "linha 1\nlinha \"2\" com \\ barra\ttab e emoji 😀 e acentuação: ção";
        Resp criada = enviar("POST", "/api/tarefas", form(
                "titulo", "  Estudar   Java \n web ", "descricao", descricao,
                "prioridade", "alta", "prazo", "2099-12-31"));
        verificar(criada.status() == 201, "POST válido -> 201");
        verificar(criada.cabecalho("Location").startsWith("/api/tarefas/"), "Location presente");
        verificar(criada.corpo().contains("\"titulo\":\"Estudar Java web\""), "título normalizado (espaços colapsados)");
        verificar(criada.corpo().contains("\"prioridade\":\"ALTA\""), "prioridade em minúsculas é aceita");
        verificar(criada.corpo().contains("\"status\":\"A_FAZER\""), "status padrão A_FAZER");
        verificar(criada.corpo().contains("\"prazo\":\"2099-12-31\""), "prazo salvo");
        verificar(criada.corpo().contains("\"atrasada\":false"), "prazo futuro não está atrasado");
        verificar(criada.corpo().contains("linha 1\\nlinha \\\"2\\\" com \\\\ barra\\ttab e emoji 😀 e acentuação: ção"),
                "descrição com aspas, barra, tab, quebra de linha, emoji e acentos vai escapada corretamente no JSON");
        long id = extrairId(criada.corpo());

        Resp padrao = enviar("POST", "/api/tarefas", form("titulo", "Só o título"));
        verificar(padrao.corpo().contains("\"prioridade\":\"MEDIA\"") && padrao.corpo().contains("\"prazo\":null"),
                "padrões: prioridade MEDIA e prazo null");

        Resp lida = get("/api/tarefas/" + id);
        verificar(lida.status() == 200 && lida.corpo().contains("\"id\":" + id), "GET por id");

        Resp atualizada = enviar("PUT", "/api/tarefas/" + id, form(
                "titulo", "Estudar Spring", "descricao", "", "prazo", "", "status", "EM_ANDAMENTO"));
        verificar(atualizada.status() == 200, "PUT -> 200");
        verificar(atualizada.corpo().contains("\"titulo\":\"Estudar Spring\""), "PUT altera o título");
        verificar(atualizada.corpo().contains("\"descricao\":\"\"") && atualizada.corpo().contains("\"prazo\":null"),
                "PUT com descrição e prazo em branco limpa os campos");
        verificar(atualizada.corpo().contains("\"prioridade\":\"ALTA\""), "PUT sem prioridade mantém a atual");
        verificar(atualizada.corpo().contains("\"status\":\"EM_ANDAMENTO\""), "PUT altera o status");
        verificar(enviar("PUT", "/api/tarefas/" + id, form("titulo", "")).status() == 400, "PUT inválido -> 400");

        Resp movida = enviar("PATCH", "/api/tarefas/" + id + "/status", form("status", "CONCLUIDA"));
        verificar(movida.status() == 200 && movida.corpo().contains("\"status\":\"CONCLUIDA\""), "PATCH status");
        verificar(enviar("PATCH", "/api/tarefas/" + id + "/status", form("status", "")).status() == 400, "PATCH sem status -> 400");
        verificar(enviar("PATCH", "/api/tarefas/" + id + "/status", form("status", "XYZ")).status() == 400, "PATCH status inválido -> 400");
        verificar(enviar("PATCH", "/api/tarefas/99999/status", form("status", "A_FAZER")).status() == 404, "PATCH em id inexistente -> 404");

        Resp excluida = enviar("DELETE", "/api/tarefas/" + id, null);
        verificar(excluida.status() == 204 && excluida.corpo().isEmpty(), "DELETE -> 204 sem corpo");
        verificar(get("/api/tarefas/" + id).status() == 404, "GET após DELETE -> 404");
        verificar(enviar("DELETE", "/api/tarefas/" + id, null).status() == 404, "DELETE repetido -> 404");
        verificar(enviar("PUT", "/api/tarefas/" + id, form("titulo", "x")).status() == 404, "PUT em tarefa excluída -> 404");
    }

    private static void testarFiltrosEOrdem() throws Exception {
        enviar("POST", "/api/tarefas", form("titulo", "Revisão do relatório", "prioridade", "BAIXA"));
        enviar("POST", "/api/tarefas", form("titulo", "Comprar café", "descricao", "Para a REUNIÃO de sexta", "prioridade", "ALTA"));

        Resp porAcento = get("/api/tarefas?q=revisao");
        verificar(porAcento.corpo().contains("Revisão do relatório"), "busca ignora acentos (revisao -> Revisão)");
        Resp porMaiuscula = get("/api/tarefas?q=" + codificar("REVISÃO"));
        verificar(porMaiuscula.corpo().contains("Revisão do relatório"), "busca ignora maiúsculas");
        Resp porDescricao = get("/api/tarefas?q=reuniao");
        verificar(porDescricao.corpo().contains("Comprar café") && !porDescricao.corpo().contains("Revisão"), "busca na descrição");
        verificar(get("/api/tarefas?q=zzzinexistente").corpo().equals("[]"), "busca sem resultado -> []");

        Resp altas = get("/api/tarefas?prioridade=ALTA");
        verificar(altas.corpo().contains("Comprar café") && !altas.corpo().contains("\"prioridade\":\"BAIXA\""), "filtro por prioridade");
        verificar(get("/api/tarefas?prioridade=XX").status() == 400, "filtro de prioridade inválido -> 400");
        verificar(get("/api/tarefas?status=A_FAZER").status() == 200, "filtro por status");
        verificar(get("/api/tarefas?status=XX").status() == 400, "filtro de status inválido -> 400");

        String lista = get("/api/tarefas").corpo();
        verificar(lista.indexOf("\"prioridade\":\"ALTA\"") < lista.indexOf("\"prioridade\":\"MEDIA\"")
                        && lista.indexOf("\"prioridade\":\"MEDIA\"") < lista.indexOf("\"prioridade\":\"BAIXA\""),
                "lista ordenada por prioridade (ALTA, MEDIA, BAIXA)");
    }

    private static void testarResumoEAtraso() throws Exception {
        Resp antes = get("/api/resumo");
        verificar(antes.status() == 200, "GET /api/resumo");
        int atrasadasAntes = lerNumero(antes.corpo(), "atrasadas");
        int totalAntes = lerNumero(antes.corpo(), "total");

        Resp atrasada = enviar("POST", "/api/tarefas", form("titulo", "Prazo vencido", "prazo", "2000-01-01"));
        verificar(atrasada.corpo().contains("\"atrasada\":true"), "prazo no passado -> atrasada");
        long id = extrairId(atrasada.corpo());

        Resp meio = get("/api/resumo");
        verificar(lerNumero(meio.corpo(), "atrasadas") == atrasadasAntes + 1, "resumo conta a tarefa atrasada");
        verificar(lerNumero(meio.corpo(), "total") == totalAntes + 1, "resumo conta o total");

        Resp concluida = enviar("PATCH", "/api/tarefas/" + id + "/status", form("status", "CONCLUIDA"));
        verificar(concluida.corpo().contains("\"atrasada\":false"), "tarefa concluída deixa de ser atrasada");
        Resp depois = get("/api/resumo");
        verificar(lerNumero(depois.corpo(), "atrasadas") == atrasadasAntes, "resumo volta ao valor anterior");

        int soma = lerNumero(depois.corpo(), "aFazer") + lerNumero(depois.corpo(), "emAndamento") + lerNumero(depois.corpo(), "concluidas");
        verificar(soma == lerNumero(depois.corpo(), "total"), "aFazer + emAndamento + concluidas == total");
    }

    private static void testarMetodosEIds() throws Exception {
        Resp metodo = enviar("DELETE", "/api/tarefas", null);
        verificar(metodo.status() == 405 && metodo.cabecalho("Allow").contains("GET"), "DELETE na coleção -> 405 com Allow");
        verificar(enviar("POST", "/api/resumo", "").status() == 405, "POST em /api/resumo -> 405");
        verificar(enviar("PUT", "/api/tarefas/1/status", form("status", "A_FAZER")).status() == 405, "PUT em /status -> 405");
        verificar(get("/api/tarefas/abc").status() == 400, "id não numérico -> 400");
        verificar(get("/api/tarefas/0").status() == 400, "id zero -> 400");
        verificar(get("/api/tarefas/-5").status() == 400, "id negativo -> 400");
        verificar(get("/api/tarefas/99999999999999999999").status() == 400, "id enorme -> 400");
        verificar(get("/api/tarefas/99999").status() == 404, "id inexistente -> 404");
        verificar(get("/api/inexistente").status() == 404, "rota inexistente -> 404");
        verificar(get("/api/tarefas/1/2/3").status() == 404, "rota longa demais -> 404");
    }

    private static void testarConcorrencia() throws Exception {
        int antes = lerNumero(get("/api/resumo").corpo(), "total");
        List<CompletableFuture<HttpResponse<String>>> futuros = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            futuros.add(CLIENTE.sendAsync(requisicao("POST", "/api/tarefas", form("titulo", "Paralela " + i)),
                    HttpResponse.BodyHandlers.ofString()));
        }
        java.util.Set<Long> ids = new java.util.HashSet<>();
        boolean todasCriadas = true;
        for (CompletableFuture<HttpResponse<String>> futuro : futuros) {
            HttpResponse<String> r = futuro.get();
            todasCriadas &= r.statusCode() == 201;
            ids.add(extrairId(r.body()));
        }
        verificar(todasCriadas, "40 criações simultâneas responderam 201");
        verificar(ids.size() == 40, "40 criações simultâneas geraram 40 ids diferentes");
        verificar(lerNumero(get("/api/resumo").corpo(), "total") == antes + 40, "total correto após criações simultâneas");
    }

    private static void testarCorpoGrande() throws Exception {
        Resp grande = enviar("POST", "/api/tarefas", "titulo=" + "a".repeat(70 * 1024));
        verificar(grande.status() == 413, "corpo > 64 KB -> 413");
    }

    private static void testarPersistencia(Path arquivo) throws Exception {
        Servidor primeiro = Servidor.iniciar("127.0.0.1", 0, arquivo);
        base = "http://127.0.0.1:" + primeiro.porta();
        String antes = get("/api/tarefas").corpo();
        long maiorId = extrairMaiorId(antes);
        primeiro.parar();

        Servidor segundo = Servidor.iniciar("127.0.0.1", 0, arquivo);
        base = "http://127.0.0.1:" + segundo.porta();
        try {
            String depois = get("/api/tarefas").corpo();
            verificar(antes.equals(depois), "após reiniciar, a lista é idêntica (inclusive acentos, emoji e quebras de linha)");
            verificar(depois.contains("Revisão do relatório"), "dados com acento sobreviveram à reinicialização");

            Resp nova = enviar("POST", "/api/tarefas", form("titulo", "Depois de reiniciar"));
            verificar(extrairId(nova.corpo()) > maiorId, "ids não são reaproveitados após reiniciar");
        } finally {
            segundo.parar();
        }

        verificar(Files.exists(arquivo), "arquivo de dados existe");
        verificar(!Files.exists(arquivo.resolveSibling("tarefas.tsv.tmp")), "arquivo temporário não fica para trás");
    }

    private static void testarArquivoCorrompido(Path pasta) throws Exception {
        Path ruim = pasta.resolve("ruim.tsv");
        Files.writeString(ruim, "isto não é uma tarefa\n", StandardCharsets.UTF_8);
        try {
            Servidor.iniciar("127.0.0.1", 0, ruim).parar();
            verificar(false, "arquivo corrompido deveria impedir a inicialização");
        } catch (IllegalStateException e) {
            verificar(e.getMessage().contains("linha 1"), "arquivo corrompido: erro claro apontando a linha");
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static void verificar(boolean condicao, String descricao) {
        verificacoes++;
        if (condicao) {
            System.out.println("  ok  " + descricao);
        } else {
            System.out.println("  XX  " + descricao);
            falhas.add(descricao);
        }
    }

    private static Resp get(String caminho) throws Exception {
        return enviar("GET", caminho, null);
    }

    private static HttpRequest requisicao(String metodo, String caminho, String corpo) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + caminho));
        if (corpo == null) {
            b.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            b.method(metodo, HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8));
        }
        return b.build();
    }

    private static Resp enviar(String metodo, String caminho, String corpo) throws Exception {
        HttpResponse<String> r = CLIENTE.send(requisicao(metodo, caminho, corpo),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resp(r.statusCode(), r.body(), r);
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }

    private static String form(String... paresChaveValor) {
        Map<String, String> mapa = new LinkedHashMap<>();
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            mapa.put(paresChaveValor[i], paresChaveValor[i + 1]);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : mapa.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(codificar(e.getKey())).append('=').append(codificar(e.getValue()));
        }
        return sb.toString();
    }

    private static long extrairId(String json) {
        int i = json.indexOf("\"id\":") + 5;
        int f = i;
        while (Character.isDigit(json.charAt(f))) {
            f++;
        }
        return Long.parseLong(json.substring(i, f));
    }

    private static long extrairMaiorId(String jsonLista) {
        long maior = 0;
        int pos = 0;
        while ((pos = jsonLista.indexOf("{\"id\":", pos)) >= 0) {
            maior = Math.max(maior, extrairId(jsonLista.substring(pos)));
            pos += 6;
        }
        return maior;
    }

    private static int lerNumero(String json, String campo) {
        int i = json.indexOf("\"" + campo + "\":") + campo.length() + 3;
        int f = i;
        while (Character.isDigit(json.charAt(f))) {
            f++;
        }
        return Integer.parseInt(json.substring(i, f));
    }
}
