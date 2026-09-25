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

### 4. Play Console
1. Criar o app com o package do passo 2; ativar **Play App Signing**.
2. Subir o **primeiro `.aab` manualmente** (a API não cria o app) — o `key.properties` já está pronto:
   ```
   storeFile=C:/Users/ramon/keys/erp/upload-keystore.jks
   storePassword=...
   keyAlias=upload
   keyPassword=...
   ```
   ```bash
   ./gradlew :mobile:bundleRelease -PapiBaseUrl=https://erp.personalia.cloud
   # saída: mobile/build/outputs/bundle/release/mobile-release.aab
   ```
   (No Windows, se o `java` não estiver no PATH: `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`.)
3. Contas pessoais criadas após nov/2023 precisam de teste fechado com 12 testadores por 14 dias antes da produção (igual ao app `mobile`).

### 5. Service account (upload automático)
Google Cloud → habilitar "Google Play Android Developer API" → criar service account e uma chave JSON → Play Console → Usuários e permissões → convidar o e-mail da service account com permissão de release **neste app**. (Pode reaproveitar a service account do app `mobile`, dando acesso também a este app.)

### 6. GitHub (repo do logi-mobile)
Settings → Environments → criar **`play-store`** (opcional: exigir aprovação manual antes do deploy) e cadastrar nele:

| Tipo | Nome | Valor |
|---|---|---|
| Secret | `ANDROID_KEYSTORE_BASE64` | `base64 -w0 upload-keystore.jks` (mesmo `.jks` do app `mobile`, se você reaproveitou o keystore — mas precisa cadastrar de novo aqui: secrets não são compartilhados entre repositórios do GitHub) |
| Secret | `ANDROID_KEYSTORE_PASSWORD` | senha do keystore |
| Secret | `ANDROID_KEY_ALIAS` | `upload` |
| Secret | `ANDROID_KEY_PASSWORD` | senha da chave |
| Secret | `PLAY_SERVICE_ACCOUNT_JSON` | conteúdo do JSON da service account |
| Variable | `API_BASE_URL` | `https://erp.personalia.cloud` (o app chama `/api/...` no gateway) |

O deploy **falha de propósito** se `API_BASE_URL` não existir ou não for `https://`, e se a tag não bater com o `VERSION_NAME` — para nunca publicar um app apontando para `localhost`.

### 7. Perfil dos motoristas
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
