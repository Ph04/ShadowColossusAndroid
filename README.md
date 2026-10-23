# ShadowColossusAndroid — Celosia USA / Android ARM64

Port experimental de **Shadow of the Colossus USA (SCUS-97472)** para Android, usando como base o recompiler e runtime [Celosia](https://github.com/HenryKun55/celosia) / PS2Recomp.

> **Estado real:** migração de arquitetura em andamento. A base experimental já usa o layout do disco americano, inicializa a integração CMake em torno da revisão Celosia fixada e mantém a entrada touch existente conectada ao polling de controle PS2. O renderer Android e o boot do jogo recompilado ainda precisam de validação. O APK de CI é somente diagnóstico, pois não inclui o código gerado a partir da ISO de um usuário.

## Base fixada

- Celosia: commit `ed1025701cfa943d7c1649d88e5175a51ff33d5e`.
- Edição esperada: USA `SCUS-97472`; ELF de boot `SCUS_974.72`.
- Módulos esperados no diretório raiz extraído: `KERNEL.XFF`, `MANAGER.XFF`, `GAMECORE.XFF`, além de `SYSTEM.CNF`.
- Renderer de compatibilidade inicial: GS em software. O renderer nativo Celosia (Metal/macOS e OpenGL ES/vitaGL no PS Vita) não é um backend Android pronto.

A base PAL original permanece na branch principal; esta migração vive em `experiment/celosia-android-us`.

## APK de CI

Abra [GitHub Actions](https://github.com/Ph04/ShadowColossusAndroid/actions) ou o [PR experimental #3](https://github.com/Ph04/ShadowColossusAndroid/pull/3). Os artefatos de CI se chamam:

- `ShadowColossusAndroid-celosia-usa-debug-scaffold`
- `ShadowColossusAndroid-celosia-usa-release-scaffold`
- `ShadowColossusAndroid-celosia-usa-diagnostic-scaffold`

**Esses APKs são builds de diagnóstico, não jogos jogáveis.** Eles não contêm fontes C++ geradas a partir de uma ISO pessoal. Só uma compilação específica que inclua os módulos recompilados poderá testar o boot real.

## Importar a ISO americana

Na tela inicial, escolha uma imagem obtida da sua própria cópia da edição USA. O importador lê a estrutura ISO-9660, procura `SYSTEM.CNF` e verifica se o boot aponta para `SCUS_974.72`. Ele exige que o boot ELF e os três módulos XFF existam e sejam não vazios, e extrai os arquivos para `files/game/disc/` dentro do armazenamento privado do app.

Não há tamanho ou SHA-256 oficial da ISO americana fixado aqui: o SHA-256 da imagem de origem é registrado apenas como valor de diagnóstico e **não é tratado como prova de autenticidade**. Essa validação estrutural não substitui a conferência do ELF/módulos pelo pipeline de geração Celosia.

Depois de uma importação bem-sucedida, o arquivo temporário completo da ISO é removido para poupar espaço; ficam os arquivos extraídos e os dados privados do jogo/saves. A importação precisa de bastante espaço livre, pois mantém temporariamente a imagem e a cópia extraída ao mesmo tempo.

## Gerar código USA e compilar localmente

### Requisitos

Git, Python 3, CMake, Ninja, compilador C/C++, `bsdtar`, `pkg-config` e Bash para a etapa host do Celosia. Para compilar o APK, instale JDK 17, Android SDK API 35, NDK `28.2.13676358`, CMake Android `3.22.1` e Gradle 8.9. No Windows, use WSL2 com a toolchain Linux.

### Validar o checkout

`python3 scripts/prepare_celosia_android.py --check-source-tree`

O helper baixa a revisão Celosia fixada para `upstream/celosia/`. Durante o build Android, cria um espelho descartável em `build/celosia-source/` para aplicar o patch de entrada touch; evita modificar a cópia upstream fixada.

### Gerar o código a partir da sua ISO

`python3 scripts/prepare_celosia_android.py --iso "/caminho/para/SOTC-USA.iso" --codegen-only --jobs 1`

Esse comando executa o pipeline do Celosia para extrair o disco, capturar os módulos que o jogo carrega dinamicamente e recompilar funções e microprogramas VU1. ISO, dumps de RAM, módulos capturados e C++ gerado permanecem no computador.

### Compilar o APK

`python3 scripts/prepare_celosia_android.py --iso "/caminho/para/SOTC-USA.iso" --jobs 1 --build-type release`

Para debug, use `--build-type debug`. Para relatório detalhado, `--build-type diagnostic`. O APK local de teste usa a assinatura debug padrão; isso não é uma chave de publicação Play Store.

Se o código já estiver gerado e validado em `build/celosia/`, `--skip-setup` permite reaproveitá-lo. Ele não completa nem corrige uma geração incompleta.

Mais detalhes: [docs/ANDROID_BUILD_FROM_ISO.md](docs/ANDROID_BUILD_FROM_ISO.md), [docs/CELOSIA_ANDROID_MIGRATION.md](docs/CELOSIA_ANDROID_MIGRATION.md) e [PORT_STATUS.md](PORT_STATUS.md).

## Renderer, entrada e bloqueios

O polling do controle Celosia é adaptado para ler o overlay touch Android: botões PlayStation em active-low e analógicos esquerdo/direito. O ciclo de vida do `GameActivity`, áudio, resposta real dos controles, menus, carregamento de áreas, saves e gameplay ainda requerem testes com a engine USA recompilada.

O Celosia documenta seu renderer nativo para Metal no macOS e OpenGL ES/vitaGL no PS Vita. A simples seleção `PLATFORM=Android` no raylib não implementa o renderer nativo de Android. A build começa com o GS em software como caminho de compatibilidade; não se deve prometer desempenho aceitável no Android até existir um teste real. O trabalho posterior precisa adaptar contextos/shaders para um backend Android (GLES/Vulkan) ou validar um caminho completo de GS por software.

## Dados e licença

Não envie ao GitHub ISO, BIOS, assets proprietários, dumps de RAM, capturas XFF, memory cards/saves ou código C++ gerado a partir da ISO. Celosia declara GPL-3.0: preserve os avisos aplicáveis e verifique as obrigações da licença antes de redistribuir código ou APK derivado.
