# Estado do porte

**Marco atual:** integração do runtime Android de PS2Recomp em CI; ainda não jogável.

## Implementado no repositório

- [x] Aplicativo Android Kotlin/Gradle com alvo `arm64-v8a`.
- [x] Biblioteca JNI e diagnóstico no launcher.
- [x] Seleção Android e importação da ISO para armazenamento privado, com barra de progresso e verificação de tamanho + SHA-256.
- [x] Código upstream de PC fixado como submódulo no commit `v0.1.0-preview.1`, com submódulos recursivos.
- [x] Auditoria estática automática dos bloqueios de porte, publicada como artefato da CI.
- [x] CMake Android configurado para compilar `ps2_runtime` e o runner `ps2EntryRunner` do PS2Recomp, usando o caminho ARM64/Android upstream.
- [x] CI configurada para construir o runtime ARM64 e o APK de diagnóstico; resultado do último commit disponível em [Actions](https://github.com/Ph04/ShadowColossusAndroid/actions).

## O que falta para rodar Shadow of the Colossus

1. O upstream não distribui `Port/generated/`: o código C++ recompilado é produzido a partir da ISO suportada. Ele não está no APK nem no repositório.
2. A geração do código ainda depende de caminhos Windows (`.exe` e `.bat`); criar e testar um fluxo de geração para Linux/Clang.
3. Integrar as fontes específicas do jogo ao target nativo Android. O runner genérico compilado é somente infraestrutura, não executa o port de Shadow of the Colossus.
4. Adaptar e validar o renderizador GS e seus shaders para os recursos gráficos disponíveis no Android; compilar o runtime não comprova que o renderer seja compatível.
5. Integrar a ISO importada à engine, controles de toque, áudio, salvamentos e logs, depois validar boot e gameplay real.

## Como baixar builds e relatórios

- Abra **Actions** e selecione a última execução.
- Baixe `ShadowColossusAndroid-debug-apk` para o APK de diagnóstico.
- Baixe `Android-port-audit` para a auditoria do código upstream fixado.

O APK de diagnóstico não é um jogo jogável até que o código recompilado, os recursos e o renderer do título sejam integrados.
