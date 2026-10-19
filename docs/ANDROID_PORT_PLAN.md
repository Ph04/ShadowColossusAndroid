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
- [ ] Fixar versões exatas de cada dependência upstream e armazenar hashes.
- [ ] Adicionar cache seguro para ferramenta/dependências sem armazenar dados do jogo.

### Fase B — código recompilado e runtime

- [ ] Revisar licença e dependências de PS2Recomp.
- [ ] Criar presets CMake Android/Clang específicos para `arm64-v8a`.
- [ ] Mapear as dependências de plataforma e as intrínsecas SSE/AVX2; implementar NEON ou caminhos portáveis preservando a semântica necessária.
- [ ] Adaptar relógios, threading, sistema de arquivos, sinais, logs e prioridade das threads.
- [ ] Definir um build host/tooling para gerar o código recompilado e, depois, compilar a biblioteca Android. Os geradores de Windows não devem ser assumidos portáveis.

### Fase C — renderização e áudio

- [ ] Inventariar o uso de OpenGL 4.6, `GL_ARB_fragment_shader_interlock`, compute shaders, sincronização e shaders existentes.
- [ ] Prototipar um contexto Vulkan Android e desenhar um plano de tradução das operações GS para recursos portáveis.
- [ ] Manter um caminho de fallback apenas quando houver implementação correta; não selecionar OpenGL ES por nome sem verificar extensões e comportamento.
- [ ] Integrar áudio e validar precisão de ordenação/temporização.
- [ ] Testar em GPU Mali primeiro no aparelho-alvo, depois ampliar a matriz de dispositivos.

### Fase D — aplicação jogável

- [x] Seletor Android e importação privada da ISO com validação de tamanho e SHA-256.\n- [ ] Conectar o arquivo importado à engine real e validar recursos na hora do boot.
- [ ] Armazenamento privado de saves/cache/logs, com importação/exportação controladas.
- [ ] Controles de toque configuráveis e compatibilidade com gamepad.
- [ ] Resolução interna, limite de FPS, limite de memória/cache e qualidade dos efeitos.
- [ ] Testes por marcos: boot, título, introdução, exploração, Agro e batalhas de colossos.

## Critério para chamar de APK jogável

Não considerar a integração concluída apenas porque o APK instala, a biblioteca JNI carrega ou uma cena de teste de Vulkan aparece. O APK só deve ser marcado como jogável após demonstrar que a engine real abre os recursos do jogo, apresenta gameplay e aceita entrada no aparelho-alvo.
