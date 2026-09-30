'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { observarDefinicoes, salvarDefinicoes, DEFINICOES_PADRAO } from '@/lib/firestore';
import type { Definicoes } from '@/lib/tipos';
import { minutosParaHora, horaParaMinutos } from '@/lib/horario';

export default function PaginaDefinicoes() {
  const { usuario } = useAuth();
  const uid = usuario?.uid ?? '';
  const [form, setForm] = useState<Definicoes>(DEFINICOES_PADRAO);

  useEffect(() => {
    if (!uid) return;
    return observarDefinicoes(uid, setForm);
  }, [uid]);

  const salvar = async () => {
    await salvarDefinicoes(uid, form);
  };

  const alterarHora = (noCampo: keyof Definicoes, valor: string) =>
    setForm((f) => ({ ...f, [noCampo]: horaParaMinutos(valor) }));

  return (
    <div>
      <h1 className="titulo">Definições</h1>
      <p className="subtitulo">
        Como a agenda calcula os horários dos candidatos, no app e no site.
      </p>

      <div className="cartao">
        <label className="secao-titulo">Horário de início</label>
        <input
          className="campo"
          type="time"
          value={minutosParaHora(form.horarioInicioMinutos)}
          onChange={(e) => alterarHora('horarioInicioMinutos', e.target.value)}
        />

        <label className="secao-titulo">Almoço</label>
        <div style={{ display: 'flex', gap: 10 }}>
          <input
            className="campo"
            type="time"
            value={minutosParaHora(form.almocoInicioMinutos)}
            onChange={(e) => alterarHora('almocoInicioMinutos', e.target.value)}
          />
          <input
            className="campo"
            type="time"
            value={minutosParaHora(form.almocoFimMinutos)}
            onChange={(e) => alterarHora('almocoFimMinutos', e.target.value)}
          />
        </div>

        <label className="secao-titulo">Duração por entrevista (minutos)</label>
        <input
          className="campo"
          type="number"
          min={5}
          max={240}
          value={form.duracaoMinutos}
          onChange={(e) =>
            setForm((f) => ({ ...f, duracaoMinutos: Number(e.target.value) || 5 }))
          }
        />

        <label className="secao-titulo">Intervalo entre entrevistas (minutos)</label>
        <input
          className="campo"
          type="number"
          min={0}
          max={120}
          value={form.intervaloMinutos}
          onChange={(e) =>
            setForm((f) => ({ ...f, intervaloMinutos: Number(e.target.value) || 0 }))
          }
        />

        <label className="secao-titulo">Quantidade por dia</label>
        <input
          className="campo"
          type="number"
          min={1}
          max={50}
          value={form.quantidadePorDia}
          onChange={(e) =>
            setForm((f) => ({ ...f, quantidadePorDia: Number(e.target.value) || 1 }))
          }
        />

        <div className="acoes">
          <button className="botao" onClick={salvar}>
            Salvar definições
          </button>
        </div>
      </div>

      <p className="dica">
        Toda vez que um candidato é adicionado, removido ou reordenado, a agenda
        é recalculada com estes valores.
      </p>
    </div>
  );
}