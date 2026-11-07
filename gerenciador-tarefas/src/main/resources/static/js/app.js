'use strict';

(() => {
  // ---------------------------------------------------------------- constantes
  const COLUNAS = ['A_FAZER', 'EM_ANDAMENTO', 'CONCLUIDA'];

  const ROTULO_STATUS = {
    A_FAZER: 'A fazer',
    EM_ANDAMENTO: 'Em andamento',
    CONCLUIDA: 'Concluída',
  };

  const ROTULO_PRIORIDADE = { BAIXA: 'baixa', MEDIA: 'média', ALTA: 'alta' };

  // Ações disponíveis em cada cartão, de acordo com a coluna em que ele está.
  const TRANSICOES = {
    A_FAZER: [{ rotulo: 'Iniciar', para: 'EM_ANDAMENTO', principal: true }],
    EM_ANDAMENTO: [
      { rotulo: 'Concluir', para: 'CONCLUIDA', principal: true },
      { rotulo: 'Pausar', para: 'A_FAZER', principal: false },
    ],
    CONCLUIDA: [{ rotulo: 'Reabrir', para: 'EM_ANDAMENTO', principal: false }],
  };

  const TEXTO_VAZIO = {
    A_FAZER: 'Nada a fazer. Use “Nova tarefa” para adicionar.',
    EM_ANDAMENTO: 'Nada em andamento. Arraste um cartão para cá ou use “Iniciar”.',
    CONCLUIDA: 'Nenhuma tarefa concluída ainda.',
  };

  // ---------------------------------------------------------------- elementos da página
  const resumoTexto = document.getElementById('resumo-texto');
  const progresso = document.getElementById('progresso');
  const busca = document.getElementById('busca');
  const filtroPrioridade = document.getElementById('filtro-prioridade');
  const botaoLimpar = document.getElementById('btn-limpar');
  const botaoNova = document.getElementById('btn-nova');
  const avisos = document.getElementById('avisos');

  const dialogo = document.getElementById('dialogo');
  const dialogoTitulo = document.getElementById('dialogo-titulo');
  const formulario = document.getElementById('form-tarefa');
  const erroForm = document.getElementById('erro-form');
  const botaoSalvar = document.getElementById('btn-salvar');
  const botaoCancelar = document.getElementById('btn-cancelar');
  const contadorDescricao = document.getElementById('contador-descricao');
  const campos = {
    titulo: document.getElementById('campo-titulo'),
    descricao: document.getElementById('campo-descricao'),
    prioridade: document.getElementById('campo-prioridade'),
    status: document.getElementById('campo-status'),
    prazo: document.getElementById('campo-prazo'),
  };

  const colunas = {};
  for (const status of COLUNAS) {
    const secao = document.querySelector(`.coluna[data-status="${status}"]`);
    colunas[status] = {
      secao,
      lista: secao.querySelector('[data-lista]'),
      contador: secao.querySelector('[data-contador]'),
    };
  }

  // ---------------------------------------------------------------- estado
  let tarefas = [];
  let editandoId = null; // null = criando uma tarefa nova
  let idArrastado = null; // id do cartão que está sendo arrastado
  let sequencia = 0; // descarta respostas antigas quando o usuário digita rápido
  let temporizadorBusca = null;

  // ---------------------------------------------------------------- utilidades
  function el(tag, atributos = {}, ...filhos) {
    const no = document.createElement(tag);
    for (const [nome, valor] of Object.entries(atributos)) {
      if (nome === 'class') {
        no.className = valor;
      } else {
        no.setAttribute(nome, valor);
      }
    }
    // append() com texto cria nós de texto: nunca interpreta HTML (evita XSS).
    no.append(...filhos);
    return no;
  }

  function plural(n, singular, pluralTexto) {
    return `${n} ${n === 1 ? singular : pluralTexto}`;
  }

  function hojeISO() {
    const d = new Date();
    const mes = String(d.getMonth() + 1).padStart(2, '0');
    const dia = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${mes}-${dia}`;
  }

  function formatarData(iso) {
    const [ano, mes, dia] = iso.split('-');
    return `${dia}/${mes}/${ano}`;
  }

  function filtrosAtivos() {
    return busca.value.trim() !== '' || filtroPrioridade.value !== '';
  }

  function avisar(mensagem, tipo = 'ok') {
    const aviso = el('div', { class: `aviso aviso--${tipo}` }, mensagem);
    while (avisos.children.length >= 3) {
      avisos.firstElementChild.remove(); // mantém no máximo 3 avisos na tela
    }
    avisos.append(aviso);
    setTimeout(() => aviso.remove(), tipo === 'erro' ? 7000 : 3500);
  }

  // ---------------------------------------------------------------- comunicação com a API
  async function api(metodo, url, dados) {
    const opcoes = { method: metodo, headers: { Accept: 'application/json' } };
    if (dados) {
      opcoes.headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
      opcoes.body = new URLSearchParams(dados).toString();
    }

    let resposta;
    try {
      resposta = await fetch(url, opcoes);
    } catch (e) {
      throw new Error('Não foi possível falar com o servidor. Verifique se ele está em execução.');
    }

    if (resposta.status === 204) {
      return null;
    }

    let corpo = null;
    try {
      corpo = await resposta.json();
    } catch (e) {
      // resposta sem JSON: usa a mensagem genérica abaixo
    }

    if (!resposta.ok) {
      throw new Error((corpo && corpo.erro) || `Erro ${resposta.status} ao falar com o servidor.`);
    }
    return corpo;
  }

  async function carregar() {
    const minha = ++sequencia;

    const parametros = new URLSearchParams();
    if (busca.value.trim()) parametros.set('q', busca.value.trim());
    if (filtroPrioridade.value) parametros.set('prioridade', filtroPrioridade.value);
    const consulta = parametros.toString();

    botaoLimpar.hidden = !filtrosAtivos();

    try {
      const [lista, resumo] = await Promise.all([
        api('GET', '/api/tarefas' + (consulta ? `?${consulta}` : '')),
        api('GET', '/api/resumo'),
      ]);
      if (minha !== sequencia) return;
      tarefas = lista;
      renderizarResumo(resumo);
      renderizarQuadro();
    } catch (e) {
      if (minha !== sequencia) return;
      resumoTexto.textContent = 'Não foi possível carregar as tarefas.';
      avisar(e.message, 'erro');
    }
  }

  // ---------------------------------------------------------------- desenho da tela
  function renderizarResumo(r) {
    let texto;
    if (r.total === 0) {
      texto = 'Nenhuma tarefa ainda. Crie a primeira para começar.';
    } else {
      texto = `${plural(r.total, 'tarefa', 'tarefas')}: ${r.aFazer} a fazer, ` +
        `${r.emAndamento} em andamento e ${plural(r.concluidas, 'concluída', 'concluídas')}.`;
      if (r.atrasadas > 0) {
        texto += r.atrasadas === 1 ? ' 1 está atrasada.' : ` ${r.atrasadas} estão atrasadas.`;
      }
    }
    resumoTexto.textContent = texto;

    const quantidades = { A_FAZER: r.aFazer, EM_ANDAMENTO: r.emAndamento, CONCLUIDA: r.concluidas };
    for (const parte of progresso.querySelectorAll('[data-segmento]')) {
      parte.style.setProperty('--v', String(quantidades[parte.dataset.segmento]));
    }
    progresso.setAttribute(
      'aria-label',
      r.total === 0 ? 'Nenhuma tarefa cadastrada' : `${r.concluidas} de ${r.total} tarefas concluídas`
    );
  }

  function renderizarQuadro() {
    const filtrando = filtrosAtivos();
    for (const status of COLUNAS) {
      const coluna = colunas[status];
      const daColuna = tarefas.filter((t) => t.status === status);
      coluna.contador.textContent = String(daColuna.length);
      if (daColuna.length === 0) {
        coluna.lista.replaceChildren(criarVazio(status, filtrando));
      } else {
        coluna.lista.replaceChildren(...daColuna.map(criarCartao));
      }
    }
  }

  function criarVazio(status, filtrando) {
    const texto = filtrando ? 'Nenhuma tarefa nesta coluna com esses filtros.' : TEXTO_VAZIO[status];
    return el('li', { class: 'vazio' }, texto);
  }

  function descreverPrazo(t) {
    if (!t.prazo) return null;
    const data = formatarData(t.prazo);
    if (t.status === 'CONCLUIDA') return { texto: `Prazo ${data}`, classe: '' };
    if (t.atrasada) return { texto: `Venceu em ${data}`, classe: 'prazo--atrasado' };
    if (t.prazo === hojeISO()) return { texto: 'Vence hoje', classe: 'prazo--hoje' };
    return { texto: `Prazo ${data}`, classe: '' };
  }

  function criarCartao(t) {
    const prioridade = t.prioridade.toLowerCase();
    const classes = ['cartao', `cartao--${prioridade}`];
    if (t.status === 'CONCLUIDA') classes.push('cartao--concluida');

    const cartao = el('li', { class: classes.join(' '), draggable: 'true' });
    cartao.append(el('h3', { class: 'cartao__titulo' }, t.titulo));

    if (t.descricao) {
      cartao.append(el('p', { class: 'cartao__descricao', title: t.descricao }, t.descricao));
    }

    const meta = el('div', { class: 'cartao__meta' });
    meta.append(el('span', { class: `etiqueta etiqueta--${prioridade}` }, `Prioridade ${ROTULO_PRIORIDADE[t.prioridade]}`));
    const prazo = descreverPrazo(t);
    if (prazo) {
      meta.append(el('span', { class: `prazo ${prazo.classe}`.trim() }, prazo.texto));
    }
    cartao.append(meta);

    const acoes = el('div', { class: 'cartao__acoes' });
    for (const transicao of TRANSICOES[t.status]) {
      const botao = el(
        'button',
        {
          type: 'button',
          class: `botao botao--pequeno${transicao.principal ? ' botao--avancar' : ''}`,
          'aria-label': `${transicao.rotulo}: ${t.titulo}`,
        },
        transicao.rotulo
      );
      botao.addEventListener('click', () => mover(t, transicao.para));
      acoes.append(botao);
    }

    const botaoEditar = el(
      'button',
      { type: 'button', class: 'botao botao--pequeno', 'aria-label': `Editar: ${t.titulo}` },
      'Editar'
    );
    botaoEditar.addEventListener('click', () => abrirDialogo(t));

    const botaoExcluir = el(
      'button',
      { type: 'button', class: 'botao botao--pequeno botao--perigo', 'aria-label': `Excluir: ${t.titulo}` },
      'Excluir'
    );
    botaoExcluir.addEventListener('click', () => excluir(t));

    acoes.append(botaoEditar, botaoExcluir);
    cartao.append(acoes);

    cartao.addEventListener('dragstart', (e) => {
      idArrastado = t.id;
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', String(t.id));
      cartao.classList.add('cartao--arrastando');
    });
    cartao.addEventListener('dragend', () => {
      idArrastado = null;
      cartao.classList.remove('cartao--arrastando');
      limparDestinos();
    });

    return cartao;
  }

  function limparDestinos() {
    for (const status of COLUNAS) {
      colunas[status].secao.classList.remove('coluna--destino');
    }
  }

  // ---------------------------------------------------------------- ações
  async function mover(t, status) {
    try {
      await api('PATCH', `/api/tarefas/${t.id}/status`, { status });
      await carregar();
      avisar(`Tarefa movida para ${ROTULO_STATUS[status]}.`);
    } catch (e) {
      avisar(e.message, 'erro');
    }
  }

  async function excluir(t) {
    if (!window.confirm(`Excluir a tarefa “${t.titulo}”? Esta ação não pode ser desfeita.`)) {
      return;
    }
    try {
      await api('DELETE', `/api/tarefas/${t.id}`);
      await carregar();
      avisar('Tarefa excluída.');
    } catch (e) {
      avisar(e.message, 'erro');
    }
  }

  // ---------------------------------------------------------------- diálogo de criar/editar
  function atualizarContador() {
    contadorDescricao.textContent = `${campos.descricao.value.length} de 500 caracteres`;
  }

  function mostrarErroForm(mensagem) {
    erroForm.textContent = mensagem;
    erroForm.hidden = false;
  }

  function esconderErroForm() {
    erroForm.textContent = '';
    erroForm.hidden = true;
  }

  function abrirDialogo(tarefa) {
    editandoId = tarefa ? tarefa.id : null;
    dialogoTitulo.textContent = tarefa ? 'Editar tarefa' : 'Nova tarefa';

    formulario.reset();
    campos.titulo.value = tarefa ? tarefa.titulo : '';
    campos.descricao.value = tarefa ? tarefa.descricao : '';
    campos.prioridade.value = tarefa ? tarefa.prioridade : 'MEDIA';
    campos.status.value = tarefa ? tarefa.status : 'A_FAZER';
    campos.prazo.value = tarefa && tarefa.prazo ? tarefa.prazo : '';

    esconderErroForm();
    atualizarContador();
    botaoSalvar.disabled = false;
    dialogo.showModal();
  }

  async function salvar(evento) {
    evento.preventDefault();
    esconderErroForm();

    const dados = {
      titulo: campos.titulo.value,
      descricao: campos.descricao.value,
      prioridade: campos.prioridade.value,
      status: campos.status.value,
      prazo: campos.prazo.value,
    };
    const criando = editandoId === null;

    botaoSalvar.disabled = true;
    try {
      if (criando) {
        await api('POST', '/api/tarefas', dados);
      } else {
        await api('PUT', `/api/tarefas/${editandoId}`, dados);
      }
      dialogo.close();
      await carregar();
      avisar(criando ? 'Tarefa criada.' : 'Tarefa salva.');
    } catch (e) {
      mostrarErroForm(e.message);
    } finally {
      botaoSalvar.disabled = false;
    }
  }

  // ---------------------------------------------------------------- inicialização
  function configurarArrastar() {
    for (const status of COLUNAS) {
      const secao = colunas[status].secao;

      secao.addEventListener('dragover', (e) => {
        if (idArrastado === null) return;
        e.preventDefault(); // necessário para permitir o "soltar"
        e.dataTransfer.dropEffect = 'move';
        secao.classList.add('coluna--destino');
      });

      secao.addEventListener('dragleave', (e) => {
        if (!secao.contains(e.relatedTarget)) {
          secao.classList.remove('coluna--destino');
        }
      });

      secao.addEventListener('drop', (e) => {
        if (idArrastado === null) return;
        e.preventDefault();
        const id = idArrastado;
        idArrastado = null;
        secao.classList.remove('coluna--destino');
        const tarefa = tarefas.find((t) => t.id === id);
        if (tarefa && tarefa.status !== status) {
          mover(tarefa, status);
        }
      });
    }
  }

  function iniciar() {
    botaoNova.addEventListener('click', () => abrirDialogo(null));
    botaoCancelar.addEventListener('click', () => dialogo.close());
    formulario.addEventListener('submit', salvar);
    campos.descricao.addEventListener('input', atualizarContador);

    busca.addEventListener('input', () => {
      clearTimeout(temporizadorBusca);
      temporizadorBusca = setTimeout(carregar, 250);
    });
    filtroPrioridade.addEventListener('change', carregar);
    botaoLimpar.addEventListener('click', () => {
      busca.value = '';
      filtroPrioridade.value = '';
      carregar();
    });

    configurarArrastar();
    carregar();
  }

  iniciar();
})();
