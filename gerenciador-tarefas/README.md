# Gerenciador de Tarefas

Quadro de tarefas com três colunas (A fazer, Em andamento, Concluída).
Backend em **Java puro** (só o JDK, sem Maven, sem Spring, sem bibliotecas externas) e interface em **HTML, CSS e JavaScript**.

## Como rodar

Requisito: **JDK 17 ou superior** (`javac -version` deve funcionar).

| Sistema | Comando |
|---|---|
| Linux / macOS | `./executar.sh` |
| Windows | `executar.bat` (dois cliques ou pelo terminal) |

Depois abra **http://localhost:8080**. Para usar outra porta: `./executar.sh 9090`.
Para parar, `Ctrl+C`.

Os dados ficam em `dados/tarefas.tsv` (criado automaticamente). Apague o arquivo para começar do zero.

### Pela IDE (IntelliJ)

1. `File > Open` e escolha a pasta do projeto (o `pom.xml` será importado como projeto Maven, sem dependências).
2. Abra `src/main/java/com/gerenciador/Main.java` e clique em Run.

### Com Maven (opcional)

```
mvn package
java -jar target/gerenciador-tarefas-1.0.0.jar
```

## O que a interface faz

- Criar, editar e excluir tarefas (título, descrição, prioridade, situação, prazo).
- Mover entre colunas arrastando o cartão ou pelos botões Iniciar, Concluir, Pausar e Reabrir.
- Buscar por título ou descrição (ignora maiúsculas e acentos) e filtrar por prioridade.
- Barra de progresso e resumo no topo, com aviso de tarefas atrasadas.
- Funciona no celular; navegação por teclado e leitores de tela.

## Estrutura

```
src/main/java/com/gerenciador/
  Main.java                  entrada: lê porta/host e inicia o servidor
  Servidor.java              monta o servidor HTTP do JDK
  modelo/                    Tarefa, Prioridade, Status, Resumo
  repositorio/               TarefaRepositorio (interface) e ArquivoTarefaRepositorio
  servico/                   TarefaServico: validação, filtros, ordenação, resumo
  web/                       ApiHandler (REST), EstaticoHandler (HTML/CSS/JS), Json, Formulario
src/main/resources/static/   index.html, css/style.css, js/app.js, favicon.svg
src/test/java/               TestesApi: testes de ponta a ponta (sem JUnit)
```

Arquitetura em camadas: **web → serviço → repositório**. Para usar MySQL em vez de arquivo, crie uma
classe que implemente `TarefaRepositorio` (com JDBC) e troque a linha `new ArquivoTarefaRepositorio(...)` em `Servidor.java`.

## API

Envia dados como formulário (`application/x-www-form-urlencoded`) e responde JSON.

| Método | Rota | O que faz |
|---|---|---|
| GET | `/api/tarefas?q=&prioridade=&status=` | Lista (filtros opcionais) |
| POST | `/api/tarefas` | Cria (`titulo` obrigatório) |
| GET | `/api/tarefas/{id}` | Busca uma |
| PUT | `/api/tarefas/{id}` | Atualiza (descrição e prazo em branco limpam o campo) |
| PATCH | `/api/tarefas/{id}/status` | Muda só a situação (`status`) |
| DELETE | `/api/tarefas/{id}` | Exclui (204) |
| GET | `/api/resumo` | Contagens para o cabeçalho |

Valores: `prioridade` = `BAIXA`, `MEDIA`, `ALTA`; `status` = `A_FAZER`, `EM_ANDAMENTO`, `CONCLUIDA`; `prazo` = `AAAA-MM-DD`.
Limites: título até 100 caracteres, descrição até 500.
Erros voltam como `{"erro":"mensagem"}` com o código HTTP adequado (400, 404, 405, 413, 500).

Exemplo:

```
curl -X POST -d "titulo=Estudar Java&prioridade=ALTA&prazo=2026-12-01" http://localhost:8080/api/tarefas
```

## Testes

```
./testar.sh        (Linux/macOS)
testar.bat         (Windows)
```

Sobe o servidor numa porta livre, com um arquivo temporário, e confere rotas, validações, filtros, persistência,
criações simultâneas e proteção contra acesso a arquivos fora de `static/`.

## Segurança (resumo)

- O servidor aceita conexões só da própria máquina por padrão (`127.0.0.1`). Para liberar a rede: `./executar.sh 8080 0.0.0.0`.
- A interface nunca insere texto do usuário como HTML (`textContent`), o que evita XSS.
- Cabeçalho `Content-Security-Policy` restringe de onde a página carrega scripts e estilos.
- Só são servidos arquivos de `static/` com extensões conhecidas; caminhos com `..` são recusados.
- Não há login: use apenas em ambiente local ou de estudo.

## Observação sobre as fontes

A página pede as fontes *Bricolage Grotesque* e *IBM Plex Sans* ao Google Fonts. Sem internet, ela usa as fontes do sistema e continua funcionando normalmente.
