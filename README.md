# Celosia Android

Port Android em desenvolvimento para Celosia / Shadow of the Colossus.
Este repositório mantém a integração Android separada do código original e
fixa o Celosia upstream como submódulo para manter runtime, recompiler e
correções de jogo sincronizados.

## Estado atual

- O estado anterior do experimento Android está na branch
  backup/pre-celosia-cleanup-2026-10-27.
- A branch main agora usa HenryKun55/celosia como submódulo em upstream/celosia.
- Este repositório contém a sobreposição Android, script de integração e
  auditoria estática.
- Não há APK validado. O renderer nativo OpenGL do Celosia ainda não tem
  backend Android; a primeira meta é inicializar o runtime e validar o caminho
  gráfico emulado.

## Checkout

    git clone --recurse-submodules https://github.com/Ph04/ShadowColossusAndroid.git
    cd ShadowColossusAndroid

Se já foi clonado sem submódulos:

    git submodule update --init --recursive

## Aplicar a integração Android

Na raiz do repositório:

    python3 scripts/apply_android_overlay.py

O script instala os arquivos Gradle e o auxiliar Android na árvore upstream e
integra os controles touch no leitor de pad e no ciclo de desenho. Ele pode ser
executado mais de uma vez. Para verificar os pontos de integração sem alterar:

    python3 scripts/apply_android_overlay.py --check-only

## Preparar código do jogo e compilar

A recompilação precisa da sua própria cópia legítima do disco. Nenhum ISO, ELF,
XFF, dump ou código C++ derivado do jogo é distribuído aqui.

1. Aplique a integração acima.
2. No diretório upstream/celosia, execute no computador host:

       ./celosia setup --iso /caminho/para/sua-copia/Shadow-of-the-Colossus.iso

3. Depois compile o alvo Android:

       cd PS2Recomp/android
       gradle wrapper --gradle-version 8.9
       ./gradlew assembleDebug

O APK esperado fica em upstream/celosia/PS2Recomp/android/app/build/outputs/apk/debug/app-debug.apk.

Requisitos: Android SDK, NDK 28.2.13676358, CMake 3.22.1 e JDK compatível com
Gradle 8.9. A primeira fase usa apenas arm64-v8a.

## Arquivos no aparelho

O caminho de boot padrão é
/storage/emulated/0/Android/data/dev.celosia.sotc/files/SCUS_974.72.
Copie para essa pasta os arquivos extraídos da sua cópia do jogo. Não publique
dados proprietários do jogo.

O env.txt pode ficar ao lado do ELF inicial. São aceitas variáveis PS2X_*,
SOTC_* e CELOSIA_*. Os controles touch ficam ativos por padrão; use
CELOSIA_TOUCH_CONTROLS=0 para desligá-los.

## Limitações

- Esta branch não contém APK compilado/testado.
- Os controles touch ainda são um protótipo, sem editor de layout.
- FFmpeg fica desligado no Android nesta primeira fase, então vídeos MPEG não
  estão implementados.
- O renderer nativo OpenGL do upstream ainda não tem backend Android. Não
  estamos afirmando paridade com macOS/Vita.
- A auditoria de CI verifica a estrutura e os pontos de inserção, não substitui
  build com SDK/NDK nem teste em dispositivo.

Consulte PORT_STATUS.md para o estado exato.
