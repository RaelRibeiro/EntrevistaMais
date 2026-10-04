'use client';

import { useEffect, useState, use } from 'react';
import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';
import {
  observarEntrevista,
  observarRoteiros,
  observarPerguntas,
  observarRespostas,
  salvarResposta,
  atualizarEntrevista,
  salvarResumoCurriculo,
  reabrirEntrevista,
  observarVagas,
  anexarCurriculo,
  apagarCurriculoArquivo,
  urlDoCurriculo,
  observarExperiencias,
  salvarExperiencia,
  atualizarExperiencia,
  removerExperiencia,
} from '@/lib/firestore';
import type { Entrevista, Vaga, Roteiro, Pergunta, LinhaExperiencia } from '@/lib/tipos';
import { ROTULO_STATUS, ehFinalizado, StatusEntrevista } from '@/lib/tipos';
import { minutosParaHora, formatarTelefone } from '@/lib/horario';

type Props = { params: Promise<{ id: string }> };

type AbaEntrevista = 'CURRICULO' | 'ROTEIRO';

export default function PaginaEntrevista({ params }: Props) {
  const { id } = use(params);
  const { usuario } = useAuth();
  const uid = usuario?.uid ?? '';
  const [carregando, setCarregando] = useState(true);
  const [entrevista, setEntrevista] = useState<Entrevista | null>(null);
  const [vagas, setVagas] = useState<Vaga[]>([]);
  const [roteiros, setRoteiros] = useState<Roteiro[]>([]);
  const [roteiro, setRoteiro] = useState<Roteiro | null>(null);
  const [perguntas, setPerguntas] = useState<Pergunta[]>([]);
  const [respostas, setRespostas] = useState<Record<string, string>>({});
  const [aba, setAba] = useState<AbaEntrevista>('CURRICULO');
  const [mensagem, setMensagem] = useState('');
  const [confirmarFinalizacao, setConfirmarFinalizacao] = useState(false);

  useEffect(() => {
    if (!uid) return;
    setCarregando(true);
    // O observer do Firestore só entrega valor no primeiro snapshot, então é
    // ele quem desliga o "carregando". Sem isso a tela ficava em
    // "Carregando entrevista…" para sempre.
    return observarEntrevista(uid, id, (dados) => {
      setEntrevista(dados);
      setCarregando(false);
    });
  }, [uid, id]);

  useEffect(() => {
    if (!uid) return;
    return observarVagas(uid, setVagas);
  }, [uid]);

  useEffect(() => {
    if (!uid) return;
    return observarRoteiros(uid, setRoteiros);
  }, [uid]);

  // Roteiro da entrevista: o da vaga se existir, senão o padrão — igual ao app.
  useEffect(() => {
    if (!uid || roteiros.length === 0) return;
    const escolhido =
      roteiros.find((x) => x.vagaId === entrevista?.vagaId) ??
      roteiros.find((x) => x.padrao);
    setRoteiro(escolhido ?? null);
    if (!escolhido) {
      setPerguntas([]);
      return;
    }
    return observarPerguntas(uid, escolhido.id, setPerguntas);
  }, [uid, roteiros, entrevista?.vagaId]);

  useEffect(() => {
    if (!uid) return;
    return observarRespostas(uid, id, setRespostas);
  }, [uid, id]);

  if (carregando) return <p className="dica">Carregando entrevista…</p>;

  if (!entrevista) {
    return (
      <div className="cartao">
        <strong>Entrevista não encontrada.</strong>
        <p className="dica" style={{ margin: 0 }}>
          <Link href="/agenda">Voltar à agenda</Link>
        </p>
      </div>
    );
  }

  const vaga = vagas.find((v) => Number(v.id) === entrevista.vagaId);
  const finalizada = ehFinalizado(entrevista.status);
  const emAndamento = entrevista.status === 'EM_ANDAMENTO';
  // As respostas do roteiro viram registro depois que a entrevista terminou de
  // qualquer jeito — mesmo regra do app (`AbaRoteiro.somenteLeitura`).
  const somenteLeitura = !emAndamento && entrevista.status !== 'AGENDADA';

  /**
   * Roda uma transição de status e devolve a mensagem do erro do Firestore
   * para a tela, como o app faz pelos repositórios.
   */
  const acao = async (acaoInterna: () => Promise<void>, sucesso: string) => {
    try {
      await acaoInterna();
      setMensagem(sucesso);
    } catch {
      setMensagem('Não foi possível concluir a operação. Tente novamente.');
    }
  };

  const iniciar = () =>
    acao(
      async () => {
        // Não marca o horário como manual: no app iniciar a entrevista não
        // libera o candidato do recálculo da agenda, e no site isso deixava a
        // entrevista travada num horário errado e marcada como "alterado".
        await atualizarEntrevista(uid, id, {
          status: 'EM_ANDAMENTO',
          inicioReal: Date.now(),
        });
      },
      'Entrevista iniciada.',
    );

  const finalizar = () =>
    acao(
      async () => {
        const agora = Date.now();
        await atualizarEntrevista(uid, id, {
          status: 'CONCLUIDA',
          // O app garante os dois campos; sem o início não haveria como
          // calcular a duração real depois.
          inicioReal: entrevista.inicioReal ?? agora,
          fimReal: agora,
        });
      },
      'Entrevista concluída.',
    );

  const decidir = (status: StatusEntrevista, sucesso: string) =>
    acao(() => atualizarEntrevista(uid, id, { status }), sucesso);

  const reabrir = () =>
    acao(
      () => reabrirEntrevista(uid, id),
      'Candidato reaberto. Pode ajustar as respostas e iniciar de novo.',
    );

  const podeIniciar = !emAndamento && !finalizada;

  return (
    <div>
      <div className="cartao">
        <div className="secao-titulo">Candidato</div>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 10,
            flexWrap: 'wrap',
            marginBottom: 8,
          }}
        >
          <h1 className="titulo" style={{ margin: 0, flex: 1 }}>
            {entrevista.nome}
          </h1>
          <span className={`status ${entrevista.status.toLowerCase()}`}>
            {ROTULO_STATUS[entrevista.status]}
          </span>
        </div>
        <p className="dica" style={{ margin: 0 }}>
          {formatarTelefone(entrevista.telefone)}
        </p>
        <p className="dica" style={{ margin: 0 }}>
          {dataParaTexto(entrevista.data)} · {minutosParaHora(entrevista.inicioMinutos)} às{' '}
          {minutosParaHora(entrevista.inicioMinutos + entrevista.duracaoMinutos)}
        </p>
        {vaga ? (
          <p className="dica" style={{ margin: 0 }}>
            {vaga.titulo}
          </p>
        ) : null}
      </div>

      {emAndamento && (
        <CartaoCronometro
          inicioReal={entrevista.inicioReal}
          duracaoPrevistaMinutos={entrevista.duracaoMinutos}
        />
      )}

      {emAndamento && (
        <div className="cartao">
          <button className="botao" onClick={() => setConfirmarFinalizacao(true)}>
            Finalizar entrevista
          </button>
        </div>
      )}

      {podeIniciar && (
        <>
          <div className="cartao">
            <button className="botao" onClick={iniciar}>
              INICIAR
            </button>
          </div>
          <div className="cartao">
            <p className="dica" style={{ margin: 0 }}>
              Toque em Iniciar para começar. A entrevista só começa a contar o tempo a partir
              daí, e o horário real fica registrado.
            </p>
          </div>
        </>
      )}

      {finalizada && (
        <ResumoFinalizado
          entrevista={entrevista}
          aoAprovar={() => decidir('APROVADO', 'Candidato aprovado para a próxima fase.')}
          aoReprovar={() => decidir('REPROVADO', 'Candidato reprovado.')}
          aoEncerrar={() => decidir('ENCERRADA', 'Candidato encerrado.')}
          aoReabrir={reabrir}
        />
      )}

      <Abas
        aba={aba}
        aoTrocar={setAba}
        habilitada={emAndamento || finalizada}
      />

      {aba === 'CURRICULO' ? (
        <BlocoCurriculo uid={uid} entrevista={entrevista} aoMensagem={setMensagem} />
      ) : (
        <AbaRoteiro
          uid={uid}
          entrevista={entrevista}
          roteiro={roteiro}
          perguntas={perguntas}
          respostas={respostas}
          vaga={vaga}
          somenteLeitura={somenteLeitura}
          aoAlterar={(perguntaId, valor) => {
            void salvarResposta(uid, id, perguntaId, valor);
          }}
        />
      )}

      {confirmarFinalizacao && (
        <Dialogo
          titulo="Finalizar entrevista?"
          texto="O tempo total é registrado e o roteiro preenchido é salvo."
          confirmar="Finalizar"
          cancelar="Continuar"
          aoConfirmar={() => {
            setConfirmarFinalizacao(false);
            void finalizar();
          }}
          aoCancelar={() => setConfirmarFinalizacao(false)}
        />
      )}

      {mensagem && (
        <div className="cartao" onClick={() => setMensagem('')}>
          <strong>{mensagem}</strong>
          <p className="dica" style={{ margin: 0 }}>
            Toque para fechar.
          </p>
        </div>
      )}
    </div>
  );
}

/** "2026-10-01" -> "01/10/2026". */
function dataParaTexto(data: string): string {
  const [ano, mes, dia] = data.split('-');
  if (!ano || !mes || !dia) return data;
  return `${dia}/${mes}/${ano}`;
}

function formatarDuracao(milissegundos: number): string {
  const total = Math.max(0, Math.floor(milissegundos / 1000));
  const horas = Math.floor(total / 3600);
  const minutos = Math.floor((total % 3600) / 60);
  const segundos = total % 60;
  return [horas, minutos, segundos].map((n) => String(n).padStart(2, '0')).join(':');
}

function CartaoCronometro({
  inicioReal,
  duracaoPrevistaMinutos,
}: {
  inicioReal: number | null;
  duracaoPrevistaMinutos: number;
}) {
  const [, forcar] = useState(0);
  useEffect(() => {
    const timer = setInterval(() => forcar((n) => n + 1), 250);
    return () => clearInterval(timer);
  }, []);

  if (!inicioReal) return null;
  const decorrido = Date.now() - inicioReal;
  const tempoExcedido = decorrido > duracaoPrevistaMinutos * 60_000;

  return (
    <div className="cartao">
      <div style={{ fontSize: 26, fontWeight: 700, fontFamily: 'monospace' }}>
        {formatarDuracao(decorrido)}
      </div>
      <p className="dica" style={{ margin: 0 }}>
        {tempoExcedido
          ? `Passou do tempo previsto de ${duracaoPrevistaMinutos} min`
          : `Tempo previsto: ${duracaoPrevistaMinutos} min`}
      </p>
    </div>
  );
}

function Abas({
  aba,
  aoTrocar,
  habilitada,
}: {
  aba: AbaEntrevista;
  aoTrocar: (aba: AbaEntrevista) => void;
  habilitada: boolean;
}) {
  const opcoes: [AbaEntrevista, string][] = [
    ['CURRICULO', 'Currículo'],
    ['ROTEIRO', 'Roteiro'],
  ];
  return (
    <div className="fieldset" style={{ marginBottom: 12 }}>
      {opcoes.map(([chave, rotulo]) => (
        <span
          key={chave}
          className={aba === chave ? 'selecionado' : ''}
          style={habilitada ? undefined : { opacity: 0.5 }}
          onClick={() => {
            if (habilitada) aoTrocar(chave);
          }}
        >
          {rotulo}
        </span>
      ))}
    </div>
  );
}

/**
 * Resumo de quem já passou por decisão ou foi encerrado.
 *
 * Mesmos botões e textos do cartão equivalente do app: decidir só faz sentido
 * depois de concluída, quem já decidiu pode trocar a decisão e encerrar, e o
 * encerramento é sempre reversível por "Reabrir candidato".
 */
function ResumoFinalizado({
  entrevista,
  aoAprovar,
  aoReprovar,
  aoEncerrar,
  aoReabrir,
}: {
  entrevista: Entrevista;
  aoAprovar: () => void;
  aoReprovar: () => void;
  aoEncerrar: () => void;
  aoReabrir: () => void;
}) {
  const status = entrevista.status;
  const encerrada = status === 'ENCERRADA';
  const decidido = status === 'APROVADO' || status === 'REPROVADO';
  const duracao =
    entrevista.inicioReal !== null && entrevista.fimReal !== null
      ? formatarDuracao(entrevista.fimReal - entrevista.inicioReal)
      : null;

  return (
    <div className="cartao">
      <strong>
        {encerrada
          ? 'Candidato encerrado'
          : status === 'APROVADO'
            ? 'Candidato aprovado'
            : status === 'REPROVADO'
              ? 'Candidato reprovado'
              : 'Entrevista concluída'}
      </strong>
      {duracao ? (
        <p className="dica" style={{ margin: 0 }}>
          Duração real: {duracao}
        </p>
      ) : null}

      <div className="acoes">
        {!decidido && !encerrada && (
          <>
            <button className="botao botao-perigo" onClick={aoReprovar}>
              Reprovar
            </button>
            <button className="botao" onClick={aoAprovar}>
              Aprovar
            </button>
          </>
        )}
        {!encerrada && decidido && (
          <button className="botao" onClick={aoEncerrar}>
            Encerrar
          </button>
        )}
      </div>

      <p className="dica">
        {encerrada
          ? 'O registro, as respostas e o currículo continuam guardados. Para refazer a entrevista, use Reabrir.'
          : decidido
            ? 'Você pode trocar a decisão, encerrar ou reabrir a qualquer momento.'
            : 'Você pode aprovar, reprovar ou encerrar este candidato.'}
      </p>

      <button className="botao botao-secundario" onClick={aoReabrir}>
        Reabrir candidato
      </button>
      <p className="dica" style={{ margin: 0 }}>
        As respostas e o currículo já preenchidos são mantidos.
      </p>
    </div>
  );
}

function Dialogo({
  titulo,
  texto,
  confirmar,
  cancelar,
  aoConfirmar,
  aoCancelar,
}: {
  titulo: string;
  texto: string;
  confirmar: string;
  cancelar: string;
  aoConfirmar: () => void;
  aoCancelar: () => void;
}) {
  return (
    <div className="fundo-dialogo" onClick={aoCancelar}>
      <div className="dialogo" onClick={(ev) => ev.stopPropagation()}>
        <strong>{titulo}</strong>
        <p className="dica">{texto}</p>
        <div className="acoes">
          <button className="botao" onClick={aoConfirmar}>
            {confirmar}
          </button>
          <button className="botao botao-secundario" onClick={aoCancelar}>
            {cancelar}
          </button>
        </div>
      </div>
    </div>
  );
}

function BlocoCurriculo({
  uid,
  entrevista: e,
  aoMensagem,
}: {
  uid: string;
  entrevista: Entrevista;
  aoMensagem: (mensagem: string) => void;
}) {
  const temArquivo = Boolean(e.caminhoCurriculo);
  const [editando, setEditando] = useState(false);
  const [texto, setTexto] = useState(e.curriculo);
  const [erroAnexo, setErroAnexo] = useState('');

  // A entrevista chega pelo observador do Firestore; enquanto o campo está em
  // edição ele não pode ser sobrescrito por outra atualização da tela.
  useEffect(() => {
    if (!editando) setTexto(e.curriculo);
  }, [e.curriculo, editando]);

  const abrirCurriculo = async () => {
    if (!e.caminhoCurriculo) return;
    try {
      const url = await urlDoCurriculo(e.caminhoCurriculo);
      const novaAba = window.open(url, '_blank');
      // A URL é montada na hora a partir dos pedaços guardados; se a aba
      // abrir, o navegador não pode liberar a memória junto com a imagem.
      if (!novaAba) setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch {
      setErroAnexo('Não consegui buscar este currículo no banco de dados.');
    }
  };

  const trocarArquivo = async (novo: File) => {
    try {
      const anexo = await anexarCurriculo(uid, novo);
      if (e.caminhoCurriculo) void apagarCurriculoArquivo(e.caminhoCurriculo);
      await atualizarEntrevista(uid, e.id, {
        caminhoCurriculo: anexo.caminhoCurriculo,
        tipoCurriculo: anexo.tipoCurriculo,
        nomeArquivoCurriculo: novo.name,
      });
      setErroAnexo('');
      aoMensagem('Currículo anexado.');
    } catch (erro) {
      const detalhe = erro instanceof Error ? erro.message : '';
      setErroAnexo(`Não consegui enviar o currículo. ${detalhe}`.trim());
    }
  };

  const salvar = async () => {
    await salvarResumoCurriculo(uid, e.id, texto);
    setEditando(false);
    aoMensagem('Currículo atualizado.');
  };

  return (
    <div className="cartao">
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div className="secao-titulo" style={{ flex: 1, margin: 0 }}>
          Currículo
        </div>
        {!editando && (
          <button className="botao botao-secundario" onClick={() => setEditando(true)}>
            Editar
          </button>
        )}
      </div>

      {temArquivo && (
        <div
          className="cartao"
          style={{ padding: 12, marginTop: 12, cursor: 'pointer' }}
          onClick={() => void abrirCurriculo()}
        >
          <div>{e.nomeArquivoCurriculo}</div>
          <div className="dica" style={{ margin: 0 }}>
            {e.tipoCurriculo === 'PDF' ? 'PDF anexado · toque para abrir' : 'Imagem anexada'}
          </div>
        </div>
      )}

      {!temArquivo && !editando && (
        <label className="campo arquivo" style={{ marginTop: 12, display: 'block' }}>
          Anexar PDF ou imagem
          <input
            type="file"
            accept=".pdf,image/*"
            style={{ display: 'none' }}
            onChange={(ev) => {
              const arquivo = ev.target.files?.[0];
              if (arquivo) void trocarArquivo(arquivo);
            }}
          />
        </label>
      )}

      {erroAnexo && (
        <p className="dica" style={{ color: 'var(--erro, #c0392b)' }}>
          {erroAnexo}
        </p>
      )}

      {editando ? (
        <>
          <div style={{ marginTop: 12 }}>
            <span className="dica">Resumo do currículo</span>
            <div className="dica">Se o candidato enviou arquivo, escreva aqui só o essencial.</div>
            <textarea
              className="campo"
              rows={8}
              value={texto}
              {...SEM_AUTOFILL}
              onChange={(ev) => setTexto(ev.target.value)}
            />
          </div>
          <div className="acoes">
            <button className="botao" onClick={() => void salvar()}>
              Salvar
            </button>
            <button
              className="botao botao-secundario"
              onClick={() => {
                setTexto(e.curriculo);
                setEditando(false);
              }}
            >
              Cancelar
            </button>
          </div>
        </>
      ) : e.curriculo.trim() === '' && !temArquivo ? (
        <p className="dica" style={{ marginBottom: 0 }}>
          Nenhum currículo lançado para este candidato. Anexe o PDF/imagem ou toque em Editar
          para escrever o resumo.
        </p>
      ) : (
        <p style={{ marginBottom: 0 }}>{e.curriculo}</p>
      )}
    </div>
  );
}

function AbaRoteiro({
  uid,
  entrevista,
  roteiro,
  perguntas,
  respostas,
  vaga,
  somenteLeitura,
  aoAlterar,
}: {
  uid: string;
  entrevista: Entrevista;
  roteiro: Roteiro | null;
  perguntas: Pergunta[];
  respostas: Record<string, string>;
  vaga: Vaga | undefined;
  somenteLeitura: boolean;
  aoAlterar: (perguntaId: string, valor: string) => void;
}) {
  if (!roteiro) {
    return (
      <div className="cartao">
        <p className="dica" style={{ margin: 0 }}>
          Nenhum roteiro cadastrado.
        </p>
      </div>
    );
  }

  const diaEHora = `${dataParaTexto(entrevista.data)} às ${String(
    Math.floor(entrevista.inicioMinutos / 60),
  ).padStart(2, '0')}h${String(entrevista.inicioMinutos % 60).padStart(2, '0')}`;

  const automaticos: Record<string, string> = {
    DIA_E_HORA: diaEHora,
    NOME_CANDIDATO: entrevista.nome,
    TELEFONE_CANDIDATO: formatarTelefone(entrevista.telefone),
    DADOS_DA_VAGA: vaga
      ? [vaga.horarioTrabalho, vaga.salarioBeneficios].filter(Boolean).join(' · ')
      : '',
  };

  return (
    <div>
      <div className="secao-titulo">{roteiro.titulo}</div>

      {somenteLeitura && (
        <div className="cartao">
          <strong>Respostas registradas</strong>
          <p className="dica" style={{ margin: 0 }}>
            Esta entrevista foi finalizada. As respostas abaixo são o registro final e não
            podem mais ser editadas.
          </p>
        </div>
      )}

      <BlocoVaga vaga={vaga} />

      {perguntas.map((pergunta) => {
        if (pergunta.tipo === 'SECAO') {
          return (
            <div className="secao-titulo" key={pergunta.id}>
              {pergunta.titulo}
            </div>
          );
        }

        if (pergunta.tipo === 'TABELA_EXPERIENCIAS') {
          return (
            <TabelaExperiencias
              key={pergunta.id}
              uid={uid}
              entrevistaId={entrevista.id}
              pergunta={pergunta}
              somenteLeitura={somenteLeitura}
            />
          );
        }

        return (
          <CampoPergunta
            key={pergunta.id}
            pergunta={pergunta}
            valor={respostas[pergunta.id] ?? ''}
            automatico={automaticos[pergunta.respostaAutomatica] ?? ''}
            somenteLeitura={somenteLeitura}
            aoAlterar={(valor) => aoAlterar(pergunta.id, valor)}
          />
        );
      })}
    </div>
  );
}

/** Resumo da vaga no topo do roteiro, preenchido a partir do cadastro de vagas. */
function BlocoVaga({ vaga }: { vaga: Vaga | undefined }) {
  if (!vaga) {
    return (
      <div className="cartao">
        <strong>Informações da vaga</strong>
        <p className="dica" style={{ margin: 0 }}>
          Nenhuma vaga vinculada a esta entrevista. Cadastre a aba Vagas para estas informações
          aparecerem aqui.
        </p>
      </div>
    );
  }

  return (
    <div className="cartao">
      <strong>Informações da vaga</strong>
      <p className="titulo" style={{ fontSize: 18, margin: '4px 0 0' }}>
        {vaga.titulo}
      </p>
      <ItemDaVaga rotulo="Horário de trabalho" valor={vaga.horarioTrabalho} />
      <ItemDaVaga rotulo="Salário e benefícios" valor={vaga.salarioBeneficios} />
      <ItemDaVaga rotulo="Tempo de experiência" valor={vaga.tempoExperiencia} />
      <ItemDaVaga rotulo="Escolaridade" valor={vaga.escolaridade} />
      <ItemDaVaga rotulo="Precisa de habilitação" valor={vaga.exigeHabilitacao} />
      <ItemDaVaga rotulo="Resumo das atividades principais" valor={vaga.resumoAtividades} />
    </div>
  );
}

function ItemDaVaga({ rotulo, valor }: { rotulo: string; valor: string }) {
  if (!valor || !valor.trim()) return null;
  return (
    <div style={{ marginTop: 6 }}>
      <div className="dica" style={{ margin: 0 }}>
        {rotulo}
      </div>
      <div>{valor}</div>
    </div>
  );
}

/**
 * Um campo por pergunta. Pergunta com resposta automática mostra o valor que o
 * app preencheu e deixa sobrescrever.
 */
function CampoPergunta({
  pergunta,
  valor,
  automatico,
  somenteLeitura,
  aoAlterar,
}: {
  pergunta: Pergunta;
  valor: string;
  automatico: string;
  somenteLeitura: boolean;
  aoAlterar: (valor: string) => void;
}) {
  const preenchidoPeloApp = valor === '' && automatico !== '';
  const dica = preenchidoPeloApp
    ? 'Preenchido pelo app — pode editar se precisar.'
    : pergunta.dica || null;

  if (pergunta.tipo === 'SIM_NAO') {
    const selecionado = valor || (preenchidoPeloApp ? automatico : '');
    return (
      <div className="cartao">
        <div className="dica" style={{ margin: 0 }}>
          {pergunta.titulo}
        </div>
        {dica && (
          <div className="dica" style={{ fontSize: 12 }}>
            {dica}
          </div>
        )}
        <div className="fieldset">
          {['Sim', 'Não'].map((opcao) => (
            <span
              key={opcao}
              className={selecionado === opcao ? 'selecionado' : ''}
              style={somenteLeitura ? { opacity: 0.6 } : undefined}
              onClick={() => {
                if (!somenteLeitura) aoAlterar(opcao);
              }}
            >
              {opcao}
            </span>
          ))}
        </div>
      </div>
    );
  }

  const umaLinha = pergunta.tipo !== 'TEXTO_LONGO';
  return (
    <div className="cartao">
      <div className="dica" style={{ margin: 0 }}>
        {pergunta.titulo}
      </div>
      {dica && (
        <div className="dica" style={{ fontSize: 12 }}>
          {dica}
        </div>
      )}
      <Campo
        valor={valor || (preenchidoPeloApp ? automatico : '')}
        aoAlterar={aoAlterar}
        somenteLeitura={somenteLeitura}
        numerico={pergunta.tipo === 'NUMERO'}
        umaLinha={umaLinha}
      />
    </div>
  );
}

/**
 * Atributos que impedem o navegador de abrir o preenchimento automático sobre o
 * campo.
 *
 * `autoComplete="off"` sozinho não basta: o Chrome ignora esse valor em quase
 * todos os campos e ainda assim mostra a lista do Google por cima do texto. A
 * combinação abaixo resolve — `one-time-code` marca o campo como "não
 * preenchível", e `data-1p-ignore`/`data-lpignore` cobrem os gerenciadores de
 * senha que fazem o mesmo.
 */
const SEM_AUTOFILL = {
  autoComplete: 'off' as const,
  'data-1p-ignore': true,
  'data-lpignore': 'true',
  'data-form-type': 'other',
};

/** Campo de texto que respeita o formato pedido pelo tipo da pergunta. */
function Campo({
  valor,
  aoAlterar,
  somenteLeitura,
  numerico,
  umaLinha,
}: {
  valor: string;
  aoAlterar: (valor: string) => void;
  somenteLeitura: boolean;
  numerico: boolean;
  umaLinha: boolean;
}) {
  if (numerico) {
    return (
      <input
        className="campo"
        type="number"
        value={valor}
        readOnly={somenteLeitura}
        {...SEM_AUTOFILL}
        onChange={(ev) => aoAlterar(ev.target.value)}
      />
    );
  }
  if (umaLinha) {
    return (
      <input
        className="campo"
        value={valor}
        readOnly={somenteLeitura}
        {...SEM_AUTOFILL}
        onChange={(ev) => aoAlterar(ev.target.value)}
      />
    );
  }
  return (
    <textarea
      className="campo"
      rows={4}
      value={valor}
      readOnly={somenteLeitura}
      {...SEM_AUTOFILL}
      onChange={(ev) => aoAlterar(ev.target.value)}
    />
  );
}

/**
 * Mini tabela "locais onde trabalhou": Local, Ano, Duração, Cargo e Motivo da
 * saída. Uma linha por emprego, com o botão de adicionar no fim.
 */
function TabelaExperiencias({
  uid,
  entrevistaId,
  pergunta,
  somenteLeitura,
}: {
  uid: string;
  entrevistaId: string;
  pergunta: Pergunta;
  somenteLeitura: boolean;
}) {
  const [linhas, setLinhas] = useState<LinhaExperiencia[]>([]);

  useEffect(() => {
    if (!uid) return;
    return observarExperiencias(uid, entrevistaId, setLinhas);
  }, [uid, entrevistaId]);

  const colunas: [keyof LinhaExperiencia, string][] = [
    ['local', 'Local'],
    ['ano', 'Ano'],
    ['duracao', 'Duração'],
    ['cargo', 'Cargo'],
    ['motivoSaida', 'Motivo da saída'],
  ];

  const adicionar = async () => {
    await salvarExperiencia(uid, entrevistaId, {
      local: '',
      ano: '',
      duracao: '',
      cargo: '',
      motivoSaida: '',
      ordem: linhas.length,
    });
  };

  const alterar = (linha: LinhaExperiencia, campo: keyof LinhaExperiencia, valor: string) => {
    setLinhas((atuais) =>
      atuais.map((l) => (l.id === linha.id ? { ...l, [campo]: valor } : l)),
    );
    void atualizarExperiencia(uid, entrevistaId, linha.id, {
      [campo]: valor,
      ordem: linha.ordem,
    });
  };

  return (
    <div className="cartao">
      <div className="secao-titulo" style={{ margin: 0 }}>
        Locais onde trabalhou
      </div>
      {pergunta.dica && (
        <div className="dica" style={{ fontSize: 12 }}>
          {pergunta.dica}
        </div>
      )}

      <table className="tabela">
        <thead>
          <tr>
            {colunas.map(([, rotulo]) => (
              <th key={rotulo}>{rotulo}</th>
            ))}
            {linhas.length > 1 && <th />}
          </tr>
        </thead>
        <tbody>
          {linhas.map((linha) => (
            <tr key={linha.id}>
              {colunas.map(([campo, rotulo]) => (
                <td key={rotulo}>
                  <input
                    className="campo"
                    value={linha[campo]}
                    readOnly={somenteLeitura}
                    {...SEM_AUTOFILL}
                    // `name` único por linha: sem ele o navegador reaproveita o
                    // mesmo campo em todas as linhas e o popup reaparece.
                    name={`exp_${linha.id}_${campo}`}
                    aria-label={rotulo}
                    onChange={(ev) => alterar(linha, campo, ev.target.value)}
                  />
                </td>
              ))}
              {linhas.length > 1 && (
                <td>
                  {!somenteLeitura && (
                    <button
                      className="icone-botao"
                      title="Remover linha"
                      aria-label="Remover linha"
                      onClick={() => void removerExperiencia(uid, entrevistaId, linha.id)}
                    >
                      🗑
                    </button>
                  )}
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>

      {!somenteLeitura && (
        <button className="botao botao-secundario" onClick={() => void adicionar()}>
          Adicionar local
        </button>
      )}
    </div>
  );
}