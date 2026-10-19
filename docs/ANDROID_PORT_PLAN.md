# Plano técnico — Android ARM64

## Referências

- Port PC: https://github.com/LightVelox/sotc-vibe-pc/tree/v0.1.0-preview.1
- Port Android de referência: https://github.com/dfdx047/Tenkaichi3DecompAndroid
- Android NDK: https://developer.android.com/ndk

O Dragon Rage demonstra uma estratégia prática: aplicação Android separada, engine nativa compilada como biblioteca ARM64, SDL3, integração de entrada por ponte nativa, configurações da GPU e GitHub Actions para gerar APKs. Devemos reaproveitar a arquitetura como referência, não seu motor de jogo ou renderizador específico.

## Fases

### Fase A — build reproduzível

- [x] Projeto Gradle mínimo.
- [x] CI no GitHub Actions que compila APK `arm64-v8a` em cada push/PR e sob demanda.
- [x] Biblioteca JNI nativa mínima para provar o fluxo Kotlin → código nativo.
- [x] Integrar no CMake do APK o runtime Android ARM64 de PS2Recomp e o runner NativeActivity upstream.
- [ ] Fixar versões exatas de cada dependência upstream e armazenar hashes.
- [ ] Adicionar cache seguro para ferramenta/dependências sem armazenar dados do jogo.

### Fase B — código recompilado e runtime

- [ ] Revisar licença e dependências de PS2Recomp.
- [x] Criar preset CMake Android/Clang usando o caminho ARM64/sse2neon que existe no PS2Recomp fixado.
- [ ] Mapear as dependências de plataforma e as intrínsecas SSE/AVX2; implementar NEON ou caminhos portáveis preservando a semântica necessária.
- [x] Encaminhar stdout/stderr do entry point de jogo para Android logcat por patch local do checkout.
- [ ] Validar relógios, threading, sinais e prioridades no aparelho; o caminho de arquivos privados foi conectado, mas o boot real segue pendente.
- [x] Criar `scripts/prepare_sotc_android.py`: configuração CMake host, geração local do código EE/VU1, verificação de relocations e build Gradle. A execução com uma ISO real ainda precisa ser validada no ambiente do usuário.

### Fase C — renderização e áudio

- [ ] Inventariar o uso de OpenGL 4.6, `GL_ARB_fragment_shader_interlock`, compute shaders, sincronização e shaders existentes.
- [ ] Prototipar um contexto Vulkan Android e desenhar um plano de tradução das operações GS para recursos portáveis.
- [x] Selecionar GS por software em Android como fallback para evitar o backend desktop OpenGL 4.6/fragment-interlock. Ainda precisa de teste de imagem/desempenho.
- [ ] Implementar um caminho GS acelerado para GLES/Vulkan com verificações de capacidades; não presumir compatibilidade por nome de API.
- [ ] Integrar áudio e validar precisão de ordenação/temporização.
- [ ] Testar em GPU Mali primeiro no aparelho-alvo, depois ampliar a matriz de dispositivos.

### Fase D — aplicação jogável

- [x] Seletor Android e importação privada da ISO com validação do tamanho esperado; hashes de ELF/módulos são conferidos pelo runtime gerado.
- [x] Encaminhar a ISO absoluta à NativeActivity e trocar o diretório de trabalho para armazenamento privado do app. Boot real ainda pendente de compilação das fontes geradas e teste no dispositivo.
- [ ] Armazenamento privado de saves/cache/logs, com importação/exportação controladas.
- [x] Overlay touch-screen com dois analógicos, botões PS2, múltiplos toques e botão para mostrar/ocultar, integrado ao polling de controle via biblioteca compartilhada.
- [ ] Validar o overlay em gameplay real, ajustar o mapeamento e testar controles físicos.
- [ ] Resolução interna, limite de FPS, limite de memória/cache e qualidade dos efeitos.
- [ ] Testes por marcos: boot, título, introdução, exploração, Agro e batalhas de colossos.

## Critério para chamar de APK jogável

Não considerar a integração concluída apenas porque o APK instala, a biblioteca JNI carrega ou uma cena de teste de Vulkan aparece. O APK só deve ser marcado como jogável após demonstrar que a engine real abre os recursos do jogo, apresenta gameplay e aceita entrada no aparelho-alvo.
