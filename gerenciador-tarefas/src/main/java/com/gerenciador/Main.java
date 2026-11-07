package com.gerenciador;

import java.io.IOException;
import java.net.BindException;
import java.nio.file.Path;

/**
 * Ponto de entrada.
 *
 * <pre>
 * java -cp out com.gerenciador.Main [porta] [host]
 * </pre>
 *
 * Variável de ambiente opcional: TAREFAS_ARQUIVO (caminho do arquivo de dados; padrão dados/tarefas.tsv).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int porta = 8080;
        if (args.length > 0) {
            try {
                porta = Integer.parseInt(args[0]);
                if (porta < 1 || porta > 65535) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                System.err.println("Porta inválida: " + args[0] + ". Use um número de 1 a 65535.");
                System.exit(1);
            }
        }
        // Por padrão só aceita conexões desta máquina. Passe outro host (ex.: 0.0.0.0) para liberar a rede.
        String host = args.length > 1 ? args[1] : "127.0.0.1";

        String caminhoDados = System.getenv("TAREFAS_ARQUIVO");
        Path arquivo = Path.of(caminhoDados == null || caminhoDados.isBlank() ? "dados/tarefas.tsv" : caminhoDados);

        try {
            Servidor servidor = Servidor.iniciar(host, porta, arquivo);
            Runtime.getRuntime().addShutdownHook(new Thread(servidor::parar));
            System.out.println("Gerenciador de tarefas em http://localhost:" + servidor.porta());
            System.out.println("Dados salvos em " + arquivo.toAbsolutePath());
            System.out.println("Para encerrar, pressione Ctrl+C.");
        } catch (BindException e) {
            System.err.println("A porta " + porta + " já está em uso. Tente outra: java -cp out com.gerenciador.Main 8081");
            System.exit(1);
        } catch (IOException | RuntimeException e) {
            System.err.println("Não foi possível iniciar: " + e.getMessage());
            System.exit(1);
        }
    }
}
