# ShadowColossusAndroid

Projeto para portar para Android ARM64 a versão recompilada de [Shadow of the Colossus para PC](https://github.com/LightVelox/sotc-vibe-pc/tree/v0.1.0-preview.1), usando [Tenkaichi3DecompAndroid](https://github.com/dfdx047/Tenkaichi3DecompAndroid) como referência de arquitetura móvel.

> **Estado real: protótipo Android, ainda não jogável.** O APK tem launcher de diagnóstico, importação verificada da ISO e uma biblioteca JNI. O build Android agora também compila o runtime ARM64/Android e o runner NativeActivity existentes no submódulo PS2Recomp. O código recompilado específico do jogo e o renderizador ainda não estão integrados.

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

Na tela inicial, selecione sua própria ISO PAL europeia `SCES-53326 v1.00`. O app copia o arquivo para o armazenamento privado e verifica o tamanho esperado de 4.635.918.336 bytes e o SHA-256 da versão suportada. É necessário espaço interno livre suficiente; a ISO não é enviada ao GitHub.

A ISO, assets, fontes C++ geradas e saves são excluídos do Git.

## Compilar localmente

Requisitos: JDK 17, Android SDK API 35, Android NDK `28.2.13676358`, CMake `3.22.1` e Gradle `8.9`.

```sh
gradle --no-daemon :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Etapas críticas restantes

1. Tornar o gerador de código recompilado do upstream compatível com host Linux/Clang.
2. Integrar `Port/generated/` e as fontes específicas do port ao target nativo Android quando forem geradas a partir da imagem de jogo do usuário.
3. Adaptar e validar o renderer GS/shaders para Android; não presumir que uma compilação bem-sucedida comprova compatibilidade gráfica.
4. Integrar a ISO importada com a engine, áudio, controles de toque, saves e diagnóstico.
5. Testar boot e gameplay reais no aparelho.

## Licenças e dados

Nenhuma ISO, BIOS, asset, executável proprietário ou código gerado derivado da imagem do jogo é distribuído. Antes de copiar código de terceiros para este repositório, revisar as licenças e manter os avisos de cada dependência.
