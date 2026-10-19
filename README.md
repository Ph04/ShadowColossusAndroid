# ShadowColossusAndroid

Projeto para portar para Android ARM64 a versão recompilada de [Shadow of the Colossus para PC](https://github.com/LightVelox/sotc-vibe-pc/tree/v0.1.0-preview.1), usando [Tenkaichi3DecompAndroid](https://github.com/dfdx047/Tenkaichi3DecompAndroid) como referência de arquitetura e compilação móvel.

> **Estado real: protótipo Android, ainda não jogável.** O app tem agora uma tela de diagnóstico, uma biblioteca JNI mínima e importação da ISO com verificação de tamanho e SHA-256. A engine recompilada, renderizador, áudio e controles do jogo ainda precisam ser portados.

## Código upstream fixado

O projeto de PC está vinculado como submódulo em `upstream/sotc-vibe-pc`, fixado em `v0.1.0-preview.1`. O PS2Recomp é mantido como submódulo do upstream e é obtido recursivamente no CI. Essa separação permite estudar e adaptar a engine sem modificar o repositório original.

## Compilação de APK

O GitHub Actions compila um APK de depuração `arm64-v8a` em cada push, pull request ou execução manual.

1. Abra **Actions → Android APK**.
2. Execute **Run workflow** ou use a execução iniciada pelo último commit.
3. Baixe o artefato `ShadowColossusAndroid-debug-apk` para obter o APK de teste.
4. Baixe também `Android-port-audit` para ver o relatório gerado a partir do código upstream fixado.

O APK de diagnóstico não inicia o jogo até a engine real ser portada. O sucesso da compilação do launcher não deve ser confundido com gameplay funcional.

## Importação da ISO

Na tela inicial, selecione sua própria ISO PAL europeia `SCES-53326 v1.00`. O app copia para o armazenamento privado e confere o tamanho esperado de 4.635.918.336 bytes e o SHA-256 da versão suportada. É necessário espaço interno livre suficiente para armazenar a imagem; ela não é enviada ao GitHub.

A ISO, os assets, os arquivos C++ gerados a partir da imagem e os saves são ignorados pelo Git. Nunca envie esses arquivos ao repositório.

## Compilar localmente

Requisitos: JDK 17, Android SDK API 35, NDK `27.2.12479018`, CMake `3.22.1` e Gradle `8.9`.

```sh
gradle --no-daemon :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Próximos marcos técnicos

Veja [`docs/ANDROID_PORT_PLAN.md`](docs/ANDROID_PORT_PLAN.md). O relatório estático [`PORT_STATUS.md`](PORT_STATUS.md) diferencia o que existe hoje das etapas ainda abertas.

- Compilar ferramentas de geração do upstream em Linux/Clang e produzir o código recompilado a partir de uma cópia de jogo fornecida pelo usuário.
- Portar o runtime para Android NDK/Clang ARM64 e substituir dependências de Windows/MSVC e intrínsecas x86.
- Adaptar corretamente o renderizador GS e sincronização gráfica para Android, provavelmente começando por Vulkan.
- Integrar áudio, controles de toque, gamepad, saves e diagnóstico.
- Testar boot e gameplay reais no aparelho.

## Licenças e dados

Este repositório não distribui jogo, ISO, BIOS, assets ou código gerado derivado de uma imagem de jogo. Antes de transportar código de terceiros para este repositório, é obrigatório revisar as licenças e os avisos de cada dependência.
