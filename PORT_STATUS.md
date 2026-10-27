# Estado do port Celosia Android

Data: 2026-10-27.

## Repositório e escopo
- Área de trabalho ativa: branch `celosia-android-port`.
- Upstream como submódulo: `HenryKun55/celosia`.
- Commit upstream fixado: `bb0bfd840a7454533c3018544fcf68f46de0a1ff`.
- O conteúdo anterior foi preservado em
  `backup/pre-celosia-cleanup-2026-10-27`.
- A branch `main` não foi substituída; permanece com o conteúdo anterior.
- Nenhum arquivo do jogo ou fonte C++ gerado pelo jogo é versionado.

## Integração Android
- Gradle / manifesto Android com build inicial para `arm64-v8a`.
- Helper de runtime Android para leitura allow-listed de `env.txt` e controles touch.
- Script de integração aplica o helper ao CMake do upstream e passa o caminho de
  boot ao módulo que lê o `env.txt`.
- CI de auditoria estática configurado para rodar também em
  `celosia-android-port`.
- Scripts de bootstrap e build remoto via Codespaces foram adicionados para
  oferecer um caminho de build a quem só tem celular.

## Geração dos fontes sem computador local
O pipeline upstream `./celosia setup --iso <cópia própria>` gera código em
`build/runner` e `build/vu1rec`. Não é um passo puramente Gradle: ele executa o
runtime e captura módulos enquanto o jogo corre. O novo script tenta fazê-lo
em Linux com Xvfb e renderização por software, depois compila o APK com Android
SDK/NDK. A compatibilidade da captura headless ainda precisa ser testada; se
falhar, os logs do pipeline vão indicar a etapa que exige adaptação.

## Não validado
- Build completa `assembleDebug`: ainda não executada neste ambiente.
- Compilação C++ para Android: ainda não executada.
- Execução headless do `celosia setup`: ainda não validada.
- Teste em emulador/aparelho: ainda não executado.
- Renderer nativo Android: não implementado.

Não tratar este estado como APK funcional antes de uma build concluída e de um
teste em dispositivo.
