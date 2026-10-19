# EntrevistaMais

Agenda de entrevistas para recrutadores, feita nos dois lugares que importam: **app Android nativo** (Kotlin + Jetpack Compose) e **site** (Next.js + React), com a **mesma conta e os mesmos dados** no Firebase.

> Recrutador agendou um candidato no celular pela manhã, abre o computador no meio da tarde e o candidato já está lá — com a mesma vaga, o mesmo currículo e o mesmo horário.

---

## Por que existe

Agendar entrevista costuma virar uma planilha improvisada. O recrutador anota nome, telefone e horário, depois descobre que o horário anterior atrasou e tem que remarcar todo o resto na mão.

O EntrevistaMais inverte a lógica: quem define as regras do dia (horário de início, duração de cada entrevista, intervalo entre candidatos e limite diário) é o recrutador, uma única vez. Depois disso, a agenda se monta sozinha — e se algo mudar no meio do caminho, tudo volta a fazer sentido com um toque.

## Funcionalidades

**Agenda**
- Horários calculados a partir das definições do usuário, com o próximo horário já preenchido na hora de adicionar um candidato
- Contador de vagas usadas do dia (ex.: `5 vagas: 3 usadas`)
- Remoção/movimentação de candidatos dispara o recálculo de todo o restante do dia
- Horário manual com marcação de "Horário alterado" e botão para voltar ao horário calculado
- Encerramento em lote dos avaliados de um dia

**Entrevista**
- Fluxo completo: `AGENDADA → EM_ANDAMENTO → CONCLUÍDA → APROVADO/REPROVADO/ENCERRADA`
- Cronômetro de duração em tempo real
- Abas: **Currículo** (dados pessoais, experiência profissional em tabela editável, resumo e anexo) e **Roteiro**
- Reabertura de entrevista decidida, mantendo as respostas anteriores

**Roteiro de perguntas**
- Perguntas na ordem com respostas automáticas (dia, hora, nome e dados da vaga) — sem digitar o que já se sabe
- Um roteiro pode ser vinculado a uma vaga; sem vínculo, cai no roteiro padrão

**Vagas**
- Cadastro único com salário, jornada, endereço e benefícios
- Os dados da vaga aparecem automaticamente dentro da entrevista

**Candidatos**
- Currículo em PDF ou imagem, guardado **no próprio Firestore** em blocos de 500 000 caracteres (sem depender do Firebase Storage)
- Resumo escrito do currículo, com estado "definalizada"
- Telefone formatado no padrão brasileiro

**Conta e segurança**
- Autenticação por e-mail/senha no Firebase
- Dados isolados por usuário (`usuarios/{uid}/...`) — nenhum recrutador enxerga o dado do outro
- Sessão persistente no app (DataStore) e no site

## Stack

| Camada | Tecnologias |
| --- | --- |
| Android | Kotlin 2.2, Jetpack Compose (BOM 2026.09), Material 3, Navigation Compose, ViewModel + StateFlow |
| Android — dados | Room (cache offline), Firebase Auth, Cloud Firestore, DataStore (sessão) |
| Android — build | Gradle Kotlin DSL, version catalog (`libs.versions.toml`), desugaring de `java.time` para minSdk 24 |
| Web | Next.js 15 (App Router), React 19, TypeScript 5.7, CSS puro (design system próprio) |
| Web — dados | Firebase JS SDK 11 (Auth + Firestore), listeners em tempo real |
| Backend | Firebase Auth, Cloud Firestore (sem functions — toda a regra vive nos clientes) |
| Publicação | APK assinado (v1 + v2 + v3), site no Vercel com deploy automático pelo GitHub |

## Arquitetura

### Android (`app/`)

Arquitetura em camadas com injeção de dependência manual (sem framework de DI, de propósito):

```
com.example.entrevistador
├── core/        Formatters, constantes e utilidades puros
├── domain/      Modelos de negócio (Entrevista, Roteiro, Definicoes) + regras
│                de cálculo de agenda (AgendaScheduler, OrdenacaoAgenda)
├── data/
│   ├── firebase/   Fonte de dados remota (Firestore/Auth)
│   ├── local/      Room — cache e estado offline
│   ├── auth/       Sessão persistida em DataStore
│   └── repository/ Repositórios (Entrevista, Roteiro, Definicoes, Anexo…)
├── di/          AppContainer — composição manual das dependências
└── ui/          Tela por feature (agenda, entrevista, vagas, roteiro…)
```

As telas observam `StateFlow` vindo dos repositórios; o Firestore é a fonte de verdade e o Room serve de cache.

### Web (`web/`)

```
web/src
├── app/
│   ├── page.tsx            Página inicial
│   ├── login / cadastro    Autenticação
│   └── (app)/              Rotas protegidas: agenda, entrevista/[id], vagas, roteiro, definições
├── context/AuthContext.tsx Sessão e estado de carregamento
└── lib/
    ├── firestore.ts        Todas as operações (espelham o app)
    ├── tipos.ts            Modelos e helpers de status
    ├── agenda.ts           Cálculo de horários (mesma regra do Android)
    └── horario.ts          Formatação de data/hora no padrão pt-BR
```

O mesmo domínio do app foi reescrito em TypeScript: `agenda.ts` reproduz `AgendaScheduler.kt` e `firestore.ts` reproduz as validações e mensagens dos repositórios do Android, letra por letra.

## Modelo de dados

```
usuarios/{uid}
├── definicoes/          Regras do dia (início, duração, intervalo, limite)
├── entrevistas/{id}     Candidato, vaga, horário, status, cronômetro
│   ├── respostas/{id}   Resposta a cada pergunta do roteiro
│   └── experiencias/{id}  Linhas da tabela de experiência profissional
├── vagas/{id}           Cargo, salário, jornada, endereço
├── roteiros/{id}        Perguntas na ordem + respostas automáticas
├── curriculos/{id}      Metadados do anexo (nome, tipo, tamanho)
│   └── partes/000000…   Conteúdo em Base64, blocos de 500 000 caracteres
```

**Por que não Firebase Storage?** O plano gratuito não tinha Storage disponível, então o currículo é salvo no Firestore em blocos de base64. O limite prático é ~5 MB por arquivo — suficiente para PDF e imagem de currículo, e custa centavos em leituras.

## Como rodar

### App Android

```bash
git clone https://github.com/RaelRibeiro/EntrevistaMais.git
cd EntrevistaMais

# Android Studio: abrir a pasta e rodar em um dispositivo com Android 8+
.\gradlew.bat assembleRelease
# o APK fica em app/build/outputs/apk/release/
```

### Site

```bash
cd web
npm install
npm run dev     # http://localhost:3000
npm run build   # build de produção
```

### Firebase

O app e o site apontam para o mesmo projeto Firebase (Auth + Firestore), configurado em código:
- `app/src/main/java/com/example/entrevistador/data/firebase/FirebaseConfig.kt`
- `web/src/lib/firebase.ts` (variáveis `NEXT_PUBLIC_FIREBASE_*`)

Para usar com o seu projeto, troque as credenciais nos dois arquivos e publique as regras de segurança permitindo acesso apenas ao dono do documento (`request.auth.uid == uid`).

## Publicação

- **Android**: APK assinado com keystore próprio, `versionCode` incrementado a cada release e verificado com `apksigner` (esquemas v1, v2 e v3 ativos — exigência do Android 11+).
- **Web**: `git push` em `master` dispara o deploy automático no Vercel.

## Decisões técnicas que valem destaque

1. **Domínio único, duas implementações** — a regra de negócio (cálculo de horários, validações, fluxo de status) existe em Kotlin e em TypeScript. Isso mantém o app e o site em sincronia sem um backend próprio.
2. **Recálculo em cascata** — remover, remarcar ou alterar o horário de um candidato reordena o dia inteiro. Feito nos dois lados com a mesma lógica de fila por minutos.
3. **Arquivo sem Storage** — o currículo em Base64 no Firestore removeu uma dependência de infraestrutura e o custo fixo do Storage.
4. **Cache offline com Room** — o app continua utilizável sem rede; quando volta, o Firestore reconcilia por listener.
5. **Compatibilidade mínima de 4.000 dispositivos** — `minSdk 24` com desugaring de `java.time` para não perder o suporte a aparelhos Android 7.
6. **Assinatura moderna** — v1 + v2 + v3 para funcionar de Android 7 até as versões mais recentes sem alerta de instalação insegura.

## Roadmap

- [ ] Categorias e filtros de candidatos
- [ ] Exportação da agenda em PDF
- [ ] Notificações locais de entrevista próxima
- [ ] Publicação em track interno do Google Play
- [ ] Modo offline completo no site

## Autor

Desenvolvido por **Rael Ribeiro** — [github.com/RaelRibeiro](https://github.com/RaelRibeiro)
