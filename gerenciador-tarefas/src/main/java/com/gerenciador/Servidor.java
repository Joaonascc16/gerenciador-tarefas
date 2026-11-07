package com.gerenciador;

import com.gerenciador.repositorio.ArquivoTarefaRepositorio;
import com.gerenciador.servico.TarefaServico;
import com.gerenciador.web.ApiHandler;
import com.gerenciador.web.EstaticoHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Monta e controla o servidor HTTP (usa o servidor embutido do JDK, sem bibliotecas externas). */
public final class Servidor {

    private final HttpServer http;
    private final ExecutorService executor;

    private Servidor(HttpServer http, ExecutorService executor) {
        this.http = http;
        this.executor = executor;
    }

    /** @param porta use 0 para o sistema escolher uma porta livre (útil em testes) */
    public static Servidor iniciar(String host, int porta, Path arquivoDeDados) throws IOException {
        TarefaServico servico = new TarefaServico(new ArquivoTarefaRepositorio(arquivoDeDados));

        HttpServer http = HttpServer.create(new InetSocketAddress(host, porta), 0);
        http.createContext("/api/", new ApiHandler(servico));
        http.createContext("/", new EstaticoHandler());

        ExecutorService executor = Executors.newFixedThreadPool(8);
        http.setExecutor(executor);
        http.start();
        return new Servidor(http, executor);
    }

    public int porta() {
        return http.getAddress().getPort();
    }

    public void parar() {
        http.stop(0);
        executor.shutdown();
    }
}
