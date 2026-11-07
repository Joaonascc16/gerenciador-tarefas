package com.gerenciador.servico;

import com.gerenciador.modelo.Prioridade;
import com.gerenciador.modelo.Resumo;
import com.gerenciador.modelo.Status;
import com.gerenciador.modelo.Tarefa;
import com.gerenciador.repositorio.TarefaRepositorio;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Regras de negócio: validação, filtros, ordenação e resumo. */
public class TarefaServico {

    public static final int MAX_TITULO = 100;
    public static final int MAX_DESCRICAO = 500;

    private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACOS = Pattern.compile("\\s+");

    /** Mais prioritárias primeiro; depois prazo mais próximo (sem prazo por último); depois mais antigas. */
    private static final Comparator<Tarefa> ORDEM = Comparator
            .comparing((Tarefa t) -> t.prioridade().ordinal()).reversed()
            .thenComparing(Tarefa::prazo, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
            .thenComparingLong(Tarefa::id);

    private final TarefaRepositorio repositorio;

    public TarefaServico(TarefaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    // ---------------------------------------------------------------- consultas

    public List<Tarefa> listar(String busca, String prioridade, String status) {
        String termo = busca == null ? "" : normalizar(busca.trim());
        Prioridade filtroPrioridade = lerPrioridade(prioridade, null);
        Status filtroStatus = lerStatus(status, null);

        return repositorio.listarTodas().stream()
                .filter(t -> filtroPrioridade == null || t.prioridade() == filtroPrioridade)
                .filter(t -> filtroStatus == null || t.status() == filtroStatus)
                .filter(t -> termo.isEmpty()
                        || normalizar(t.titulo()).contains(termo)
                        || normalizar(t.descricao()).contains(termo))
                .sorted(ORDEM)
                .toList();
    }

    public Tarefa buscar(long id) {
        return repositorio.buscarPorId(id)
                .orElseThrow(() -> new NaoEncontradoException("Tarefa não encontrada."));
    }

    public Resumo resumo() {
        int aFazer = 0;
        int emAndamento = 0;
        int concluidas = 0;
        int atrasadas = 0;
        List<Tarefa> todas = repositorio.listarTodas();
        for (Tarefa t : todas) {
            switch (t.status()) {
                case A_FAZER -> aFazer++;
                case EM_ANDAMENTO -> emAndamento++;
                case CONCLUIDA -> concluidas++;
            }
            if (t.atrasada()) {
                atrasadas++;
            }
        }
        return new Resumo(todas.size(), aFazer, emAndamento, concluidas, atrasadas);
    }

    // ---------------------------------------------------------------- alterações
    // synchronized: evita que duas requisições simultâneas leiam e gravem a mesma tarefa ao mesmo tempo.

    public synchronized Tarefa criar(DadosTarefa dados) {
        LocalDateTime agora = agora();
        Tarefa nova = new Tarefa(
                0,
                lerTitulo(dados.titulo()),
                lerDescricao(dados.descricao()),
                lerPrioridade(dados.prioridade(), Prioridade.MEDIA),
                lerStatus(dados.status(), Status.A_FAZER),
                lerPrazo(dados.prazo()),
                agora,
                agora);
        return repositorio.salvar(nova);
    }

    /**
     * Substitui os dados da tarefa. Título, descrição e prazo são sempre substituídos
     * (descrição ou prazo em branco limpam o campo); prioridade e status em branco mantêm o valor atual.
     */
    public synchronized Tarefa atualizar(long id, DadosTarefa dados) {
        Tarefa atual = buscar(id);
        Tarefa nova = new Tarefa(
                id,
                lerTitulo(dados.titulo()),
                lerDescricao(dados.descricao()),
                lerPrioridade(dados.prioridade(), atual.prioridade()),
                lerStatus(dados.status(), atual.status()),
                lerPrazo(dados.prazo()),
                atual.criadaEm(),
                agora());
        return repositorio.salvar(nova);
    }

    public synchronized Tarefa mudarStatus(long id, String status) {
        Status novo = lerStatus(status, null);
        if (novo == null) {
            throw new ValidacaoException("O status é obrigatório.");
        }
        Tarefa atual = buscar(id);
        return repositorio.salvar(atual.comStatus(novo, agora()));
    }

    public synchronized void excluir(long id) {
        if (!repositorio.excluir(id)) {
            throw new NaoEncontradoException("Tarefa não encontrada.");
        }
    }

    // ---------------------------------------------------------------- validação

    private static LocalDateTime agora() {
        return LocalDateTime.now().withNano(0);
    }

    private static String lerTitulo(String texto) {
        String titulo = texto == null ? "" : ESPACOS.matcher(texto.trim()).replaceAll(" ");
        if (titulo.isEmpty()) {
            throw new ValidacaoException("O título é obrigatório.");
        }
        if (titulo.length() > MAX_TITULO) {
            throw new ValidacaoException("O título deve ter no máximo " + MAX_TITULO + " caracteres.");
        }
        return titulo;
    }

    private static String lerDescricao(String texto) {
        String descricao = texto == null ? "" : texto.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (descricao.length() > MAX_DESCRICAO) {
            throw new ValidacaoException("A descrição deve ter no máximo " + MAX_DESCRICAO + " caracteres.");
        }
        return descricao;
    }

    private static Prioridade lerPrioridade(String texto, Prioridade padrao) {
        if (texto == null || texto.isBlank()) {
            return padrao;
        }
        try {
            return Prioridade.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidacaoException("Prioridade inválida. Use BAIXA, MEDIA ou ALTA.");
        }
    }

    private static Status lerStatus(String texto, Status padrao) {
        if (texto == null || texto.isBlank()) {
            return padrao;
        }
        try {
            return Status.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ValidacaoException("Status inválido. Use A_FAZER, EM_ANDAMENTO ou CONCLUIDA.");
        }
    }

    private static LocalDate lerPrazo(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ValidacaoException("Data inválida. Use o formato AAAA-MM-DD.");
        }
    }

    /** Minúsculas e sem acentos, para a busca encontrar "revisao" ao procurar "Revisão". */
    private static String normalizar(String texto) {
        String semAcentos = ACENTOS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return semAcentos.toLowerCase(Locale.ROOT);
    }
}
