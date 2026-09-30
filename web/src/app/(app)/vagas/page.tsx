'use client';

import { useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import {
  observarVagas,
  criarVaga,
  atualizarVaga,
  removerVaga,
  contarCandidatos,
} from '@/lib/firestore';
import type { Vaga } from '@/lib/tipos';

const VAZIA: Omit<Vaga, 'id' | 'criadoEm'> = {
  titulo: '',
  empresa: '',
  tipoContrato: '',
  horarioTrabalho: '',
  salarioBeneficios: '',
  tempoExperiencia: '',
  escolaridade: '',
  exigeHabilitacao: '',
  resumoAtividades: '',
  limiteCandidatos: 0,
};

export default function PaginaVagas() {
  const { usuario } = useAuth();
  const uid = usuario?.uid ?? '';
  const [vagas, setVagas] = useState<Vaga[]>([]);
  const [contagem, setContagem] = useState<Record<string, number>>({});
  const [formulario, setFormulario] = useState<Vaga | null>(null);

  useEffect(() => {
    if (!uid) return;
    return observarVagas(uid, setVagas);
  }, [uid]);

  useEffect(() => {
    if (!uid) return;
    let ativo = true;
    void contarCandidatos(uid).then((c) => {
      if (ativo) setContagem(c);
    });
    return () => {
      ativo = false;
    };
  }, [uid, vagas.length]);

  const salvar = async () => {
    if (!formulario || !uid) return;
    const corpo = {
      titulo: formulario.titulo.trim(),
      empresa: formulario.empresa.trim(),
      tipoContrato: formulario.tipoContrato.trim(),
      horarioTrabalho: formulario.horarioTrabalho.trim(),
      salarioBeneficios: formulario.salarioBeneficios.trim(),
      tempoExperiencia: formulario.tempoExperiencia.trim(),
      escolaridade: formulario.escolaridade.trim(),
      exigeHabilitacao: formulario.exigeHabilitacao.trim(),
      resumoAtividades: formulario.resumoAtividades.trim(),
      limiteCandidatos: formulario.limiteCandidatos || 0,
    };
    if (formulario.id) {
      await atualizarVaga(uid, formulario.id, corpo);
    } else {
      await criarVaga(uid, corpo);
    }
    setFormulario(null);
  };

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <h1 className="titulo" style={{ flex: 1, marginBottom: 0 }}>
          Vagas
        </h1>
        <button className="botao" onClick={() => setFormulario({ ...VAZIA, id: '' } as Vaga)}>
          Nova vaga
        </button>
      </div>

      {vagas.length === 0 && (
        <p className="dica" style={{ marginTop: 18 }}>
          Cadastre a vaga uma vez e o roteiro mostra as informações
          automaticamente no início de cada entrevista.
        </p>
      )}

      {vagas.map((vaga) => (
        <div
          className="cartao"
          key={vaga.id}
          style={{ cursor: 'pointer' }}
          onClick={() => setFormulario(vaga)}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <div style={{ flex: 1 }}>
              <strong>{vaga.titulo}</strong>
              <div className="dica" style={{ margin: 0 }}>
                {vaga.empresa}
                {vaga.limiteCandidatos > 0 &&
                  ` · ${contagem[vaga.id] ?? 0} de ${vaga.limiteCandidatos} candidatos`}
              </div>
            </div>
            <button
              className="icone-botao"
              title="Excluir vaga"
              onClick={(ev) => {
                ev.stopPropagation();
                if (confirm(`Excluir a vaga "${vaga.titulo}"?`)) removerVaga(uid, vaga.id);
              }}
            >
              🗑
            </button>
          </div>
        </div>
      ))}

      {formulario && (
        <FormularioVaga
          vaga={formulario}
          aoAlterar={setFormulario}
          aoSalvar={salvar}
          aoFechar={() => setFormulario(null)}
        />
      )}
    </div>
  );
}

function FormularioVaga({
  vaga,
  aoAlterar,
  aoSalvar,
  aoFechar,
}: {
  vaga: Vaga;
  aoAlterar: (vaga: Vaga) => void;
  aoSalvar: () => void;
  aoFechar: () => void;
}) {
  const campo = (chave: keyof Omit<Vaga, 'id' | 'criadoEm'>) => ({
    value: String(vaga[chave] ?? ''),
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      aoAlterar({
        ...vaga,
        [chave]: chave === 'limiteCandidatos'
          ? Number.parseInt(e.target.value, 10) || 0
          : e.target.value,
      }),
  });

  return (
    <div className="cartao" style={{ marginTop: 18 }}>
      <strong>{vaga.id ? 'Editar vaga' : 'Nova vaga'}</strong>
      <input className="campo" placeholder="Título da vaga" {...campo('titulo')} />
      <input className="campo" placeholder="Empresa" {...campo('empresa')} />
      <input className="campo" placeholder="Tipo de contrato (CLT, PJ…)" {...campo('tipoContrato')} />
      <input className="campo" placeholder="Horário de trabalho" {...campo('horarioTrabalho')} />
      <textarea
        className="campo"
        rows={2}
        placeholder="Salário e benefícios"
        {...campo('salarioBeneficios')}
      />
      <input className="campo" placeholder="Tempo de experiência" {...campo('tempoExperiencia')} />
      <input className="campo" placeholder="Escolaridade" {...campo('escolaridade')} />
      <input className="campo" placeholder="Precisa de habilitação?" {...campo('exigeHabilitacao')} />
      <textarea
        className="campo"
        rows={2}
        placeholder="Resumo das atividades principais"
        {...campo('resumoAtividades')}
      />
      <input
        className="campo"
        type="number"
        min={0}
        placeholder="Limite de candidatos (0 = sem limite)"
        {...campo('limiteCandidatos')}
      />
      <div className="acoes">
        <button className="botao" onClick={aoSalvar}>Salvar</button>
        <button className="botao botao-secundario" onClick={aoFechar}>Cancelar</button>
      </div>
    </div>
  );
}