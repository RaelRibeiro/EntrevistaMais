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
  observarVagas,
} from '@/lib/firestore';
import type { Entrevista, Vaga, Roteiro, Pergunta } from '@/lib/tipos';
import { ehFinalizado } from '@/lib/tipos';
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

  return (
    <div>
      <Link href="/agenda" className="dica" style={{ display: 'inline-block', marginBottom: 10 }}>
        ← Voltar à agenda
      </Link>
      <h1 className="titulo" style={{ marginBottom: 4 }}>
        {entrevista.nome}
      </h1>
      <p className="subtitulo" style={{ marginBottom: 16 }}>
        {minutosParaHora(entrevista.inicioMinutos)}
        {vaga ? ' · ' + vaga.titulo : ''}
      </p>

      {finalizada && (
        <div className="cartao">
          <strong>Respostas registradas</strong>
          <p className="dica">
            Esta entrevista foi finalizada. As respostas abaixo são o registro
            final e não podem mais mudar.
          </p>
        </div>
      )}

      {emAndamento && (
        <div className="cartao">
          <strong>Em andamento</strong>
          <div className="acoes">
            <button
              className="botao"
              onClick={() => atualizarStatus(uid, id, 'CONCLUIDA')}
            >
              Concluir entrevista
            </button>
          </div>
        </div>
      )}

      {!emAndamento && !finalizada && (
        <div className="cartao">
          <strong>Agendada</strong>
          <div className="acoes">
            <button
              className="botao"
              onClick={() => atualizarStatus(uid, id, 'EM_ANDAMENTO')}
            >
              Iniciar entrevista
            </button>
          </div>
        </div>
      )}

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
      return [parametros.vaga?.horarioTrabalho, parametros.vaga?.salarioBeneficios]
        .filter(Boolean)
        .join(' · ');
    default:
      return '';
  }
}

function PerguntaResposta({
  indice,
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
          rows={pergunta.tipo === 'TEXTO_LONGO' || pergunta.tipo === 'TABELA_EXPERIENCIAS' ? 4 : 2}
          placeholder={
            pergunta.tipo === 'TABELA_EXPERIENCIAS'
              ? 'Experiências anteriores…'
              : 'Resposta'
          }
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