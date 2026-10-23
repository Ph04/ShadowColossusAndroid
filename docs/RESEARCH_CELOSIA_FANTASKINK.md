# Auditoria comparativa: Celosia, Fantaskink/SOTC e Android

Data da auditoria: 2026-10-23.

## Conclusão executiva

O Celosia é a referência prioritária para o runtime PS2Recomp e para a lógica dos renderizadores, porque é uma recompilação funcional de Shadow of the Colossus no macOS e possui um alvo PS Vita. Porém, **não é um renderer Android pronto para copiar**. O alvo Android que aparece em seu CMake seleciona a plataforma Android do raylib; os backends do renderer nativo e do GS acelerado continuam condicionados a macOS/PS Vita.

O projeto Fantaskink/SOTC é útil para engenharia reversa e estrutura de módulos, mas tem como alvo outra versão do jogo (Preview, `SCPS_150.97`). Não se devem copiar endereços, mapas de funções ou configurações de módulos dele para a edição PAL suportada pelo Android.

A ordem segura continua sendo: validar a geração e a ligação da engine específica PAL, confirmar um primeiro boot usando o caminho gráfico de compatibilidade e só então desenvolver/portar uma implementação gráfica acelerada para Android.

## ISO compatível com a base Android

O launcher exige a imagem PAL europeia **Shadow of the Colossus, `SCES-53326 v1.00`**:

- Tamanho exato: `4,635,918,336` bytes.
- SHA-256 completo: `068925770645266189005f09c9a9a1fee25726bfc93c89b12f832f3673664ac4`.
- Validador do launcher: [IsoIntegrity.kt](../app/src/main/java/dev/mobile/sotcandroid/IsoIntegrity.kt).
- O script de geração também usa o perfil de módulos `Port/profile/SCES-53326_v1.00.json` e verifica o ELF e os módulos através do linker.

É necessário usar uma imagem obtida da própria cópia do usuário e correspondente exatamente a essa versão. Uma ISO de mesmo título, mas de outra região/revisão, não atende a esse perfil.

## Celosia

Referência inspecionada: [HenryKun55/celosia](https://github.com/HenryKun55/celosia), commit `ed1025701cfa943d7c1649d88e5175a51ff33d5e`.

A documentação e o código indicam que o Celosia:
- Recompila o código MIPS do ELF de boot e dos módulos XFF para C++ com PS2Recomp.
- Implementa partes do hardware PS2 no runtime anfitrião, incluindo DMA, IOP/SPU2 e temporização.
- Possui renderer nativo que lê diretamente estruturas de modelos do jogo.
- Tem um backend nativo Metal no macOS e um backend OpenGL para o macOS/PS Vita. A documentação reporta gameplay no macOS; no alvo Vita, o estado documentado ainda inclui limites de fidelidade/desempenho.

Arquivos de referência:
- [CMakeLists.txt do runtime](https://github.com/HenryKun55/celosia/blob/main/PS2Recomp/ps2xRuntime/CMakeLists.txt)
- [Fachada do renderer nativo](https://github.com/HenryKun55/celosia/blob/main/PS2Recomp/ps2xRuntime/src/lib/native/native_renderer.cpp)
- [Backend OpenGL do renderer nativo](https://github.com/HenryKun55/celosia/blob/main/PS2Recomp/ps2xRuntime/src/lib/native/native_renderer_gl.cpp)
- [Backend GS por GPU](https://github.com/HenryKun55/celosia/blob/main/PS2Recomp/ps2xRuntime/src/lib/gs/gs_hw_backend.cpp)

### O que não pode ser copiado como se fosse Android

Os pontos de seleção do renderer nativo usam `__APPLE__` e `__vita__`; o backend GS por GPU também só habilita as implementações de macOS e Vita. O caminho OpenGL do renderer usa GLSL 3.3 no macOS e shaders CG específicos do vitaGL na Vita. Portanto, apenas configurar `ANDROID` no CMake não fornece um backend GLES funcional.

Além disso, o renderer nativo do Celosia recebe informação de hooks de jogo específicos à edição americana `SCUS-97472`. Mesmo que o backend gráfico genérico seja útil, as estruturas/endereços de game hooks não devem ser reutilizados sem adaptação e validação para `SCES-53326`.

A licença declarada pelo Celosia é GPL-3.0. Antes de portar código, é obrigatório analisar a compatibilidade da licença e preservar os avisos aplicáveis.

### Linhas de investigação aproveitáveis

1. Comparar as alterações genéricas no PS2Recomp do Celosia contra a versão incorporada pelo upstream do Android, priorizando correções reproduzíveis de memória, DMA, temporização, VIF/VU1 e áudio.
2. Separar mudanças genéricas de runtime dos hooks específicos de `SCUS-97472`.
3. Não importar automaticamente otimizações do recompiler. Por exemplo, alterações que mudam o código gerado exigem validação de correção e custo de compilação na toolchain ARM64 antes de serem habilitadas.
4. Usar o backend GS por GPU como referência de comportamento e organização, não como implementação Android pronta.

## Fantaskink/SOTC

Referência: [Fantaskink/SOTC](https://github.com/Fantaskink/SOTC).

O README documenta o alvo Preview `SCPS_150.97` (SHA-1 do ELF publicado pelo projeto: `c4d5576d1cae3721c411a746c7845f5c6f026dbb`) e `KERNEL.XFF`, com ferramentas de compilação para Linux/WSL2. O repositório é um projeto de descompilação, não um port Android com runtime e renderer integrados.

Usos recomendados:
- Referência para metodologia de análise de ELF/XFF e organização de módulos.
- Consulta de engenharia reversa quando uma dúvida específica sobre o fluxo do jogo puder ser resolvida por esse código.

Não usar os binários, hashes de módulos, offsets, function maps ou perfis de geração da Preview como substitutos dos dados PAL. O projeto declara licença MIT; isso não altera a incompatibilidade técnica entre versões.

## Estado específico do ShadowColossusAndroid

A base atual tem o submódulo `upstream/sotc-vibe-pc` fixado e o helper `scripts/prepare_sotc_android.py`. Esse helper compila ferramentas host, valida a imagem, gera o código específico do jogo, verifica os artefatos necessários e então inicia o build Gradle. A CI sem imagem de usuário compila somente o scaffold/runtime de diagnóstico; não contém o código do jogo gerado.

O CMake Android integra `Port/generated/` somente quando a geração local está presente. O bridge mantém o GS por software como fallback, porque o caminho GPU da base upstream pressupõe recursos de OpenGL desktop/fragment-interlock que não podem ser presumidos em dispositivos Android.

## Próximas etapas recomendadas

1. Executar `python scripts/prepare_sotc_android.py --iso CAMINHO/SCES-53326.iso --jobs 1 --build-type diagnostic --skip-vu1` num computador com a toolchain configurada e uma cópia compatível da ISO. `--skip-vu1` é apenas uma primeira tentativa de diagnóstico; pode reduzir desempenho.
2. Não marcar o build como jogável só porque o Gradle terminou. Confirmar que as fontes geradas estão ligadas, que o runtime entra no ELF do jogo e recolher `sotc-boot.log` se o boot falhar.
3. Comparar correções genéricas do PS2Recomp do Celosia com a revisão pinada e portar mudanças pequenas, isoladas e testáveis, sem substituir em bloco o submódulo.
4. Depois de confirmar o boot, desenvolver uma implementação de renderer adequada a Android (GLES/Vulkan ou port do renderer nativo com shaders/contexto adequados) e testar no aparelho. Medir a renderização por software como linha de base, não como requisito de desempenho final.
5. Usar Fantaskink/SOTC apenas para investigar questões de engenharia reversa, sempre considerando a diferença de versão.

## Critério de sucesso

Um APK só será chamado de jogável depois de instalado e testado com a ISO PAL suportada e de confirmar boot visível, controles, imagem, áudio e estabilidade em aparelho Android real. Os artefatos padrão da CI não atendem a esse critério.

## Comparação de código já conferida

Uma comparação direcionada do runtime dá uma indicação útil sobre prioridades:

- **EE timer overflow/compare backlog:** não é uma correção para copiar sem verificar. A revisão de PS2Recomp documentada pelo port PC já contém estruturas e contabilidade de eventos pendentes em `ps2_memory.cpp`; a correção antiga do Celosia sobre estouros de timer não deve ser reaplicada cegamente. O ponto prático é confirmar que o código gerado usa essa implementação e exercitar a temporização no boot.
- **EE dispatch direto (`callGuest` / `jumpGuest`):** o Celosia tem melhorias posteriores no caminho rápido do runtime/recompilador que não aparecem na revisão de PS2Recomp consultada no port PC fixado. Isso pode ser uma otimização futura, mas altera o código gerado e as fronteiras do scheduler. Deve ficar desligada/fora do primeiro marco até haver boot real e testes diferenciais reproduzíveis; não é pré-requisito para validar o pipeline de build.
- **Renderer nativo do Celosia:** as abstrações de backend e a conversão de estruturas do jogo são referências úteis, mas o código está acoplado a macros de plataforma `__APPLE__`/`__vita__` e hooks da edição americana. Um backend Android exigirá contexto EGL/ANativeWindow, carregamento de funções GLES ou Vulkan e compilação/adaptação de shaders, além de verificar a semântica do desenho PS2. Copiar somente `native_renderer_gl.cpp` não produz um renderer Android.
- **Código Android dentro do Celosia:** o arquivo `ps2_android_runtime.cpp` do estado consultado ainda contém apenas um TODO para overlay de controles e um bloco Android vazio. Logo, não é uma implementação de integração Android pronta para reutilizar; os controles/JNI já presentes no nosso projeto continuam sendo a base mais útil.

Estas constatações reforçam a separação entre (1) gerar/ligar o código de jogo PAL, (2) demonstrar o primeiro boot com o caminho de compatibilidade e (3) desenvolver um backend gráfico acelerado. Nenhuma otimização de performance será considerada uma correção de boot sem um teste que a reproduza.

