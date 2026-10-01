'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import {
  observarRoteiros,
  observarPerguntas,
  salvarRoteiro,
  apagarRoteiro,
  ROTEIRO_PADRAO_INICIAL,
  garantirRoteiroPadrao,
  observarVagas,
} from '@/lib/firestore';
import type { Roteiro, Pergunta, Vaga, TipoResposta, RespostaAutomatica } from '@/lib/tipos';
import { ROTULO_TIPO, ROTULO_AUTOMATICA } from '@/lib/tipos';

export default function PaginaRoteiro() {
  const { usuario } = useAuth();
  const uid = usuario?.uid ?? '';
  const [roteiros, setRoteiros] = useState<Roteiro[]>([]);
  const [perguntas, setPerguntas] = useState<Record<string, Pergunta[]>>({});
  const [vagas, setVagas] = useState<Vaga[]>([]);
  const [alvo, setAlvo] = useState<{ roteiro: Roteiro | null; vagaId: string | null } | null>(null);

  useEffect(() => {
    if (!uid) return;
    return observarRoteiros(uid, setRoteiros);
  }, [uid]);

  useEffect(() => {
    if (!uid) return;
    return observarVagas(uid, setVagas);
  }, [uid]);

  // Igual ao app: o roteiro padrão nasce com as 29 perguntas no primeiro acesso.
  useEffect(() => {
    if (!uid) return;
    void garantirRoteiroPadrao(uid);
  }, [uid]);

  // Perguntas de roteiros que já estão em tela.
  useEffect(() => {
    if (!uid) return;
    const desativar: (() => void)[] = [];
    roteiros.forEach((roteiro) => {
      desativar.push(
        observarPerguntas(uid, roteiro.id, (lista) =>
          setPerguntas((atual) => ({ ...atual, [roteiro.id]: lista })),
        ),
      );
    });
    return () => desativar.forEach((d) => d());
  }, [uid, roteiros]);

  const padrao = roteiros.find((r) => r.padrao);
  const porVaga = roteiros.filter((r) => !r.padrao);

  return (
    <div>
      <h1 className="titulo">Roteiro</h1>
      <p className="subtitulo">
        As perguntas são as mesmas para todas as vagas, a menos que uma vaga
        tenha o seu próprio roteiro.
      </p>

      <div className="cartao">
        <strong>Roteiro padrão do processo</strong>
        {padrao && (
          <div className="dica">
            {perguntas[padrao.id]?.length ?? 0} perguntas
          </div>
        )}
        <div className="acoes">
          <button
            className="botao"
            onClick={() =>
              setAlvo({
                roteiro: padrao ?? null,
                vagaId: null,
              })
            }
          >
            {padrao ? 'Editar padrão' : 'Criar roteiro padrão'}
          </button>
        </div>
      </div>

      {vagas.map((vaga) => {
        const roteiroDaVaga = porVaga.find((r) => r.vagaId === Number(vaga.id));
        return (
          <div className="cartao" key={vaga.id}>
            <strong>Roteiro da vaga — {vaga.titulo}</strong>
            <div className="dica">
              {roteiroDaVaga
                ? `${perguntas[roteiroDaVaga.id]?.length ?? 0} perguntas próprias`
                : 'Usa o roteiro padrão até ser editado'}
            </div>
            <div className="acoes">
              <button
                className="botao"
                onClick={() =>
                  setAlvo({
                    roteiro: roteiroDaVaga ?? null,
                    vagaId: vaga.id,
                  })
                }
              >
                {roteiroDaVaga ? 'Editar' : 'Criar roteiro desta vaga'}
              </button>
              {roteiroDaVaga && (
                <button
                  className="botao botao-secundario"
                  onClick={() => {
                    if (
                      confirm(
                        'Voltar ao padrão? O roteiro desta vaga é apagado e ela volta a seguir o padrão.',
                      )
                    ) {
                      apagarRoteiro(uid, roteiroDaVaga.id).then(() =>
                        setPerguntas((atual) => {
                          const { [roteiroDaVaga.id]: _, ...resto } = atual;
                          return resto;
                        }),
                      );
                    }
                  }}
                >
                  Voltar ao padrão
                </button>
              )}
            </div>
          </div>
        );
      })}

      {alvo && (
        <EditorRoteiro
          uid={uid}
          roteiroId={alvo.roteiro?.id ?? null}
          tituloBase={
            alvo.vagaId
              ? alvo.roteiro
                ? `Roteiro da vaga — ${vagas.find((v) => v.id === alvo.vagaId)?.titulo ?? ''}`
                : `${padrao?.titulo ?? 'Roteiro padrão'} — ${vagas.find((v) => v.id === alvo.vagaId)?.titulo ?? ''}`
              : 'Roteiro padrão'
          }
          vagaId={alvo.vagaId}
          perguntasIniciais={
            alvo.roteiro
              ? perguntas[alvo.roteiro.id] ?? []
              : alvo.vagaId
                ? (perguntas[padrao?.id ?? ''] ?? []).map((p) => ({ ...p, id: '' }))
                : []
          }
          aoFechar={() => setAlvo(null)}
        />
      )}
    </div>
  );
}

function EditorRoteiro({
  uid,
  roteiroId,
  tituloBase,
  vagaId,
  perguntasIniciais,
  aoFechar,
}: {
  uid: string;
  roteiroId: string | null;
  tituloBase: string;
  vagaId: string | null;
  perguntasIniciais: Pergunta[];
  aoFechar: () => void;
}) {
  const [titulo, setTitulo] = useState(tituloBase);
  const [lista, setLista] = useState<
    { titulo: string; tipo: TipoResposta; dica: string; respostaAutomatica: RespostaAutomatica }[]
  >(
    perguntasIniciais.map((p) => ({
      titulo: p.titulo,
      tipo: p.tipo,
      dica: p.dica,
      respostaAutomatica: p.respostaAutomatica,
    })),
  );

  const salvar = async () => {
    await salvarRoteiro(
      uid,
      {
        titulo: titulo.trim() || tituloBase,
        conteudo: '',
        ordem: vagaId ? 1 : 0,
        padrao: !vagaId,
        vagaId: vagaId ? Number(vagaId) : null,
      },
      lista.filter((p) => p.titulo.trim()),
      roteiroId ?? undefined,
    );
    aoFechar();
  };

  const vaiTerConteudo = lista.length === 0;

  return (
    <div className="cartao" style={{ marginTop: 18 }}>
      <strong>{vagaId ? 'Roteiro da vaga' : 'Roteiro padrão'}</strong>
      <input
        className="campo"
        value={titulo}
        onChange={(e) => setTitulo(e.target.value)}
        placeholder="Título do roteiro"
      />
      <p className="dica">
        {lista.length} perguntas. A vaga sem roteiro próprio acompanha o padrão;
        ao salvar, ela cria o próprio.
      </p>

      {vaiTerConteudo && (
        <button
          className="botao botao-secundario"
          onClick={() =>
            setLista(
              ROTEIRO_PADRAO_INICIAL.map((p) => ({
                ...p,
                tipo: p.tipo as TipoResposta,
                respostaAutomatica: p.respostaAutomatica as RespostaAutomatica,
              })),
            )
          }
        >
          Usar perguntas iniciais
        </button>
      )}

      {!vaiTerConteudo && !vagaId && roteiroId && (
        <button
          className="botao botao-secundario"
          onClick={() => {
            if (!confirm('Restaurar as perguntas originais do roteiro padrão?')) return;
            setLista(
              ROTEIRO_PADRAO_INICIAL.map((p) => ({
                ...p,
                tipo: p.tipo as TipoResposta,
                respostaAutomatica: p.respostaAutomatica as RespostaAutomatica,
              })),
            );
          }}
        >
          Restaurar perguntas originais
        </button>
      )}

      {lista.map((pergunta, indice) => (
        <PerguntaEditavel
          key={indice}
          indice={indice}
          pergunta={pergunta}
          aoAlterar={(novo) =>
            setLista((atual) => atual.map((p, i) => (i === indice ? novo : p)))
          }
          aoSubir={() =>
            indice > 0 &&
            setLista((atual) => {
              const copia = [...atual];
              [copia[indice - 1], copia[indice]] = [copia[indice], copia[indice - 1]];
              return copia;
            })
          }
          aoDescer={() =>
            indice < lista.length - 1 &&
            setLista((atual) => {
              const copia = [...atual];
              [copia[indice + 1], copia[indice]] = [copia[indice], copia[indice + 1]];
              return copia;
            })
          }
          aoRemover={() => setLista((atual) => atual.filter((_, i) => i !== indice))}
        />
      ))}

      <button
        className="botao botao-secundario"
        onClick={() =>
          setLista((atual) => [
            ...atual,
            { titulo: '', tipo: 'TEXTO', dica: '', respostaAutomatica: 'NENHUMA' },
          ])
        }
      >
        Adicionar pergunta
      </button>

      <div className="acoes">
        <button className="botao" onClick={salvar}>
          Salvar roteiro
        </button>
        <button className="botao botao-secundario" onClick={aoFechar}>
          Cancelar
        </button>
      </div>
    </div>
  );
}

type PerguntaEditavelDados = {
  titulo: string;
  tipo: TipoResposta;
  dica: string;
  respostaAutomatica: RespostaAutomatica;
};

function PerguntaEditavel({
  indice,
  pergunta,
  aoAlterar,
  aoSubir,
  aoDescer,
  aoRemover,
}: {
  indice: number;
  pergunta: PerguntaEditavelDados;
  aoAlterar: (pergunta: PerguntaEditavelDados) => void;
  aoSubir: () => void;
  aoDescer: () => void;
  aoRemover: () => void;
}) {
  return (
    <div className="cartao" style={{ padding: 12, marginBottom: 10 }}>
      <div className="dica" style={{ marginBottom: 4 }}>
        Pergunta {indice + 1}
      </div>
      <input
        className="campo"
        placeholder="Texto da pergunta"
        value={pergunta.titulo}
        onChange={(e) => aoAlterar({ ...pergunta, titulo: e.target.value })}
      />
      <div className="fieldset">
        {(Object.keys(ROTULO_TIPO) as TipoResposta[]).map((tipo) => (
          <span
            key={tipo}
            className={pergunta.tipo === tipo ? 'selecionado' : ''}
            onClick={() => aoAlterar({ ...pergunta, tipo })}
          >
            {ROTULO_TIPO[tipo]}
          </span>
        ))}
      </div>
      <div className="fieldset">
        {(Object.keys(ROTULO_AUTOMATICA) as RespostaAutomatica[]).map((auto) => (
          <span
            key={auto}
            className={pergunta.respostaAutomatica === auto ? 'selecionado' : ''}
            onClick={() => aoAlterar({ ...pergunta, respostaAutomatica: auto })}
          >
            {ROTULO_AUTOMATICA[auto]}
          </span>
        ))}
      </div>
      <div className="acoes">
        <button className="icone-botao" onClick={aoSubir}>
          ↑
        </button>
        <button className="icone-botao" onClick={aoDescer}>
          ↓
        </button>
        <button className="icone-botao" onClick={aoRemover}>
          🗑
        </button>
      </div>
    </div>
  );
}