# ShadowColossusAndroid

Projeto separado para preparar o porte Android ARM64 de [sotc-vibe-pc](https://github.com/LightVelox/sotc-vibe-pc/tree/v0.1.0-preview.1), usando o projeto [Tenkaichi3DecompAndroid](https://github.com/dfdx047/Tenkaichi3DecompAndroid) como referência de arquitetura Android e CI.

> **Estado atual: scaffold de compilação, não é um APK jogável.** Este repositório inicial só confirma que a estrutura Android/Gradle, a NDK ARM64 e uma biblioteca JNI mínima compilam e carregam. A engine PS2Recomp, o runtime de Shadow of the Colossus, o renderizador, áudio e controles do jogo ainda precisam ser portados e integrados.

## O que a CI faz

A cada `push`, pull request ou execução manual em **Actions → Android APK**, o GitHub Actions compila um APK de depuração ARM64 e publica o APK como artefato da execução. A compilação não necessita de uma ISO nem inclui dados do jogo.

## Como obter o APK de teste

1. Abra a aba **Actions** do repositório.
2. Selecione **Android APK** e clique em **Run workflow**, ou envie um commit.
3. Quando a execução terminar, abra a execução e baixe o artefato `ShadowColossusAndroid-debug-apk`.

O APK atual é apenas um aplicativo-base de diagnóstico. Ainda não inicia Shadow of the Colossus.

## Compilar localmente

Requisitos: JDK 17, Android SDK com API 35, Android NDK `27.2.12479018`, CMake `3.22.1` e Gradle `8.9`.

```sh
gradle --no-daemon :app:assembleDebug
```

APK gerado em `app/build/outputs/apk/debug/app-debug.apk`.

## Próximas etapas do porte real

Veja [`docs/ANDROID_PORT_PLAN.md`](docs/ANDROID_PORT_PLAN.md). Em resumo:

1. Fazer o runtime e o código recompilado compilar com Android NDK/Clang para `arm64-v8a`, eliminando dependências exclusivas de Windows/MSVC e instruções x86.
2. Criar uma camada de plataforma Android para arquivos, threads, entrada, áudio e ciclo de vida.
3. Adaptar o renderizador GS para os recursos gráficos de Android; não basta trocar o nome da API. Validar primeiro em Vulkan e avaliar OpenGL ES quando for viável.
4. Integrar a geração dos milhares de arquivos C++ produzidos a partir da imagem do usuário, sem exigir compilação pesada no aparelho.
5. Implementar seleção/verificação da ISO, controles de toque, salvamentos, diagnóstico e otimizações.

## Dados do jogo e direitos

Nenhuma ISO, BIOS, assets, arquivo gerado a partir do jogo ou executável proprietário é distribuído aqui. O fluxo futuro deve exigir que a pessoa forneça sua própria cópia legítima, compatível com a versão suportada pelo projeto original. Antes de copiar código de outros projetos para cá, revisar suas licenças e manter os avisos exigidos.
