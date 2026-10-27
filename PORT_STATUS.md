# Estado do port Celosia Android

Data: 2026-10-27.

## Repositório
- Upstream como submódulo: HenryKun55/celosia.
- Commit upstream fixado: bb0bfd840a7454533c3018544fcf68f46de0a1ff.
- O conteúdo anterior foi preservado em branch
  backup/pre-celosia-cleanup-2026-10-27.
- main foi reorganizada para ser a área de trabalho do port Android.

## Descobertas verificadas nos fontes
- PS2Recomp/CMakeLists.txt detecta Android e desativa recompiler, analyzer,
  testes e studio no build nativo Android.
- ps2xRuntime constrói ps2EntryRunner como biblioteca compartilhada Android.
- src/lib/ps2_android_runtime.cpp estava apenas como TODO.
- src/main.cpp já redireciona stdout/stderr para logcat no Android.
- ./celosia setup gera C++ em build/runner e build/vu1rec; o código derivado
  não é versionado e depende de uma cópia legítima do jogo.
- raylib 5.5 define ES 2.0 por padrão no Android; a configuração aqui seleciona
  explicitamente ES 3.0.
- native_renderer_gl.cpp ainda só ativa backend GL para macOS e Vita/vitaGL.

## Incluído nesta branch
- Submódulo apontando para upstream em commit fixo.
- Gradle/CMake Android com os diretórios dos fontes gerados.
- Leitura de env.txt com allow-list.
- Controles touch básicos com analógico e botões.
- Script de integração idempotente e workflow de auditoria estática.

## Não validado
- assembleDebug: não executado neste ambiente; SDK/NDK não configurados.
- Compilação C++ para Android: não executada.
- Teste em emulador/aparelho: não executado.
- Renderer nativo Android: não implementado.

Não tratar este estado como APK funcional antes de build e teste em dispositivo.
