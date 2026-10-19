# ShadowColossusAndroid

Projeto para portar para Android ARM64 a versão recompilada de [Shadow of the Colossus para PC](https://github.com/LightVelox/sotc-vibe-pc/tree/v0.1.0-preview.1), usando [Tenkaichi3DecompAndroid](https://github.com/dfdx047/Tenkaichi3DecompAndroid) como referência de arquitetura móvel.

> **Estado real: prévia 0.2 em desenvolvimento, ainda não comprovada como jogável.** O repositório agora inclui o overlay touch-screen e sua ponte de entrada nativa, o fluxo local para gerar o código do jogo e diagnóstico nativo em logcat. O APK gerado pela CI continua sem o código recompilado específico do jogo, pois esse código precisa ser gerado a partir de uma ISO do usuário e compilado localmente.

## Código upstream fixado

O projeto de PC está como submódulo em `upstream/sotc-vibe-pc`, no commit da tag `v0.1.0-preview.1`. O submódulo PS2Recomp é obtido recursivamente. Seu CMake reconhece Android, escolhe raylib para a camada de plataforma e ativa compatibilidade SIMD ARM via sse2neon. O app compila os targets de runtime necessários para validar essa base; isso ainda não equivale a executar o jogo.

## Compilação de APK

GitHub Actions compila o APK ARM64 em cada push, pull request ou execução manual.

1. Abra **Actions → Android APK**.
2. Execute **Run workflow** ou abra a execução iniciada pelo último commit.
3. Baixe o artefato `ShadowColossusAndroid-debug-apk`.
4. Baixe `Android-port-audit` para ver o relatório estático das dependências upstream.

O APK ainda não inicia Shadow of the Colossus.

## Importação da ISO

Na tela inicial, selecione sua própria ISO PAL europeia `SCES-53326 v1.00`. O app copia o arquivo para o armazenamento privado e verifica o tamanho esperado de 4.635.918.336 bytes. A identificação real da edição é validada depois pelo runtime por meio do boot ELF e dos módulos esperados; tamanho correto, sozinho, não prova que a ISO é compatível. É necessário espaço interno livre suficiente; a ISO não é enviada ao GitHub.

A ISO, assets, fontes C++ geradas e saves são excluídos do Git.

## Recompilação no próprio celular: estado atual

A referência [Tenkaichi3DecompAndroid](https://github.com/dfdx047/Tenkaichi3DecompAndroid) não recompila a ISO dentro do app: o código decompilado já existe no repositório e o script `port/tools/android.py` compila o motor ARM64 durante o desenvolvimento. No aparelho, o launcher verifica a imagem e copia os dados do disco para a estrutura que o motor usa.

O fluxo de Shadow of the Colossus é diferente: `ps2_recomp` gera arquivos C++ específicos a partir dos executáveis e módulos da ISO; em seguida, esses arquivos precisam ser compilados e ligados ao runtime Android. O APK atual contém o runtime, mas ainda **não inclui um gerador PS2Recomp executável no processo do app nem uma toolchain C/C++ para compilar as fontes durante a execução**.

A tela inicial ganhou uma verificação visual por etapas. Ela mede o progresso **da verificação de pré-requisitos**, identifica a ISO, o espaço livre e a ABI ARM64 e informa a ausência das ferramentas. Os 100% dessa verificação não significam que o jogo foi recompilado. O fluxo real de geração, compilação, ligação e carregamento no Android ainda precisa ser implementado.

## Compilar localmente

Requisitos: Python 3.10+, JDK 17, Android SDK API 35, Android NDK `28.2.13676358`, CMake `3.22.1` e Gradle `8.9`. Python também é usado pelo CMake para aplicar os hooks Android de entrada e diagnóstico ao checkout local dos submódulos.

```sh
gradle --no-daemon :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Gerar o código local e compilar o APK

Com os submódulos recursivos, Python 3.10+, CMake, compilador C/C++, Android SDK/NDK e Gradle configurados, execute `python scripts/prepare_sotc_android.py --iso CAMINHO/SCES-53326.iso --jobs 1`. O script gera o código recompilado no computador e compila por padrão o APK Release otimizado. Para depurar um erro de compilação, informe também `--build-type debug`. Requisitos e diagnósticos: [docs/ANDROID_BUILD_FROM_ISO.md](docs/ANDROID_BUILD_FROM_ISO.md). A ISO e `Port/generated/` permanecem locais.

## Controles touch-screen

A tela do jogo usa um overlay transparente com analógico esquerdo para movimento, analógico direito para câmera, direcional, botões PlayStation (△, ○, ×, □), L1/L2, R1/R2, L3/R3 e Start/Select. O botão **CONTROLES ON/OFF** oculta ou mostra o overlay. O overlay aceita múltiplos toques ao mesmo tempo; o estado de botões e eixos chega ao polling do controle PS2 por uma biblioteca nativa compartilhada. A tela inicial também tem **Compartilhar diagnóstico**: após uma tentativa de boot, envia o log nativo do último início para ajudar a analisar uma tela preta ou erro. A integração ainda precisa ser validada com o jogo real no aparelho.

## Renderer Android

O port PC usa um renderer GS acelerado que exige OpenGL desktop 4.6, compute shaders/SSBOs e, em um dos caminhos, fragment-interlock. Para evitar esse caminho incompatível na inicialização Android, o launcher força temporariamente o GS por software. É um fallback para testar boot, não uma garantia de desempenho: ainda precisamos medir no aparelho e implementar renderer acelerado GLES/Vulkan.

## Etapas críticas restantes

1. Tornar o gerador de código recompilado do upstream compatível com host Linux/Clang.
2. Integrar `Port/generated/` e as fontes específicas do port ao target nativo Android quando forem geradas a partir da imagem de jogo do usuário.
3. Adaptar e validar o renderer GS/shaders para Android; não presumir que uma compilação bem-sucedida comprova compatibilidade gráfica.
4. Integrar a ISO importada com a engine, áudio, controles de toque, saves e diagnóstico.
5. Testar boot e gameplay reais no aparelho.

## Licenças e dados

Nenhuma ISO, BIOS, asset, executável proprietário ou código gerado derivado da imagem do jogo é distribuído. Antes de copiar código de terceiros para este repositório, revisar as licenças e manter os avisos de cada dependência.
