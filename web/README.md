# Entrevistador — site

Versão web do aplicativo Entrevistador. O computador usa o site; o celular usa
o aplicativo Android. Os dois compartilham **a mesma conta e a mesma base de
dados** (Firebase Auth + Firestore), então um candidato cadastrado no celular
aparece no site e vice-versa.

## O que já está pronto

- Login com e-mail/senha e com Google.
- Agenda: adicionar candidato (horário calculado sozinho), reordenar, concluir/aprovar/reprovar.
- Vagas: cadastro com os campos usados na entrevista, limite de candidatos.
- Definições: horário de início, almoço, duração, intervalo e quantidade por dia.
- Roteiro: padrão do processo + um roteiro por vaga; perguntas com tipo e resposta automática.
- Entrevista: início, respostas (editáveis até finalizar), roteiro automático da vaga/padrão.
- Botão de **Download do aplicativo** aparece **somente no celular** (e quando
  `NEXT_PUBLIC_APK_URL` está preenchido).
- PWA: no celular com Chrome, o site pode ser adicionado à tela inicial.

## Estrutura do banco (Firestore)

Tudo fica dentro da conta do usuário, e o aplicativo Android vai usar esta
mesma estrutura:

```
usuarios/{uid}
  ├── definicoes/padrao            (horário de início, almoço, duração…)
  ├── vagas/{vagaId}
  ├── entrevistas/{entrevistaId}
  ├── roteiros/{roteiroId}
  │     └── perguntas/{perguntaId}
  └── respostas/{entrevistaId}     (um documento com {perguntaId: valor})
```

## Como colocar no ar (passo a passo)

### 1. Firebase (banco + login)

1. Acesse https://console.firebase.google.com e crie um projeto (gratuito).
2. No menu **Build → Authentication**, clique em **Começar** e ative dois métodos:
   - **E-mail/Senha** (habilite);
   - **Google** (selecione o e-mail da conta para o popup).
3. No menu **Build → Firestore Database**, clique em **Criar banco de dados**:
   - modo **produção**, região perto de você.
4. Cole as regras de segurança (arquivo `firestore.rules` da pasta `web/`) no
   botão **Regras** da aba Firestore e clique em **Publicar**. Sem isto, o site
   funciona mas ninguém consegue ler/escrever.
5. Em **Configurações do projeto → Seus apps**, adicione um app **Web** (ícone
   `</>`), copie a configuração e preencha o arquivo `web/.env.local` (use o
   `.env.local.example` como modelo).

### 2. Publicar o site no Vercel (gratuito)

1. Suba a pasta `Entrevistador` (o repositório inteiro) para o seu GitHub:
   - `git init && git add . && git commit -m "app + site"` e crie o repositório
     no github.com (guia rápido: https://docs.github.com/pt/get-started).
2. Acesse https://vercel.com, entre com o GitHub e clique em **Add New →
   Project**, importe o repositório.
3. **Framework Preset: Next.js** (o Vercel detecta sozinho). Raiz do projeto:
   a pasta `web/` (campo *Root Directory*).
4. Depois do deploy, em **Settings → Environment Variables** adicione as mesmas
   variáveis do `.env.local` (chaves do mesmo app Firebase). A primeira versão
   sem elas sobe, mas o login só funciona depois de adicionadas e de um novo
   **Redeploy** **→ Deployments**.
5. Pronto: o site fica em `https://seu-projeto.vercel.app`.

### 3. Link de download do aplicativo (Android)

1. Gere o APK: no Android Studio, **Build → Build App Bundle(s)/APK(s) →
   Build APK(s)**. O arquivo sai em
   `app/build/outputs/apk/debug/app-debug.apk`.
2. Suba esse APK numa URL pública direta. Sem custo e simples: crie um
   **GitHub Release** (em *Releases*, crie a release e anexe o `.apk` — vira
   `https://github.com/seuusuario/repo/releases/download/v1/app-debug.apk`).
3. Preencha `NEXT_PUBLIC_APK_URL` com essa URL no Vercel (variável de ambiente)
   e faça um novo deploy. Só então o botão "Download do aplicativo" aparece —
   e apenas no celular.

### 4. Ligar o aplicativo Android ao mesmo banco (próximo passo)

O app hoje usa banco local (Room). Para ele usar o mesmo login e os mesmos
dados do site, o caminho previsto é o source set `app/src/firebase`, que já
existe com o esqueleto do `FirebaseAuthRepository`. A estrutura do Firestore já
está definida (seção acima) para os dois lados ficarem iguais. Isto ainda não
foi implementado — está documentado aqui para quando for feito.

## Comandos de desenvolvimento

```bash
cd web
npm install
npm run dev        # local em http://localhost:3000
npm run typecheck  # checa tipos
npm run build      # build de produção (o Vercel roda isto)
```