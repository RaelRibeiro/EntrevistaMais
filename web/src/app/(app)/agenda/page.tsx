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
  encerrarAvaliados,
  voltarHorarioPadrao,
  observarVagas,
  proximoHorarioMinutos,
} from '@/lib/firestore';
import type { Entrevista, StatusEntrevista, TipoCurriculo, Vaga } from '@/lib/tipos';
import { ROTULO_STATUS, ehDecidido, ehEncerravel } from '@/lib/tipos';
import {
  hojeIso,
  minutosParaHora,
  horaParaMinutos,
  paraTituloAgenda,
  somarDias,
} from '@/lib/horario';

function aplicarMascaraTelefone(entrada: string): string {
  const digitos = entrada.replace(/\D/g, '').slice(0, 11);
  if (digitos.length <= 2) return digitos.length ? `(${digitos}` : '';
  if (digitos.length <= 7) return `(${digitos.slice(0, 2)}) ${digitos.slice(2)}`;
  return `(${digitos.slice(0, 2)}) ${digitos.slice(2, 7)}-${digitos.slice(7)}`;
}

/**
 * Atributos que impedem o navegador de abrir o preenchimento automático sobre os
 * campos da agenda.
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

export default function PaginaAgenda() {
  const { usuario } = useAuth();
  const uid = usuario?.uid ?? '';
  const router = useRouter();
  const [entrevistas, setEntrevistas] = useState<Entrevista[]>([]);
  const [vagas, setVagas] = useState<Vaga[]>([]);
  const [data, setData] = useState(hojeIso());
  const [formAberto, setFormAberto] = useState(false);
  const [mostrarEncerradas, setMostrarEncerradas] = useState(false);
  const [confirmarEncerramento, setConfirmarEncerramento] = useState(false);
  const [confirmarRemocao, setConfirmarRemocao] = useState<Entrevista | null>(null);
  const [nome, setNome] = useState('');
  const [telefone, setTelefone] = useState('');
  const [vagaId, setVagaId] = useState('');
  const [modoCurriculo, setModoCurriculo] = useState<'RESUMO' | 'ANEXO'>('RESUMO');
  const [resumoCurriculo, setResumoCurriculo] = useState('');
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [horaManual, setHoraManual] = useState<number | null>(null);
  const [horarioCalculado, setHorarioCalculado] = useState<number | null>(null);
  const [erro, setErro] = useState('');
  const [salvando, setSalvando] = useState(false);

  useEffect(() => {
    if (!uid) return;
    return observarEntrevistas(uid, data, setEntrevistas);
  }, [uid, data]);

  useEffect(() => {
    if (!uid) return;
    return observarVagas(uid, setVagas);
  }, [uid]);

  const visiveis = mostrarEncerradas
    ? entrevistas
    : entrevistas.filter((e) => e.status !== 'ENCERRADA');
  const encerradas = entrevistas.filter((e) => e.status === 'ENCERRADA').length;
  const encerraveis = entrevistas.filter((e) => ehEncerravel(e.status)).length;

  const abrirFormulario = async () => {
    if (!uid) return;
    const seguinte = await proximoHorarioMinutos(uid, data);
    setHorarioCalculado(seguinte);
    setHoraManual(seguinte);
    setErro('');
    setFormAberto(true);
  };

  const adicionar = async () => {
    if (salvando) return;
    setSalvando(true);
    setErro('');
    try {
      let anexo: { caminhoCurriculo: string; tipoCurriculo: TipoCurriculo } | null = null;
      if (modoCurriculo === 'ANEXO' && arquivo) {
        try {
          anexo = await anexarCurriculo(uid, arquivo);
        } catch {
          setErro('Currículo não pôde ser enviado — tente de novo.');
          return;
        }
      }
      const inicioMinutos = horaManual ?? horarioCalculado;
      await adicionarEntrevista(uid, {
        nome,
        telefone,
        data,
        vagaId: vagaId ? Number(vagaId) : null,
        curriculo: modoCurriculo === 'RESUMO' ? resumoCurriculo : undefined,
        tipoCurriculo: anexo?.tipoCurriculo,
        caminhoCurriculo: anexo?.caminhoCurriculo,
        nomeArquivoCurriculo: arquivo?.name ?? '',
        inicioMinutos: inicioMinutos ?? null,
      });
      setNome('');
      setTelefone('');
      setVagaId('');
      setModoCurriculo('RESUMO');
      setResumoCurriculo('');
      setArquivo(null);
      setHoraManual(null);
      setHorarioCalculado(null);
      setFormAberto(false);
    } catch (e) {
      setErro(e instanceof Error ? e.message : 'Não foi possível reservar o horário.');
    } finally {
      setSalvando(false);
    }
  };

  return (
    <div>
      <div className="cabecalho-agenda">
        <button
          className="icone-botao"
          onClick={() => setData(somarDias(data, -1))}
          title="Dia anterior"
        >
          ‹
        </button>
        <h1 className="titulo" style={{ margin: 0, flex: 1, textAlign: 'center' }}>
          <input
            type="date"
            className="campo"
            style={{ width: 'auto', margin: 0, display: 'inline-block' }}
            value={data}
            onChange={(e) => setData(e.target.value)}
            title={paraTituloAgenda(data)}
          />
        </h1>
        <button
          className="icone-botao"
          onClick={() => setData(somarDias(data, 1))}
          title="Próximo dia"
        >
          ›
        </button>
      </div>
      <p className="titulo" style={{ textAlign: 'center', margin: '4px 0 12px' }}>
        {paraTituloAgenda(data)}
      </p>

      <div className="linha-dia">
        <div className="barra-vagas">
          <div
            className="barra-vagas-preenchida"
            style={{
              width: vagas.length
                ? `${Math.min(100, (entrevistas.length / vagas.length) * 100)}%`
                : '0%',
            }}
          />
        </div>
        <span className="dica" style={{ margin: 0, whiteSpace: 'nowrap' }}>
          {entrevistas.length} de {vagas.length}
        </span>
        <button className="botao botao-texto" onClick={() => setData(hojeIso())}>
          Hoje
        </button>
      </div>

      {(encerraveis > 0 || encerradas > 0) && (
        <div className="linha-acoes-dia">
          {encerraveis > 0 && (
            <button
              className="botao botao-secundario"
              onClick={() => setConfirmarEncerramento(true)}
            >
              {encerraveis === 1
                ? 'Encerrar 1 avaliado'
                : `Encerrar ${encerraveis} avaliados`}
            </button>
          )}
          {encerradas > 0 && (
            <button
              className="botao botao-texto"
              onClick={() => setMostrarEncerradas((v) => !v)}
            >
              {mostrarEncerradas
                ? 'Ocultar encerrados'
                : `Ver ${encerradas} encerrados`}
            </button>
          )}
        </div>
      )}

      {visiveis.length === 0 && (
        <p className="dica" style={{ marginTop: 18 }}>
          {entrevistas.length > 0
            ? 'Nenhuma entrevista neste dia'
            : 'Nenhuma entrevista neste dia'}
        </p>
      )}

      {erro && <p className="erro">{erro}</p>}

      {visiveis.map((e) => (
        <CartaoCandidato
          key={e.id}
          entrevista={e}
          aoAbrir={() => router.push(`/entrevista/${e.id}`)}
          aoSubir={() => moverEntrevista(uid, e.id, -1)}
          aoDescer={() => moverEntrevista(uid, e.id, 1)}
          aoRemover={() => setConfirmarRemocao(e)}
          aoCancelar={() => atualizarStatus(uid, e.id, 'CANCELADA')}
          aoNaoCompareceu={() => atualizarStatus(uid, e.id, 'NAO_COMPARECEU')}
          aoReabrir={() => atualizarStatus(uid, e.id, 'AGENDADA')}
          aoAprovar={() => atualizarStatus(uid, e.id, 'APROVADO')}
          aoReprovar={() => atualizarStatus(uid, e.id, 'REPROVADO')}
          aoEncerrar={() => atualizarStatus(uid, e.id, 'ENCERRADA')}
          aoAlterarHorario={() => {
            const hora = prompt(
              `Alterar horário de ${e.nome} (HH:MM). O horário dele deixa de ser automático.`,
              minutosParaHora(e.inicioMinutos),
            );
            if (!hora) return;
            const minutos = horaParaMinutos(hora.trim());
            if (Number.isFinite(minutos)) {
              atualizarEntrevista(uid, e.id, {
                inicioMinutos: minutos,
                inicioManual: true,
              });
            }
          }}
          aoVoltarHorarioPadrao={() => voltarHorarioPadrao(uid, e.id)}
        />
      ))}

      {!formAberto && (
        <button className="botao" style={{ width: '100%' }} onClick={abrirFormulario}>
          Adicionar candidato
        </button>
      )}

      {formAberto && (
        <div className="fundo-dialogo">
          <div className="dialogo">
            <strong>Novo candidato</strong>
            <p className="dica">
              O horário já vem calculado pela ordem do dia. Se precisar de outro, é só
              trocar aqui.
            </p>

            {horarioCalculado === null ? (
              <p className="erro" style={{ margin: '0 0 10px' }}>
                Dia cheio
              </p>
            ) : (
              <>
                <div className="campo-hora">
                  <select
                    className="campo"
                    value={Math.floor((horaManual ?? horarioCalculado) / 60)}
                    onChange={(e) => {
                      const minuto = (horaManual ?? horarioCalculado) % 60;
                      setHoraManual(Number(e.target.value) * 60 + minuto);
                    }}
                  >
                    {Array.from({ length: 24 }, (_, h) => (
                      <option key={h} value={h}>
                        {String(h).padStart(2, '0')}
                      </option>
                    ))}
                  </select>
                  <strong>:</strong>
                  <select
                    className="campo"
                    value={(() => {
                      const minuto = (horaManual ?? horarioCalculado) % 60;
                      return minuto % 10 === 0 ? minuto / 10 : Math.round(minuto / 10);
                    })()}
                    onChange={(e) => {
                      const hora = Math.floor((horaManual ?? horarioCalculado) / 60);
                      setHoraManual(hora * 60 + Number(e.target.value) * 10);
                    }}
                  >
                    {Array.from({ length: 6 }, (_, i) => (
                      <option key={i} value={i}>
                        {String(i * 10).padStart(2, '0')}
                      </option>
                    ))}
                  </select>
                  {horaManual !== horarioCalculado && (
                    <button
                      className="botao botao-texto"
                      onClick={() => setHoraManual(horarioCalculado)}
                    >
                      Usar o calculado
                    </button>
                  )}
                </div>
              </>
            )}

<label className="rotulo">
              Nome
              <input
                className="campo"
                name="candidato_nome"
                {...SEM_AUTOFILL}
                value={nome}
                onChange={(e) => setNome(e.target.value)}
              />
            </label>
            <label className="rotulo">
              Telefone
              <input
                className="campo"
                name="candidato_telefone"
                {...SEM_AUTOFILL}
                value={telefone}
                onChange={(e) => setTelefone(aplicarMascaraTelefone(e.target.value))}
              />
            </label>

            <div className="abas-curriculo">
              <button
                type="button"
                className={modoCurriculo === 'RESUMO' ? 'selecionado' : ''}
                onClick={() => setModoCurriculo('RESUMO')}
              >
                Resumo
              </button>
              <button
                type="button"
                className={modoCurriculo === 'ANEXO' ? 'selecionado' : ''}
                onClick={() => setModoCurriculo('ANEXO')}
              >
                Anexar
              </button>
            </div>

            {modoCurriculo === 'RESUMO' ? (
              <label className="rotulo">
                Resumo do currículo
                <textarea
                  className="campo"
                  rows={3}
                  name="candidato_resumo"
                  {...SEM_AUTOFILL}
                  placeholder="Formação, experiência e objetivo do candidato."
                  value={resumoCurriculo}
                  onChange={(e) => setResumoCurriculo(e.target.value)}
                />
              </label>
            ) : arquivo ? (
              <div className="anexo-escolhido">
                <span className="anexo-nome">{arquivo.name}</span>
                <button
                  type="button"
                  className="botao botao-texto"
                  onClick={() => setArquivo(null)}
                >
                  Trocar
                </button>
              </div>
            ) : (
              <>
                <label className="botao botao-secundario" style={{ width: '100%' }}>
                  Escolher PDF ou imagem
                  <input
                    type="file"
                    accept=".pdf,image/*"
                    style={{ display: 'none' }}
                    onChange={(e) => setArquivo(e.target.files?.[0] ?? null)}
                  />
                </label>
                <p className="dica" style={{ margin: '6px 0 0' }}>
                  O arquivo é guardado junto do candidato, então não depende de link.
                </p>
              </>
            )}

            {vagas.length > 0 && (
              <label className="rotulo">
                Vaga
                <select
                  className="campo"
                  value={vagaId}
                  onChange={(e) => setVagaId(e.target.value)}
                >
                  <option value="">Sem vaga</option>
                  {vagas.map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.titulo}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {erro && <p className="erro">{erro}</p>}

            <div className="acoes">
              <button className="botao" onClick={adicionar} disabled={salvando}>
                {salvando ? 'Salvando…' : 'Adicionar'}
              </button>
              <button
                className="botao botao-secundario"
                onClick={() => setFormAberto(false)}
                disabled={salvando}
              >
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmarEncerramento && (
        <div className="fundo-dialogo">
          <div className="dialogo">
            <strong>Encerrar avaliados?</strong>
            <p className="dica">
              {encerraveis === 1
                ? 'O candidato sai da lista do dia, mas o registro, as respostas e o currículo continuam guardados.'
                : `Os ${encerraveis} candidatos avaliados saem da lista do dia, mas os registros, respostas e currículos continuam guardados.`}
            </p>
            <div className="acoes">
              <button
                className="botao"
                onClick={() => {
                  setConfirmarEncerramento(false);
                  void encerrarAvaliados(uid, data);
                }}
              >
                Encerrar
              </button>
              <button
                className="botao botao-secundario"
                onClick={() => setConfirmarEncerramento(false)}
              >
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmarRemocao && (
        <div className="fundo-dialogo">
          <div className="dialogo">
            <strong>Remover candidato?</strong>
            <p className="dica">
              {confirmarRemocao.nome} sai da agenda e os horários são reajustados.
            </p>
            <div className="acoes">
              <button
                className="botao botao-perigo"
                onClick={() => {
                  removerEntrevista(uid, confirmarRemocao.id);
                  setConfirmarRemocao(null);
                }}
              >
                Remover
              </button>
              <button
                className="botao botao-secundario"
                onClick={() => setConfirmarRemocao(null)}
              >
                Cancelar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function CartaoCandidato({
  entrevista: e,
  aoAbrir,
  aoSubir,
  aoDescer,
  aoRemover,
  aoCancelar,
  aoNaoCompareceu,
  aoReabrir,
  aoAprovar,
  aoReprovar,
  aoEncerrar,
  aoAlterarHorario,
  aoVoltarHorarioPadrao,
}: {
  entrevista: Entrevista;
  aoAbrir: () => void;
  aoSubir: () => void;
  aoDescer: () => void;
  aoRemover: () => void;
  aoCancelar: () => void;
  aoNaoCompareceu: () => void;
  aoReabrir: () => void;
  aoAprovar: () => void;
  aoReprovar: () => void;
  aoEncerrar: () => void;
  aoAlterarHorario: () => void;
  aoVoltarHorarioPadrao: () => void;
}) {
  const [menuAberto, setMenuAberto] = useState(false);
  const emAndamento = e.status === 'EM_ANDAMENTO';
  const concluida = e.status === 'CONCLUIDA';
  const decidida = ehDecidido(e.status);
  const encerrada = e.status === 'ENCERRADA';

  const acaoPrincipal = emAndamento ? (
    <button className="botao" onClick={(ev) => { ev.stopPropagation(); aoAbrir(); }}>
      Abrir
    </button>
  ) : concluida ? (
    <span className="acoes" style={{ borderTop: 'none', paddingTop: 0 }}>
      <button
        className="icone-botao"
        onClick={(ev) => { ev.stopPropagation(); aoReprovar(); }}
        title="Reprovar para a próxima fase"
      >
        ✕
      </button>
      <button
        className="botao"
        onClick={(ev) => { ev.stopPropagation(); aoAprovar(); }}
      >
        Aprovar
      </button>
    </span>
  ) : decidida ? (
    <span className="acoes" style={{ borderTop: 'none', paddingTop: 0 }}>
      <button
        className="icone-botao"
        onClick={(ev) => { ev.stopPropagation(); aoReabrir(); }}
        title="Voltar para concluída"
      >
        ↺
      </button>
      {e.status === 'APROVADO' ? (
        <button
          className="botao botao-secundario"
          style={{ padding: '6px 14px', fontSize: 13 }}
          onClick={(ev) => { ev.stopPropagation(); aoReprovar(); }}
        >
          Reprovar
        </button>
      ) : (
        <button
          className="botao"
          style={{ padding: '6px 14px', fontSize: 13 }}
          onClick={(ev) => { ev.stopPropagation(); aoAprovar(); }}
        >
          Aprovar
        </button>
      )}
    </span>
  ) : encerrada ? (
    <button
      className="icone-botao"
      onClick={(ev) => { ev.stopPropagation(); aoAbrir(); }}
      title="Reabrir candidato encerrado"
    >
      ↺
    </button>
  ) : e.status === 'CANCELADA' || e.status === 'NAO_COMPARECEU' ? (
    <button
      className="botao botao-secundario"
      style={{ padding: '6px 14px', fontSize: 13 }}
      onClick={(ev) => { ev.stopPropagation(); aoReabrir(); }}
    >
      Reabrir
    </button>
  ) : (
    <button
      className="botao"
      onClick={(ev) => { ev.stopPropagation(); aoAbrir(); }}
    >
      Iniciar
    </button>
  );

  const itens: { texto: string; ativo: boolean; acao: () => void }[] = [
    { texto: 'Subir na lista', ativo: !emAndamento, acao: aoSubir },
    { texto: 'Descer na lista', ativo: !emAndamento, acao: aoDescer },
    {
      texto: 'Aprovar para a próxima fase',
      ativo: (concluida || decidida) && e.status !== 'APROVADO',
      acao: aoAprovar,
    },
    {
      texto: 'Reprovar',
      ativo: (concluida || decidida) && e.status !== 'REPROVADO',
      acao: aoReprovar,
    },
    {
      texto: e.inicioManual ? 'Editar horário alterado' : 'Alterar horário',
      ativo: !emAndamento,
      acao: aoAlterarHorario,
    },
    {
      texto: 'Voltar ao horário das Definições',
      ativo: !emAndamento && e.inicioManual,
      acao: aoVoltarHorarioPadrao,
    },
    {
      texto: 'Encerrar candidato',
      ativo: !emAndamento && ehEncerravel(e.status),
      acao: aoEncerrar,
    },
    {
      texto: 'Não compareceu',
      ativo: !emAndamento && !ehEncerravel(e.status),
      acao: aoNaoCompareceu,
    },
    {
      texto: 'Cancelar entrevista',
      ativo: !emAndamento && !ehEncerravel(e.status),
      acao: aoCancelar,
    },
    {
      texto: 'Reabrir',
      ativo: !emAndamento && (concluida || decidida || encerrada || e.status !== 'AGENDADA'),
      acao: aoReabrir,
    },
    { texto: 'Remover candidato', ativo: !emAndamento, acao: aoRemover },
  ];

  return (
    <div className="cartao candidato">
      <div className="hora">{minutosParaHora(e.inicioMinutos)}</div>
      <div className="candidato-nome" onClick={aoAbrir} style={{ cursor: 'pointer' }}>
        {e.nome}
        {e.inicioManual && <div className="dica hora-alterada">Horário alterado</div>}
        <span className={`status ${e.status.toLowerCase()}`}>
          {ROTULO_STATUS[e.status]}
        </span>
      </div>
      <span onClick={(ev) => ev.stopPropagation()}>{acaoPrincipal}</span>
      <span className="menu-acoes">
        <button
          className="icone-botao"
          onClick={() => setMenuAberto((v) => !v)}
          title="Mais ações"
        >
          ⋮
        </button>
        {menuAberto && (
          <div className="menu-lista">
            {itens.map((item) => (
              <button
                key={item.texto}
                className="menu-item"
                disabled={!item.ativo}
                onClick={() => {
                  setMenuAberto(false);
                  item.acao();
                }}
              >
                {item.texto}
              </button>
            ))}
          </div>
        )}
      </span>
    </div>
  );
}