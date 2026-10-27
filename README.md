# Celosia Android

Port Android em desenvolvimento para Celosia / Shadow of the Colossus.
Esta branch mantém a integração Android separada do código original e fixa
HenryKun55/celosia como submódulo, no commit `bb0bfd840a7454533c3018544fcf68f46de0a1ff`.

## Estado atual

- Área de trabalho: branch `celosia-android-port`.
- O experimento Android anterior foi preservado em
  `backup/pre-celosia-cleanup-2026-10-27`.
- `main` continua preservada no estado anterior; não foi substituída.
- A integração Android e o ambiente remoto de build estão preparados, mas ainda
  não existe APK compilado ou validado.
- O renderer nativo OpenGL do Celosia não possui backend Android nesta revisão.
  A meta inicial é compilar, iniciar o runtime e testar o caminho gráfico
  disponível antes de avaliar o renderer.

## Build usando somente o celular

A geração dos fontes do Celosia precisa executar o programa do jogo durante
capturas de módulos. Como o fluxo original espera uma janela gráfica, o script
para Linux usa Xvfb (display virtual) e renderização por software. Isso ainda
precisa ser confirmado numa execução real.

1. Abra o repositório na branch `celosia-android-port` e crie um Codespace:
   [GitHub Codespaces](https://github.com/codespaces). A disponibilidade e a
   cota dependem da conta.
2. Quando o ambiente abrir, aguarde o script de preparação instalar as
   ferramentas de Linux, Android SDK/NDK, CMake e Gradle.
3. Envie a sua própria ISO para a área de arquivos do Codespace. **Não** a envie
   para o repositório nem a adicione a um commit.
4. No terminal do Codespace, execute, ajustando o nome/caminho do arquivo:

       ./scripts/build_android_apk.sh --iso "/workspaces/ShadowColossusAndroid/Shadow-of-the-Colossus.iso"

   A geração pode demorar e pode falhar se a captura gráfica headless não
   funcionar nesse ambiente. O script para ao encontrar erros e não anuncia
   sucesso sem confirmar que o APK existe.
5. Se os fontes gerados já estiverem em `upstream/celosia/build`, é possível
   tentar somente a compilação Android com:

       ./scripts/build_android_apk.sh --skip-setup

6. O APK esperado será:
   `upstream/celosia/PS2Recomp/android/app/build/outputs/apk/debug/app-debug.apk`.
   Baixe-o pelo explorador de arquivos do Codespace.

O script ignora as pastas de build e arquivos de jogo pelo `.gitignore`.
Verifique sempre que a ISO e os dados extraídos não aparecem entre as mudanças
do Git antes de publicar qualquer commit.

## Preparação manual / depuração

O submódulo é inicializado com:

    git submodule update --init --recursive

A aplicação da integração Android é idempotente:

    python3 scripts/apply_android_overlay.py
    python3 scripts/apply_android_overlay.py --check-only

O pipeline original de geração dos fontes é `./celosia setup --iso <ISO>`.
Ele cria, entre outros, `build/runner` e `build/vu1rec`; não incluímos esses
artefatos derivados nem arquivos proprietários do jogo neste repositório.

## Configuração Android

- Android SDK Platform 35
- Android NDK `28.2.13676358`
- CMake `3.22.1`
- Gradle `8.9`
- ABI inicial: `arm64-v8a`
- ID do aplicativo: `dev.celosia.sotc`

## Arquivos no aparelho

O caminho configurado para o ELF de inicialização é:

`/storage/emulated/0/Android/data/dev.celosia.sotc/files/SCUS_974.72`

Copie para a pasta de arquivos do aplicativo os dados extraídos da sua própria
cópia do jogo. Não publique esses dados.

O `env.txt` pode ficar ao lado do ELF inicial. São aceitas variáveis
`PS2X_*`, `SOTC_*` e `CELOSIA_*`. Os controles touch estão ativos por padrão;
`CELOSIA_TOUCH_CONTROLS=0` os desativa.

## Limitações atuais

- A auditoria estática do CI verifica a estrutura e os pontos de integração; não
  substitui a compilação NDK nem um teste em aparelho.
- A primeira build completa via Codespaces ainda não foi executada/confirmada.
- Os controles touch são um protótipo, sem editor de layout.
- FFmpeg fica desativado no Android nesta primeira fase; vídeos MPEG ainda não
  estão implementados.
- O renderer nativo do upstream não tem backend Android nesta revisão.
