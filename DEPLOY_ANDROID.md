# Deploy Android (Google Play) — logi-mobile

Mesma ideia do app `mobile/` (Flutter): dois comandos na raiz do monorepo (`erp/`), e o **push da tag**
é o que dispara o CI/CD (`.github/workflows/android.yml`).

```bash
make logi-release patch      # 1) prepara: sobe version.properties, commita só ele e cria a tag vX.Y.Z (LOCAL)
make logi-publish            # 2) publica: push do branch + da tag = DISPARA o pipeline (pede para digitar a tag)
```

`patch | minor | major | VERSION=x.y.z` · `ARGS=-n` = dry-run · `ARGS=-y` = sem perguntar.

## O que o pipeline faz

| Gatilho | Job | O quê |
|---|---|---|
| Pull request | `test` | `./gradlew test assembleDebug` |
| Tag `v*` | `test` → `deploy` | testes, confere tag = `VERSION_NAME` e `API_BASE_URL` https, gera o `.aab` **assinado** do `:mobile` e envia à trilha `internal` do Google Play |
| "Run workflow" manual | `test` → `deploy` | igual, escolhendo a trilha (internal / alpha / beta / production) |

- Versão: `version.properties` (`VERSION_NAME` + `VERSION_CODE`). O `make logi-release` atualiza os dois; o `VERSION_CODE` só sobe (a Play recusa número repetido). Não edite na mão para publicar.
- O que sobe é o que está **no GitHub**: o CI faz checkout do repo, não do seu disco. O `make logi-release` avisa se houver arquivos sem commit.
- Um único app: `:mobile` (celular). O Android Auto (a tela do carro, quando pluga o celular) vem junto automaticamente — não é um APK ou instalação separada, não existe módulo `:automotive`.

## Configuração que você precisa fazer (uma vez)

### 1. Commit inicial e repositório no GitHub
A pasta `logi-mobile/` tem um `.git` **vazio** (sem commit, sem remote) — diferente dos serviços do ERP.

```bash
cd logi-mobile
git status --short                       # confira: nenhum key.properties / *.jks (o .gitignore já bloqueia)
git add -A && git commit -m "chore: initial import"
git branch -M main
# crie o repo no GitHub (sugestão: ramonmachadocarmo/logi-mobile, privado) e depois:
git remote add origin git@github.com:<usuario>/<repo>.git
git push -u origin main
```
O `make logi-publish` recusa rodar sem commit e sem remote, com mensagem dizendo o que falta.

### 2. Package name (applicationId)
O `com.example.myapplication` **é recusado pela Play Store** (prefixo `com.example` é proibido), então troquei o `applicationId` do `:mobile` para **`com.ramonmachadocarmo.logi`** (o `namespace` do código não mudou). Se preferir outro nome, altere em dois lugares: `mobile/build.gradle.kts` e `PACKAGE_NAME` no `.github/workflows/android.yml` — e use o mesmo ao criar o app no Play Console. **Não dá para trocar depois de publicado.**

### 3. Keystore de upload
**Feito** — `logi-mobile/key.properties` já existe (fora do git) e reaproveita o **mesmo keystore de upload do app `mobile`** (`C:/Users/ramon/keys/erp/upload-keystore.jks`, alias `upload`), em vez de gerar um novo. Isso é seguro: cada app é um listing separado no Play Console (package name diferente), então não há conflito em usar a mesma chave nos dois — só significa uma chave a menos para perder/rotacionar. Se preferir uma chave própria para o logi-mobile (isolamento maior, ao custo de mais uma coisa pra guardar), gere outra:
```bash
keytool -genkeypair -v -storetype JKS -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```
e aponte o `storeFile` do `key.properties` pra ela. Guarde o `.jks` **fora do repo** e as senhas num cofre. Perdeu o keystore de upload = processo de reset com o suporte da Play.

### 4. O que dá pra reaproveitar do app `mobile` — resumo antes de começar

| Coisa | Reaproveita? | Por quê |
|---|---|---|
| Conta do Play Console (a pessoa/organização dona) | ✅ mesma conta, novo app dentro dela | Um app = um package name; a conta é a mesma |
| Keystore de upload (`upload-keystore.jks`) | ✅ já feito (passo 3) | Mesmo arquivo, mesma senha |
| Service account do Google Cloud | ✅ mesma service account, só dar acesso a mais um app | Uma service account pode publicar em vários apps do mesmo Console |
| App no Play Console | ❌ novo, obrigatório | Package name diferente (`com.ramonmachadocarmo.logi`) = listing novo |
| Secrets no GitHub (`ANDROID_*`, `PLAY_SERVICE_ACCOUNT_JSON`) | ⚠️ mesmos **valores**, mas precisam ser recadastrados | Secrets não atravessam repositórios — o repo do `mobile` e o do `logi-mobile` são independentes |

### 5. Play Console — criar o app
1. **play.google.com/console** → entre com a **mesma conta** já usada para o app `mobile`.
2. **Criar app** → nome **"ERP Rotas"** (mesmo nome que já corrigi no `app_name`, o que aparece embaixo do ícone no celular — ver nota abaixo), idioma padrão pt-BR, tipo **App**, gratuito.
3. Em **Configuração do app**, preencha o mínimo pedido (política de privacidade, público-alvo, etc. — os mesmos formulários que você já passou pro `mobile`, agora pra este app).
4. **Integridade do app → Assinatura de apps** → ative **Play App Signing** (é por app; ativar de novo aqui, mesmo reaproveitando o keystore).
5. Gere e suba o **primeiro `.aab` manualmente** (a API do passo 7 não cria o app, só publica em app já existente):
   ```bash
   cd logi-mobile
   ./gradlew :mobile:bundleRelease -PapiBaseUrl=https://erp.personalia.cloud
   # saída: mobile/build/outputs/bundle/release/mobile-release.aab
   ```
   (No Windows, se o `java` não estiver no PATH: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`.) O `key.properties` já está pronto (passo 3), não precisa criar de novo.
6. Suba esse `.aab` em **Teste → Teste interno → Criar release**, preencha as notas da versão e envie para revisão.
7. Contas pessoais criadas após nov/2023 precisam de teste fechado com 12 testadores por 14 dias antes de liberar produção (igual ao app `mobile` — se já cumpriu isso lá, é a mesma conta de desenvolvedor, não recomeça).

> **Nome no Console × nome no aparelho:** são dois campos diferentes. O nome do passo 2 é só a *listagem* na loja — dá pra trocar depois. O nome embaixo do ícone no celular vem do `app_name` em `mobile/src/main/res/values/strings.xml`, que estava **"My Application"** (sobra do template, nunca tinha sido trocado) — já corrigi para **"ERP Rotas"**, mesmo nome sugerido pro Console, pra ficar consistente. Se quiser outro nome, troque nos dois lugares (esse arquivo + o Console) — o app `mobile` (Flutter) usa só **"ERP"**, então "ERP Rotas" segue essa mesma linha.

### 6. Service account — reaproveitando a do `mobile`
Antes de criar uma nova, ache a que já existe:
1. No **Play Console**, abra o app já publicado (package `com.ramonmachadocarmo.erp`, o `mobile`) → **Configuração → Acesso à API**. A lista mostra a(s) service account(s) já vinculada(s) — anote o e-mail (algo como `nome@projeto.iam.gserviceaccount.com`).
2. Volte pro app **novo** (logi-mobile) → **Configuração → Acesso à API** → **Vincular projeto do Google Cloud** (se ainda não estiver vinculado ao mesmo projeto do `mobile`) → na lista de service accounts do projeto, **convide a mesma conta** do passo 1 → dê permissão de **Release** (Gerenciar releases de produção/teste) só pra este app.
3. Você **não precisa gerar uma chave JSON nova** — se ainda tiver o arquivo `.json` usado para o secret `PLAY_SERVICE_ACCOUNT_JSON` do repo `mobile` (ou o conteúdo salvo em algum cofre), é o mesmo conteúdo que vai pro secret deste repo, no passo 8.
   - Se você **não guardou** esse JSON em lugar nenhum (só está como secret no GitHub, que não dá pra reler depois de salvo): Google Cloud Console → IAM e administrador → Contas de serviço → ache a mesma conta (pelo e-mail do passo 1) → aba **Chaves** → **Adicionar chave → Criar nova chave → JSON**. Isso gera uma chave *adicional* pra mesma conta (não invalida a antiga do `mobile`), então é seguro.

Só crie uma service account **nova** (Google Cloud → IAM e administrador → Contas de serviço → Criar conta de serviço, com o papel de acesso à Play Android Developer API) se preferir isolar os dois apps, ou se não achar a antiga.

### 7. GitHub (repo do `logi-mobile`)
1. No repositório do `logi-mobile` (já criado no passo 1) → **Settings → Environments → New environment** → nome **`play-store`** (opcional: marcar "Required reviewers" pra exigir aprovação manual antes do deploy, como no `mobile`).
2. Dentro desse environment, **Add secret** (ou **Add variable** na última linha) um por um:

| Tipo | Nome | Valor | De onde tirar |
|---|---|---|---|
| Secret | `ANDROID_KEYSTORE_BASE64` | saída de `base64 -w0 "C:/Users/ramon/keys/erp/upload-keystore.jks"` | mesmo `.jks` do `mobile` — gera de novo aqui, o valor em si é idêntico ao secret do repo `mobile` |
| Secret | `ANDROID_KEYSTORE_PASSWORD` | a senha do keystore | igual ao secret do repo `mobile` |
| Secret | `ANDROID_KEY_ALIAS` | `upload` | igual ao secret do repo `mobile` |
| Secret | `ANDROID_KEY_PASSWORD` | a senha da chave | igual ao secret do repo `mobile` |
| Secret | `PLAY_SERVICE_ACCOUNT_JSON` | conteúdo do arquivo `.json` (cole o arquivo inteiro) | o mesmo JSON do passo 6, ou a chave nova gerada lá |
| Variable | `API_BASE_URL` | `https://erp.personalia.cloud` | fixo — não depende do `mobile` |

   GitHub não deixa "copiar" um secret de um repo pro outro (nem pra você mesmo, depois de salvo) — é por isso que os quatro primeiros precisam ser digitados de novo aqui, mesmo sendo o mesmo valor de antes.

O deploy **falha de propósito** se `API_BASE_URL` não existir ou não for `https://`, e se a tag não bater com o `VERSION_NAME` — para nunca publicar um app apontando para `localhost`.

### 8. Perfil dos motoristas
O app só libera quem tem, no Perfil (Configurador › Perfis), acesso a **`/logistica/rotas` e `/logistica/entrega`**. Confira antes de distribuir para a trilha internal.

## Como o app escolhe o servidor
- **Release/CI:** `-PapiBaseUrl` vira `BuildConfig.API_BASE_URL` (o CI passa a variável `API_BASE_URL`).
- **Debug sem a propriedade:** emulador → `http://10.0.2.2:8086`; aparelho → `http://localhost:8086` (troque com `ApiConfig.override(...)` ou passe `-PapiBaseUrl=http://IP-DA-LAN:8086`).
- HTTP puro só funciona no **debug** (`src/debug/.../network_security_config.xml`); o release exige HTTPS.

## Quem pode falar com o app no carro (`HostValidator`)

`MyCarAppService` é um `Service` do Android — qualquer app do aparelho pode, em teoria, tentar se
conectar (`bind`) nele fingindo ser "o app do carro". O `HostValidator` é o portão que decide quem
o Android deixa passar. Ele já está configurado:

- **Debug:** `ALLOW_ALL_HOSTS_VALIDATOR` — aceita qualquer um, porque as ferramentas de teste (ADB,
  Desktop Head Unit) não têm a assinatura oficial do Google e seriam bloqueadas por uma allowlist real.
- **Release:** `hosts_allowlist_sample` — uma lista que o **próprio Google publica dentro da
  biblioteca `androidx.car.app`**, com o nome do pacote + a "impressão digital" (hash) da
  assinatura de cada host oficial (o app Android Auto no celular é o que importa aqui, já que não
  existe `:automotive`; a lista também tem o runtime do Android Automotive, sem uso neste app).

Isso não é uma configuração "extra" do módulo automotive que sumiu — protege o próprio `:mobile`.
`MyCarAppService` mora em `:shared` e é fundido no `:mobile` pelo próprio Android para o Android
Auto funcionar; sem essa allowlist, qualquer app malicioso instalado no mesmo celular do motorista
poderia se passar por "app do carro" e acionar as telas deste app — inclusive "Confirmar chegada"
— mesmo sem o motorista estar olhando pro painel. A Play Store também reprova apps de
navegação/carro publicados com `ALLOW_ALL`. Por isso mantive a troca mesmo com o `:automotive` fora.

## Rollback
A Play Store não volta versão pela tag. Pause a distribuição no Play Console (Lançamentos › trilha › Pausar/Interromper) ou publique a correção como versão maior (`make logi-release patch && make logi-publish`). Se o problema for no backend, use o rollback do `vps-release.sh`. Desfazer **antes** de publicar: o `make logi-release` imprime o comando (`git tag -d … && git reset -q HEAD~1 && git checkout -- version.properties`).

## Pendências / decisões que ficaram de fora
- **Release sem R8:** o `build.gradle.kts` mantém `optimization { enable = false }` no release (como estava); ligar reduz o tamanho, mas precisa de regras de keep e teste em aparelho.
- O `TokenStore` (Android Keystore) e a UI não têm teste automatizado no CI (só unitários JVM) — ver o README.
