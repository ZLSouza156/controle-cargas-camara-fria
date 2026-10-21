# Controle de Cargas Câmara Fria — APK pelo GitHub

Este projeto já contém a versão atual do aplicativo, com a confirmação antes de adiar uma loja. O GitHub Actions instala as ferramentas, prepara o HTML, executa o Android Lint, compila, verifica a assinatura e disponibiliza o APK.

## Fazer o primeiro APK

1. Extraia o ZIP deste projeto no computador.
2. Crie um repositório no GitHub. Pode ser privado.
3. Envie **o conteúdo extraído** para a raiz do repositório. Não envie apenas o ZIP e não deixe tudo dentro de uma pasta extra. Inclua a pasta **`.github/workflows`**; se ela não aparecer no seu gerenciador, habilite a exibição de itens ocultos.
4. Abra **Actions → Gerar APK Android → Run workflow → Run workflow**. O envio de código para `main` ou `master` também inicia a geração automaticamente.
5. Quando a execução ficar verde, abra-a e desça até **Artifacts**.
6. Baixe **Controle-Cargas-APK**, extraia o arquivo e instale o `.apk` no celular Android. Se o celular solicitar, permita a instalação pelo aplicativo usado para abrir o APK.

A raiz deve conter:

```text
.github/workflows/gerar-apk.yml
app/build.gradle
app/src/main/AndroidManifest.xml
app/src/main/java/br/com/camarafria/controlecargas/MainActivity.java
build.gradle
settings.gradle
gradle.properties
scripts/
signing/debug.keystore
web/index.html
```

Sem configurar Secrets, o resultado é **Controle-Cargas-Camara-Fria-teste.apk**, instalável, com uma chave de desenvolvimento fixa. O aplicativo aparece como **Câmara Fria · Teste**. Não é necessário instalar Android Studio no computador para usar a geração pelo GitHub.

## Assinatura para uso definitivo

Para seu APK definitivo, crie uma chave própria **uma única vez** e guarde uma cópia segura dela e da senha. A chave de teste fornecida no projeto é pública e não serve como assinatura privada de produção.

Com Python 3 e JDK 17 no computador:

```bash
python scripts/gerar_chave_release.py
```

Em **Settings → Secrets and variables → Actions → New repository secret**, configure:

| Secret | Valor |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Conteúdo de `private-signing/ANDROID_KEYSTORE_BASE64.txt` |
| `ANDROID_KEYSTORE_PASSWORD` | Senha escolhida ao criar a chave |
| `ANDROID_KEY_ALIAS` | `camara-fria` |
| `ANDROID_KEY_PASSWORD` | A mesma senha |

Execute o workflow novamente. Com os quatro Secrets, ele gera automaticamente **Controle-Cargas-Camara-Fria-release.apk**, assinado com sua chave. Se apenas parte dos Secrets estiver configurada, a geração para com uma mensagem; não gera silenciosamente outro tipo de APK.

Não envie a pasta `private-signing` para o repositório. Ela já está no `.gitignore`. Preserve a mesma chave para todas as atualizações. O APK definitivo tem identificador próprio, separado do teste; ao trocar de teste para definitivo, use Exportar/Importar Configurações para levar os dados.

## Atualizar o aplicativo depois

Substitua somente **`web/index.html`** pelo HTML atualizado e envie a alteração. O workflow gera o próximo APK automaticamente. O script adapta arquivos e navegação durante a compilação, sem precisar editar o HTML à mão.

Mantenha o mesmo identificador do aplicativo, a mesma chave e a mesma pasta de projeto ao atualizar. O código de versão usa o minuto de compilação para crescer nas próximas gerações. Instale a atualização sobre o aplicativo existente; desinstalar ou limpar os dados apaga o armazenamento local.

## Dados, funcionamento offline e arquivos

- O HTML está dentro do APK. O aplicativo funciona sem conexão; não tem permissão de Internet.
- Cadastros e cargas continuam no armazenamento local do aplicativo.
- **Exportar Configurações** abre o seletor Android para salvar o `.json` onde você escolher.
- **Importar Configurações** abre o seletor Android para escolher o arquivo exportado.
- **Compartilhar resumo** abre o compartilhamento Android com o `.txt`.
- O botão Voltar do Android fecha as opções e retorna pelas telas do aplicativo.
- O app pede espaço acima do teclado por meio dos ajustes de janela e da interface existente.
- Os dados do HTML aberto no navegador **não entram automaticamente no APK**. Exporte as configurações no navegador e importe no aplicativo após instalar.
- A interface precisa de um Android System WebView atualizado. O mínimo definido no projeto é Android 6, mas aparelhos antigos podem não oferecer um WebView compatível com todo o JavaScript atual.

## Se a geração falhar

Abra **Actions → execução com erro → etapa vermelha** e leia o log. A geração não publica APK se a compilação, o Lint ou a verificação de assinatura falhar.

- Workflow ausente: confira se `.github/workflows/gerar-apk.yml` está na raiz do repositório e na branch padrão.
- HTML ausente: confira `web/index.html`.
- Assinatura incompleta: configure os quatro Secrets ou remova os quatro para voltar ao APK de teste.
- Atualização não instala: confirme que é o mesmo tipo de APK (teste ou definitivo), identificador e chave. Exporte os dados antes de qualquer desinstalação.
- Não altere as versões das ferramentas isoladamente: este projeto fixa AGP 8.9.2, Gradle 8.11.1, Java 17 e SDK 35.

## Validação e limites desta entrega

Foram verificados a estrutura do projeto, a configuração do workflow, os scripts e a integração JavaScript com os arquivos e a navegação. A compilação real do APK e a instalação em aparelho devem ser verificadas na primeira execução do GitHub Actions; não foi executada uma compilação Android completa neste ambiente.

## Documentação das ferramentas

- [Compatibilidade do Android Gradle Plugin](https://developer.android.com/build/releases/agp-8-9-0-release-notes)
- [Conteúdo local no WebView](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)
- [Gradle no GitHub Actions](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md)
- [Baixar artefatos do workflow](https://docs.github.com/en/actions/tutorials/store-and-share-data)
