# Migração experimental para Celosia + edição USA

Status: decisão de arquitetura tomada; implementação Android ainda não concluída.

## Decisão

A experiência principal passa a usar a base Celosia e a edição americana de Shadow of the Colossus identificada pelo projeto como `SCUS_974.72` (serial `SCUS-97472`). A migração acontece somente nesta branch experimental. A base PAL e o seu fluxo de geração continuam preservados na branch original, sem troca ou remoção de arquivos no `main`.

Referência de base: https://github.com/HenryKun55/celosia

## Por que não basta trocar o arquivo ISO

A implementação PAL existente contém suposições da edição europeia em vários pontos, não apenas no seletor de ISO:

- `app/src/main/java/dev/mobile/sotcandroid/IsoIntegrity.kt` valida tamanho e SHA-256 exclusivos de `SCES-53326 v1.00`.
- `scripts/prepare_sotc_android.py` espera o layout `upstream/sotc-vibe-pc/Port/`, o perfil `SCES-53326_v1.00.json`, o linker e os verificadores específicos dessa base.
- `app/src/main/cpp/CMakeLists.txt` liga `Port/generated/` e compila a camada host específica do port PC.
- `native_bridge.cpp` também espera o nome `SCES-53326.iso` e força o rasterizador GS por software.

Esses elementos não podem ser reaproveitados como se o executável e os módulos fossem intercambiáveis. O tamanho e o SHA-256 da imagem americana ainda não foram estabelecidos nesta auditoria; não aplicar a ela o hash PAL.

## O que a inspeção do Celosia confirmou

- O pipeline de geração é o do próprio Celosia/PS2Recomp, com boot ELF e módulos `KERNEL.XFF`, `MANAGER.XFF` e `GAMECORE.XFF`.
- O perfil de jogo Celosia é específico para `SCUS_974.72` (`game/scus.toml`).
- A documentação do Celosia descreve renderer Metal no macOS e OpenGL ES via vitaGL no PS Vita. O simples `PLATFORM=Android` no CMake não implementa um backend Android.
- O código `PS2Recomp/ps2xRuntime/src/lib/native/native_renderer.cpp` seleciona backend por macros de plataforma; atualmente não seleciona Android. O arquivo `ps2_android_runtime.cpp` é somente um TODO para controles touch, não um runtime Android completo.
- A interface Android existente (importação de ISO, logcat/diagnóstico e overlay touch) é reaproveitável, mas a ponte JNI, o caminho de dados e o entry point precisam ser adaptados à organização Celosia.

## Sequência de implementação

### Marco 1 — Base e pipeline do jogo

1. Manter esta branch separada e adicionar Celosia como dependência-base experimental, sem sobrescrever `upstream/sotc-vibe-pc` na branch PAL.
2. Portar o orquestrador de preparação para o pipeline real do Celosia. Ele deve validar que a cópia da edição USA contém o boot ELF e módulos esperados, extrair/capturar/linkar os módulos e produzir o código recompilado localmente.
3. Atualizar a seleção/cópia da imagem no app para não aceitar a identificação PAL. A validação deve usar a estrutura de módulos do Celosia; até ser implementada e testada, não declarar compatibilidade apenas pelo nome do arquivo.
4. Definir os diretórios privados de dados, saves e logs que o runtime Celosia usará no Android.
5. Ligar as fontes geradas ao target NDK com um entry point específico de Celosia; não misturar funções/hooks da base PAL com hooks da versão USA.

**Critério de saída:** o pipeline da edição USA gera e liga todos os módulos sem incluir ISO, dumps, assets ou C++ gerado no Git.

### Marco 2 — Boot Android

1. Adaptar inicialização, caminhos de arquivos, threads e saída de logs de Celosia para o ciclo de vida da app Android.
2. Compilar a camada de runtime PS2Recomp para `arm64-v8a` usando o NDK.
3. Confirmar um boot demonstrável antes de começar otimizações. Uma compilação do scaffold não conta como boot.

**Critério de saída:** o código recompilado da edição USA inicia no dispositivo, com log de boot verificável.

### Marco 3 — Renderer e entrada

1. Implementar uma escolha explícita de backend Android em vez de deixar o renderer nativo retornar `nullptr`.
2. Avaliar primeiro um contexto EGL/ANativeWindow e shaders GLES compatíveis, com um teste de desenho mínimo; avaliar Vulkan somente se o desenho/recursos do renderer justificarem a mudança.
3. Adaptar shaders e recursos de GPU do renderer Celosia; a renderização PS2 por software só poderá ser usada como ferramenta diagnóstica se existir um caminho completo e utilizável nesta base.
4. Integrar o overlay touch existente ao polling do controle PS2, verificando analógicos, botões, multitouch e pausa/retorno ao app.
5. Confirmar áudio, menus, save, carregamento de área e gameplay.

**Critério de saída:** menus e gameplay visíveis, comandos e áudio funcionando, sem declarar sucesso com base apenas em build.

## Regras para esta experiência

- Não modificar `main` nem substituir a branch PAL.
- Não atribuir tamanho ou hash à ISO americana sem fonte verificável e teste local do formato esperado.
- Não enviar ISO, dados extraídos, dumps de RAM, módulos capturados, assets nem código C++ gerado ao GitHub.
- Revisar a licença GPL-3.0 do Celosia antes de copiar ou derivar código e manter avisos/licenças exigidos.
- Não prometer APK jogável até concluir o teste de instalação/boot/renderização em Android real.
- Cada mudança deve ser pequena e ligada a um critério de validação; priorizar o pipeline e o boot antes de otimizações.
