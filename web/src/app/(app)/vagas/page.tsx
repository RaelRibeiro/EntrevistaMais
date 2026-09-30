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
import { horarioDaVaga } from '@/lib/tipos';
import { minutosParaHora, horaParaMinutos } from '@/lib/horario';

const VAZIA: Omit<Vaga, 'id' | 'criadoEm'> = {
  titulo: '',
  empresa: '',
  tipoContrato: '',
  horarioTrabalho: '',
  entradaMinutos: NaN,
  saidaMinutos: NaN,
  salarioBeneficios: '',
  tempoExperiencia: '',
  escolaridade: '',
  exigeHabilitacao: '',
  resumoAtividades: '',
  limiteCandidatos: 0,
};

// Mesmas opções do aplicativo — nada de digitar texto livre.
const OPCOES_CONTRATO = [
  'CLT',
  'PJ',
  'Prestação de serviço',
  'Estágio',
  'Temporário',
  'Autônomo',
];

const OPCOES_ESCOLARIDADE = [
  'Ensino fundamental completo',
  'Ensino fundamental incompleto',
  'Ensino médio completo',
  'Ensino médio incompleto',
  'Ensino superior completo',
  'Ensino superior incompleto',
  'Pós-graduação',
  'Não exige escolaridade',
];

const OPCOES_SIM_NAO = ['Sim', 'Não'];

export default function PaginaVagas() {
  const { usuario } = useAuth();
  const uid = usuario?.uid ?? '';
  const [vagas, setVagas] = useState<Vaga[]>([]);
  const [contagem, setContagem] = useState<Record<string, number>>({});
  const [formulario, setFormulario] = useState<Vaga | null>(null);
  const [aviso, setAviso] = useState('');

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
  }, [uid, vagas.length, formulario]);

  const salvar = async () => {
    if (!formulario || !uid) return;
    if (!formulario.titulo.trim()) {
      setAviso('Informe o título da vaga.');
      return;
    }
    const corpo = {
      titulo: formulario.titulo.trim(),
      empresa: formulario.empresa.trim(),
      tipoContrato: formulario.tipoContrato,
      horarioTrabalho: formulario.horarioTrabalho.trim(),
      entradaMinutos: Number.isFinite(formulario.entradaMinutos)
        ? formulario.entradaMinutos
        : NaN,
      saidaMinutos: Number.isFinite(formulario.saidaMinutos)
        ? formulario.saidaMinutos
        : NaN,
      salarioBeneficios: formulario.salarioBeneficios.trim(),
      tempoExperiencia: formulario.tempoExperiencia.trim(),
      escolaridade: formulario.escolaridade,
      exigeHabilitacao: formulario.exigeHabilitacao,
      resumoAtividades: formulario.resumoAtividades.trim(),
      limiteCandidatos: formulario.limiteCandidatos || 0,
    };
    // Firestore não aceita NaN: horário vazio vira ausência de campo.
    const final = { ...corpo } as Record<string, unknown>;
    if (!Number.isFinite(final.entradaMinutos)) {
      delete final.entradaMinutos;
      delete final.saidaMinutos;
    }
    if (formulario.id) {
      await atualizarVaga(uid, formulario.id, final as Omit<Vaga, 'id' | 'criadoEm'>);
    } else {
      await criarVaga(uid, final as Omit<Vaga, 'id' | 'criadoEm'>);
    }
    setFormulario(null);
    setAviso('');
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
                {[vaga.empresa, horarioDaVaga(vaga), vaga.tipoContrato]
                  .filter(Boolean)
                  .join(' · ')}
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
          aviso={aviso}
        />
      )}
    </div>
  );
}

function Seletor({
  rotulo,
  opcoes,
  valor,
  aoEscolher,
}: {
  rotulo: string;
  opcoes: string[];
  valor: string;
  aoEscolher: (v: string) => void;
}) {
  return (
    <label>
      <span className="dica" style={{ display: 'block', marginBottom: 2 }}>
        {rotulo}
      </span>
      <select
        className="campo"
        value={valor}
        onChange={(e) => aoEscolher(e.target.value)}
      >
        <option value="">Selecione</option>
        {opcoes.map((opcao) => (
          <option key={opcao} value={opcao}>
            {opcao}
          </option>
        ))}
      </select>
    </label>
  );
}

function FormularioVaga({
  vaga,
  aoAlterar,
  aoSalvar,
  aoFechar,
  aviso,
}: {
  vaga: Vaga;
  aoAlterar: (vaga: Vaga) => void;
  aoSalvar: () => void;
  aoFechar: () => void;
  aviso: string;
}) {
  const campo = (chave: keyof Vaga) => ({
    value: String(vaga[chave] ?? ''),
    onChange: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
      aoAlterar({
        ...vaga,
        [chave]: chave === 'limiteCandidatos'
          ? Number.parseInt(e.target.value, 10) || 0
          : e.target.value,
      }),
  });

  const horaDe = (minutos: number) =>
    Number.isFinite(minutos) || (minutos as unknown) !== '' ? minutosParaHora(minutos) : '';

  const definirHora = (chave: 'entradaMinutos' | 'saidaMinutos', valor: string) =>
    aoAlterar({
      ...vaga,
      [chave]: valor ? horaParaMinutos(valor) : NaN,
    });

  return (
    <div className="cartao" style={{ marginTop: 18 }}>
      <strong>{vaga.id ? 'Editar vaga' : 'Nova vaga'}</strong>
      <input className="campo" placeholder="Título da vaga" {...campo('titulo')} />
      <input className="campo" placeholder="Nome da empresa" {...campo('empresa')} />
      <Seletor
        rotulo="Tipo de contrato"
        opcoes={OPCOES_CONTRATO}
        valor={vaga.tipoContrato}
        aoEscolher={(tipoContrato) => aoAlterar({ ...vaga, tipoContrato })}
      />
      <div>
        <span className="dica" style={{ display: 'block', marginBottom: 2 }}>
          Horário de trabalho
        </span>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <input
            className="campo"
            type="time"
            style={{ margin: 0 }}
            value={horaDe(vaga.entradaMinutos)}
            onChange={(e) => definirHora('entradaMinutos', e.target.value)}
            title="Entrada"
          />
          <span>às</span>
          <input
            className="campo"
            type="time"
            style={{ margin: 0 }}
            value={horaDe(vaga.saidaMinutos)}
            onChange={(e) => definirHora('saidaMinutos', e.target.value)}
            title="Saída"
          />
        </div>
      </div>
      <textarea
        className="campo"
        rows={3}
        placeholder="Salário e benefícios"
        {...campo('salarioBeneficios')}
      />
      <input
        className="campo"
        placeholder="Tempo de experiência"
        {...campo('tempoExperiencia')}
      />
      <Seletor
        rotulo="Escolaridade"
        opcoes={OPCOES_ESCOLARIDADE}
        valor={vaga.escolaridade}
        aoEscolher={(escolaridade) => aoAlterar({ ...vaga, escolaridade })}
      />
      <Seletor
        rotulo="Precisa de habilitação?"
        opcoes={OPCOES_SIM_NAO}
        valor={vaga.exigeHabilitacao}
        aoEscolher={(exigeHabilitacao) => aoAlterar({ ...vaga, exigeHabilitacao })}
      />
      <textarea
        className="campo"
        rows={4}
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
      {aviso && (
        <p className="dica" style={{ color: 'var(--erro, #c0392b)' }}>
          {aviso}
        </p>
      )}
      <div className="acoes">
        <button className="botao" onClick={aoSalvar}>Salvar</button>
        <button className="botao botao-secundario" onClick={aoFechar}>Cancelar</button>
      </div>
    </div>
  );
}