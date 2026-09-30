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
  atualizarStatus,
  atualizarEntrevista,
  observarVagas,
  anexarCurriculo,
  apagarCurriculoArquivo,
  urlDoCurriculo,
  observarExperiencias,
  salvarExperiencia,
  removerExperiencia,
} from '@/lib/firestore';
import type { Entrevista, Vaga, Roteiro, Pergunta, LinhaExperiencia } from '@/lib/tipos';
import { ehFinalizado, horarioDaVaga } from '@/lib/tipos';
import { minutosParaHora } from '@/lib/horario';

type Props = { params: Promise<{ id: string }> };

export default function PaginaEntrevista({ params }: Props) {
  const { id } = use(params);
  const { usuario } = useAuth();
  const uid = usuario?.uid ?? '';
  const [entrevista, setEntrevista] = useState<Entrevista | null>(null);
  const [vagas, setVagas] = useState<Vaga[]>([]);
  const [roteiros, setRoteiros] = useState<Roteiro[]>([]);
  const [perguntas, setPerguntas] = useState<Pergunta[]>([]);
  const [respostas, setRespostas] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!uid) return;
    return observarEntrevista(uid, id, setEntrevista);
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
    const r =
      roteiros.find((x) => x.vagaId === entrevista?.vagaId) ??
      roteiros.find((x) => x.padrao);
    if (!r) {
      setPerguntas([]);
      return;
    }
    return observarPerguntas(uid, r.id, setPerguntas);
  }, [uid, roteiros, entrevista?.vagaId]);

  useEffect(() => {
    if (!uid) return;
    return observarRespostas(uid, id, setRespostas);
  }, [uid, id]);

  if (!entrevista) return <p className="dica">Carregando entrevista…</p>;

  const vaga = vagas.find((v) => v.id === entrevista.vagaId);
  const finalizada = ehFinalizado(entrevista.status);
  const emAndamento = entrevista.status === 'EM_ANDAMENTO';

  const iniciar = () =>
    atualizarEntrevista(uid, id, {
      status: 'EM_ANDAMENTO',
      inicioReal: Date.now(),
      inicioManual: true,
    });

  const concluir = () =>
    atualizarEntrevista(uid, id, { status: 'CONCLUIDA', fimReal: Date.now() });

  const voltarParaAgendada = () => atualizarStatus(uid, id, 'AGENDADA');

  return (
    <div>
      <Link href="/agenda" className="dica" style={{ display: 'inline-block', marginBottom: 10 }}>
        ← Voltar à agenda
      </Link>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        <h1 className="titulo" style={{ flex: 1, marginBottom: 4 }}>
          {entrevista.nome}
        </h1>
        <span className={`status ${entrevista.status.toLowerCase()}`}>
          {entrevista.status.replace('_', ' ').toLowerCase()}
        </span>
      </div>
      <p className="subtitulo" style={{ marginBottom: 16 }}>
        {minutosParaHora(entrevista.inicioMinutos)}
        {vaga ? ' · ' + vaga.titulo : ''}
        {entrevista.telefone ? ' · ' + entrevista.telefone : ''}
      </p>

      {emAndamento && <Cronometro inicioReal={entrevista.inicioReal} />}
      {entrevista.status === 'CONCLUIDA' && entrevista.inicioReal && (
        <div className="cartao">
          <strong>Tempo de entrevista</strong>
          <p className="dica" style={{ margin: 0 }}>
            {formatarDuracao((entrevista.fimReal ?? Date.now()) - entrevista.inicioReal)}
          </p>
        </div>
      )}

      {emAndamento && (
        <div className="cartao">
          <strong>Entrevista em andamento</strong>
          <div className="acoes">
            <button className="botao" onClick={concluir}>
              Concluir entrevista
            </button>
          </div>
        </div>
      )}

      {!finalizada && !emAndamento && (
        <div className="cartao">
          <strong>{entrevista.status === 'AGENDADA' ? 'Agendada' : 'Interrompida'}</strong>
          <div className="acoes">
            <button className="botao" onClick={iniciar}>
              {entrevista.status === 'AGENDADA'
                ? 'Iniciar entrevista'
                : 'Reabrir e iniciar entrevista'}
            </button>
          </div>
        </div>
      )}

      {(entrevista.status === 'NAO_COMPARECEU' || entrevista.status === 'CANCELADA') && (
        <div className="cartao">
          <strong>Não houve entrevista</strong>
          <div className="acoes">
            <button className="botao botao-secundario" onClick={voltarParaAgendada}>
              Reabrir
            </button>
          </div>
        </div>
      )}

      <BlocoCurriculo uid={uid} entrevista={entrevista} somenteLeitura={finalizada} />

      {finalizada ? (
        <Decisoes uid={uid} id={id} status={entrevista.status} />
      ) : null}

      {perguntas.length === 0 && (
        <div className="cartao">
          <strong>Nenhum roteiro</strong>
          <p className="dica">
            Crie o roteiro padrão na aba <Link href="/roteiro">Roteiro</Link>.
          </p>
        </div>
      )}

      {perguntas.map((pergunta, indice) => (
        <PerguntaResposta
          key={pergunta.id}
          indice={indice}
          uid={uid}
          entrevistaId={id}
          pergunta={pergunta}
          valor={respostas[pergunta.id] ?? ''}
          vaga={vaga}
          dataHora={`${entrevista.data} ${minutosParaHora(entrevista.inicioMinutos)}`}
          nome={entrevista.nome}
          telefone={entrevista.telefone}
          somenteLeitura={finalizada}
          aoAlterar={(valor) => salvarResposta(uid, id, pergunta.id, valor)}
        />
      ))}
    </div>
  );
}

function formatarDuracao(milissegundos: number): string {
  const total = Math.max(0, Math.floor(milissegundos / 1000));
  const horas = Math.floor(total / 3600);
  const minutos = Math.floor((total % 3600) / 60);
  const segundos = total % 60;
  return [horas, minutos, segundos]
    .map((n) => String(n).padStart(2, '0'))
    .join(':');
}

function Cronometro({ inicioReal }: { inicioReal: number | null }) {
  const [, forcar] = useState(0);
  useEffect(() => {
    const timer = setInterval(() => forcar((n) => n + 1), 1000);
    return () => clearInterval(timer);
  }, []);
  if (!inicioReal) return null;
  return (
    <div className="cartao">
      <strong>Cronômetro</strong>
      <p className="dica" style={{ margin: 0, fontSize: 22 }}>
        {formatarDuracao(Date.now() - inicioReal)}
      </p>
    </div>
  );
}

function BlocoCurriculo({
  uid,
  entrevista: e,
  somenteLeitura,
}: {
  uid: string;
  entrevista: Entrevista;
  somenteLeitura: boolean;
}) {
  const temArquivo = Boolean(e.caminhoCurriculo);
  const [arquivo, setArquivo] = useState<File | null>(null);

  const abrirCurriculo = async () => {
    if (!e.caminhoCurriculo) return;
    const url = await urlDoCurriculo(e.caminhoCurriculo);
    window.open(url, '_blank');
  };

  const trocarArquivo = async (novo: File | null) => {
    if (!novo || !uid) return;
    const anexo = await anexarCurriculo(uid, novo);
    if (e.caminhoCurriculo) void apagarCurriculoArquivo(e.caminhoCurriculo);
    await atualizarEntrevista(uid, e.id, {
      caminhoCurriculo: anexo.caminhoCurriculo,
      tipoCurriculo: anexo.tipoCurriculo,
      nomeArquivoCurriculo: novo.name,
    });
  };

  const removerArquivo = async () => {
    if (!uid || !e.caminhoCurriculo) return;
    await apagarCurriculoArquivo(e.caminhoCurriculo);
    await atualizarEntrevista(uid, e.id, {
      caminhoCurriculo: '',
      nomeArquivoCurriculo: '',
      tipoCurriculo: 'RESUMO',
    });
  };

  return (
    <div className="cartao">
      <strong>Currículo</strong>
      {temArquivo && (
        <p className="dica" style={{ marginBottom: 8 }}>
          📎 {e.nomeArquivoCurriculo} ({e.tipoCurriculo === 'PDF' ? 'PDF' : 'Imagem'})
        </p>
      )}
      {temArquivo && (
        <div className="acoes" style={{ marginBottom: 8 }}>
          <button className="botao" onClick={abrirCurriculo}>
            Abrir currículo
          </button>
          {!somenteLeitura && (
            <button className="botao botao-perigo" onClick={removerArquivo}>
              Remover arquivo
            </button>
          )}
        </div>
      )}
      {!temArquivo && !somenteLeitura && (
        <label className="campo arquivo">
          {arquivo ? `📎 ${arquivo.name}` : '📄 Anexar currículo (PDF ou imagem)'}
          <input
            type="file"
            accept=".pdf,image/*"
            style={{ display: 'none' }}
            onChange={(ev) => {
              setArquivo(ev.target.files?.[0] ?? null);
              if (ev.target.files?.[0]) void trocarArquivo(ev.target.files[0]);
            }}
          />
        </label>
      )}
      <textarea
        className="campo"
        rows={3}
        placeholder="Resumo do candidato (observações, histórico…)"
        value={e.curriculo}
        readOnly={somenteLeitura}
        onChange={(ev) =>
          somenteLeitura
            ? undefined
            : atualizarEntrevista(uid, e.id, { curriculo: ev.target.value })
        }
        style={{ marginTop: 8 }}
      />
    </div>
  );
}

function Decisoes({
  uid,
  id,
  status,
}: {
  uid: string;
  id: string;
  status: Entrevista['status'];
}) {
  return (
    <div className="cartao">
      <strong>Decisão</strong>
      <p className="dica">Registra a avaliação final do candidato.</p>
      <div className="acoes">
        {status === 'CONCLUIDA' && (
          <>
            <button
              className="botao"
              onClick={() => atualizarStatus(uid, id, 'APROVADO')}
            >
              Aprovar candidato
            </button>
            <button
              className="botao botao-perigo"
              onClick={() => atualizarStatus(uid, id, 'REPROVADO')}
            >
              Reprovar candidato
            </button>
            <button
              className="botao botao-secundario"
              onClick={() => atualizarStatus(uid, id, 'ENCERRADA')}
            >
              Encerrar sem decisão
            </button>
          </>
        )}
        {status === 'APROVADO' && (
          <>
            <strong>Aprovado</strong>
            <button
              className="botao botao-secundario"
              onClick={() => atualizarStatus(uid, id, 'CONCLUIDA')}
            >
              Reabrir
            </button>
          </>
        )}
        {status === 'REPROVADO' && (
          <>
            <strong>Reprovado</strong>
            <button
              className="botao botao-secundario"
              onClick={() => atualizarStatus(uid, id, 'CONCLUIDA')}
            >
              Reabrir
            </button>
          </>
        )}
        {status === 'ENCERRADA' && (
          <button
            className="botao botao-secundario"
            onClick={() => atualizarStatus(uid, id, 'CONCLUIDA')}
          >
            Reabrir
          </button>
        )}
      </div>
    </div>
  );
}

function valorAutomatico(
  pergunta: Pergunta,
  parametros: { dataHora: string; nome: string; telefone: string; vaga: Vaga | undefined },
): string {
  switch (pergunta.respostaAutomatica) {
    case 'DIA_E_HORA':
      return parametros.dataHora;
    case 'NOME_CANDIDATO':
      return parametros.nome;
    case 'TELEFONE_CANDIDATO':
      return parametros.telefone;
    case 'DADOS_DA_VAGA':
      return [
        parametros.vaga ? horarioDaVaga(parametros.vaga) : '',
        parametros.vaga?.tipoContrato,
        parametros.vaga?.salarioBeneficios,
        parametros.vaga?.tempoExperiencia,
        parametros.vaga?.escolaridade,
        parametros.vaga?.exigeHabilitacao
          ? `Habilitação: ${parametros.vaga.exigeHabilitacao}`
          : '',
        parametros.vaga?.resumoAtividades,
      ]
        .filter(Boolean)
        .join('\n');
    default:
      return '';
  }
}

function PerguntaResposta({
  indice,
  uid,
  entrevistaId,
  pergunta,
  valor,
  vaga,
  dataHora,
  nome,
  telefone,
  somenteLeitura,
  aoAlterar,
}: {
  indice: number;
  uid: string;
  entrevistaId: string;
  pergunta: Pergunta;
  valor: string;
  vaga: Vaga | undefined;
  dataHora: string;
  nome: string;
  telefone: string;
  somenteLeitura: boolean;
  aoAlterar: (valor: string) => void;
}) {
  if (pergunta.tipo === 'SECAO') {
    return <div className="secao-titulo">{pergunta.titulo}</div>;
  }

  if (pergunta.tipo === 'TABELA_EXPERIENCIAS') {
    return (
      <TabelaExperiencias
        indice={indice}
        uid={uid}
        entrevistaId={entrevistaId}
        pergunta={pergunta}
        somenteLeitura={somenteLeitura}
      />
    );
  }

  const automatico = valorAutomatico(pergunta, { dataHora, nome, telefone, vaga });
  const exibido = valor || automatico;
  const dica =
    pergunta.respostaAutomatica !== 'NENHUMA' && !valor
      ? 'Preenchido pelo sistema — pode editar se precisar.'
      : pergunta.dica;

  return (
    <div className="cartao">
      <strong>
        {indice + 1}. {pergunta.titulo}
      </strong>
      {dica ? <p className="dica">{dica}</p> : null}

      {pergunta.tipo === 'SIM_NAO' ? (
        <div className="fieldset">
          {['Sim', 'Não', 'Talvez'].map((opcao) => (
            <span
              key={opcao}
              className={valor === opcao ? 'selecionado' : ''}
              onClick={() => !somenteLeitura && aoAlterar(opcao)}
            >
              {opcao}
            </span>
          ))}
        </div>
      ) : (
        <textarea
          className="campo"
          rows={pergunta.tipo === 'TEXTO_LONGO' ? 4 : 2}
          placeholder="Resposta"
          value={exibido}
          readOnly={somenteLeitura}
          onChange={(e) => aoAlterar(e.target.value)}
        />
      )}
      {valor && automatico && (
        <div className="dica" style={{ color: 'var(--primaria)' }}>
          {somenteLeitura ? 'Registro final' : 'Pode editar'}
        </div>
      )}
    </div>
  );
}

function TabelaExperiencias({
  indice,
  uid,
  entrevistaId,
  pergunta,
  somenteLeitura,
}: {
  indice: number;
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

  const adicionar = async () => {
    const campos = ['Local / empresa', 'Ano de início', 'Duração', 'Cargo', 'Motivo da saída'];
    const [local, ano, duracao, cargo, motivoSaida] = campos.map((rotulo) =>
      prompt(`${rotulo}:`)?.trim() ?? '',
    );
    if (!local && !cargo) return;
    await salvarExperiencia(uid, entrevistaId, {
      local,
      ano,
      duracao,
      cargo,
      motivoSaida,
      ordem: linhas.length,
    });
  };

  return (
    <div className="cartao">
      <strong>
        {indice + 1}. {pergunta.titulo}
      </strong>
      {pergunta.dica ? <p className="dica">{pergunta.dica}</p> : null}
      {linhas.length === 0 && (
        <p className="dica">
          Nenhuma experiência registrada.
        </p>
      )}
      {linhas.map((linha) => (
        <div
          key={linha.id}
          className="cartao"
          style={{ padding: 10, marginBottom: 8 }}
        >
          <strong>{linha.local || linha.cargo || 'Experiência'}</strong>
          <div className="dica" style={{ margin: 0 }}>
            {[linha.ano, linha.duracao, linha.cargo]
              .filter(Boolean)
              .join(' · ')}
            {linha.motivoSaida ? ` — ${linha.motivoSaida}` : ''}
          </div>
          {!somenteLeitura && (
            <div className="acoes" style={{ marginTop: 6 }}>
              <button
                className="icone-botao"
                onClick={() => removerExperiencia(uid, entrevistaId, linha.id)}
              >
                🗑
              </button>
            </div>
          )}
        </div>
      ))}
      {!somenteLeitura && (
        <button className="botao botao-secundario" onClick={adicionar}>
          Adicionar experiência
        </button>
      )}
    </div>
  );
}