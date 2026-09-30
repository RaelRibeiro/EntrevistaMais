'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import {
  observarEntrevistas,
  adicionarEntrevista,
  atualizarStatus,
  atualizarEntrevista,
  removerEntrevista,
  moverEntrevista,
  anexarCurriculo,
  recalcularHorariosDoDia,
  observarVagas,
} from '@/lib/firestore';
import type { Entrevista, StatusEntrevista, Vaga } from '@/lib/tipos';
import { hojeIso, minutosParaHora, horaParaMinutos } from '@/lib/horario';

function aplicarMascaraTelefone(entrada: string): string {
  const digitos = entrada.replace(/\D/g, '').slice(0, 11);
  if (digitos.length <= 2) return digitos.length ? `(${digitos}` : '';
  if (digitos.length <= 7) return `(${digitos.slice(0, 2)}) ${digitos.slice(2)}`;
  return `(${digitos.slice(0, 2)}) ${digitos.slice(2, 7)}-${digitos.slice(7)}`;
}

export default function PaginaAgenda() {
  const { usuario } = useAuth();
  const uid = usuario?.uid ?? '';
  const router = useRouter();
  const [entrevistas, setEntrevistas] = useState<Entrevista[]>([]);
  const [vagas, setVagas] = useState<Vaga[]>([]);
  const [data, setData] = useState(hojeIso());
  const [formAberto, setFormAberto] = useState(false);
  const [nome, setNome] = useState('');
  const [telefone, setTelefone] = useState('');
  const [vagaId, setVagaId] = useState('');
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [erro, setErro] = useState('');

  useEffect(() => {
    if (!uid) return;
    return observarEntrevistas(uid, data, setEntrevistas);
  }, [uid, data]);

  useEffect(() => {
    if (!uid) return;
    return observarVagas(uid, setVagas);
  }, [uid]);

  const adicionar = async () => {
    try {
      if (!nome.trim() || !uid) return;
      const anexo = arquivo ? await anexarCurriculo(uid, arquivo) : null;
      await adicionarEntrevista(uid, {
        nome: nome.trim(),
        telefone: telefone.trim(),
        data,
        vagaId: vagaId || null,
        duracaoMinutos: 45,
        curriculo: anexo ? undefined : '',
        tipoCurriculo: anexo?.tipoCurriculo,
        caminhoCurriculo: anexo?.caminhoCurriculo,
        nomeArquivoCurriculo: arquivo?.name ?? '',
      });
      setNome('');
      setTelefone('');
      setVagaId('');
      setArquivo(null);
      setErro('');
      setFormAberto(false);
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível reservar o horário.');
    }
  };

  return (
    <div>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 10,
          flexWrap: 'wrap',
        }}
      >
        <h1 className="titulo" style={{ flex: 1, marginBottom: 0 }}>
          Agenda
        </h1>
        <input
          type="date"
          className="campo"
          style={{ width: 'auto', margin: 0 }}
          value={data}
          onChange={(e) => setData(e.target.value)}
        />
        <button
          className="botao botao-secundario"
          style={{ margin: 0 }}
          onClick={() => {
            if (confirm('Recalcular os horários deste dia?')) recalcularHorariosDoDia(uid, data);
          }}
        >
          Recalcular
        </button>
        <button className="botao" onClick={() => setFormAberto((v) => !v)}>
          Candidato
        </button>
      </div>

      {entrevistas.length === 0 && (
        <p className="dica" style={{ marginTop: 18 }}>
          Nenhum candidato nesta data.
        </p>
      )}

      {entrevistas.map((e) => (
        <CartaoCandidato
          key={e.id}
          entrevista={e}
          vaga={vagas.find((v) => v.id === e.vagaId)?.titulo ?? ''}
          aoAbrir={() => router.push(`/entrevista/${e.id}`)}
          aoSubir={() => moverEntrevista(uid, e.id, -1)}
          aoDescer={() => moverEntrevista(uid, e.id, 1)}
          aoRemover={() => removerEntrevista(uid, e.id)}
          aoMudarStatus={(status) => atualizarStatus(uid, e.id, status)}
          aoAlterarHorario={() => {
            const hora = prompt(
              `Novo horário para ${e.nome} (HH:MM). O horário dele deixa de ser automático.`,
              minutosParaHora(e.inicioMinutos),
            );
            if (!hora) return;
            const minutos = horaParaMinutos(hora.trim());
            if (Number.isFinite(minutos)) {
              atualizarEntrevista(uid, e.id, { inicioMinutos: minutos, inicioManual: true });
            }
          }}
        />
      ))}

      {formAberto && (
        <div className="cartao">
          <strong>Novo candidato — {data}</strong>
          <p className="dica">
            O horário é calculado sozinho pelas Definições. Você pode anexar o
            currículo (PDF ou foto) — ele abre no cartão da entrevista.
          </p>
          <input
            className="campo"
            placeholder="Nome completo"
            value={nome}
            onChange={(e) => setNome(e.target.value)}
          />
          <input
            className="campo"
            placeholder="(XX) 00000-0000"
            value={telefone}
            onChange={(e) => setTelefone(aplicarMascaraTelefone(e.target.value))}
          />
          <select
            className="campo"
            value={vagaId}
            onChange={(e) => setVagaId(e.target.value)}
          >
            <option value="">Vaga (opcional)</option>
            {vagas.map((v) => (
              <option key={v.id} value={v.id}>
                {v.titulo}
              </option>
            ))}
          </select>
          <label className="campo arquivo">
            {arquivo ? `📎 ${arquivo.name}` : '📄 Anexar currículo (PDF ou imagem)'}
            <input
              type="file"
              accept=".pdf,image/*"
              style={{ display: 'none' }}
              onChange={(e) => setArquivo(e.target.files?.[0] ?? null)}
            />
          </label>
          {erro && (
            <p className="dica" style={{ color: 'var(--erro, #c0392b)' }}>
              {erro}
            </p>
          )}
          {arquivo && (
            <div className="dica" style={{ margin: 0 }}>
              O arquivo é enviado ao cadastrar.
            </div>
          )}
          <div className="acoes">
            <button className="botao" onClick={adicionar}>
              Reservar horário
            </button>
            <button
              className="botao botao-secundario"
              onClick={() => setFormAberto(false)}
            >
              Cancelar
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

const ROTULOS: Record<StatusEntrevista, string> = {
  AGENDADA: 'Agendada',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDA: 'Concluída',
  CANCELADA: 'Cancelada',
  NAO_COMPARECEU: 'Não compareceu',
  APROVADO: 'Aprovado',
  REPROVADO: 'Reprovado',
  ENCERRADA: 'Encerrada',
};

function CartaoCandidato({
  entrevista: e,
  vaga,
  aoAbrir,
  aoSubir,
  aoDescer,
  aoRemover,
  aoMudarStatus,
  aoAlterarHorario,
}: {
  entrevista: Entrevista;
  vaga: string;
  aoAbrir: () => void;
  aoSubir: () => void;
  aoDescer: () => void;
  aoRemover: () => void;
  aoMudarStatus: (status: StatusEntrevista) => void;
  aoAlterarHorario: () => void;
}) {
  const conclusa = e.status === 'CONCLUIDA';
  const encerrada = e.status === 'ENCERRADA';

  return (
    <div className="cartao candidato" onClick={aoAbrir} style={{ cursor: 'pointer' }}>
      <div className="hora">{minutosParaHora(e.inicioMinutos)}</div>
      <div className="candidato-nome">
        {e.nome}
        {vaga && <div className="dica" style={{ margin: 0 }}>{vaga}</div>}
        {e.nomeArquivoCurriculo && (
          <div className="dica" style={{ margin: 0 }}>
            📎 {e.nomeArquivoCurriculo}
          </div>
        )}
        {e.inicioManual && (
          <div className="dica" style={{ margin: 0, color: 'var(--primaria)' }}>
            Horário alterado
          </div>
        )}
      </div>
      <span className={`status ${e.status.toLowerCase()}`}>
        {ROTULOS[e.status]}
      </span>
      <div className="acoes" onClick={(ev) => ev.stopPropagation()}>
        {!encerrada && (
          <>
            <button className="icone-botao" onClick={aoSubir} title="Subir na lista">
              ↑
            </button>
            <button className="icone-botao" onClick={aoDescer} title="Descer na lista">
              ↓
            </button>
          </>
        )}
        {e.status === 'AGENDADA' && (
          <>
            <button
              className="botao botao-secundario"
              style={{ padding: '6px 14px', fontSize: 13 }}
              onClick={() => aoMudarStatus('NAO_COMPARECEU')}
            >
              Não compareceu
            </button>
            <button
              className="botao botao-secundario"
              style={{ padding: '6px 14px', fontSize: 13 }}
              onClick={() => aoMudarStatus('CANCELADA')}
            >
              Cancelar
            </button>
          </>
        )}
        {e.status === 'EM_ANDAMENTO' && (
          <button
            className="botao"
            style={{ padding: '6px 14px', fontSize: 13 }}
            onClick={() => aoMudarStatus('CONCLUIDA')}
          >
            Concluir
          </button>
        )}
        {conclusa && (
          <div className="acoes" style={{ margin: 0 }}>
            <button
              className="botao botao-secundario"
              style={{ padding: '6px 14px', fontSize: 13 }}
              onClick={() => aoMudarStatus('ENCERRADA')}
            >
              Encerrar
            </button>
            <button
              className="botao botao-perigo"
              style={{ padding: '6px 14px', fontSize: 13 }}
              onClick={() => aoMudarStatus('REPROVADO')}
            >
              Reprovar
            </button>
            <button
              className="botao"
              style={{ padding: '6px 14px', fontSize: 13 }}
              onClick={() => aoMudarStatus('APROVADO')}
            >
              Aprovar
            </button>
          </div>
        )}
        {['APROVADO', 'REPROVADO'].includes(e.status) && (
          <button
            className="botao botao-secundario"
            style={{ padding: '6px 14px', fontSize: 13 }}
            onClick={() => aoMudarStatus('CONCLUIDA')}
          >
            Voltar
          </button>
        )}
        {!encerrada && (
          <button
            className="icone-botao"
            title="Alterar horário"
            onClick={aoAlterarHorario}
          >
            🕐
          </button>
        )}
        {encerrada && (
          <button
            className="botao botao-secundario"
            style={{ padding: '6px 14px', fontSize: 13 }}
            onClick={() => aoMudarStatus('CONCLUIDA')}
          >
            Reabrir
          </button>
        )}
        {e.status === 'AGENDADA' && (
          <button className="icone-botao" onClick={aoRemover} title="Remover candidato">
            🗑
          </button>
        )}
      </div>
    </div>
  );
}