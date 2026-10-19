# Estado do porte

**Marco atual:** launcher Android e processo inicial de integração configurados; jogo ainda não jogável.

## Já implementado neste repositório

- [x] Aplicativo Android Kotlin/Gradle com alvo `arm64-v8a`.
- [x] Biblioteca C++ JNI mínima e diagnóstico exibido na tela.
- [x] Seletor de arquivos Android para importar a imagem do jogo.
- [x] Cópia para armazenamento privado com progresso e verificação de tamanho + SHA-256.
- [x] Submódulo `upstream/sotc-vibe-pc` fixado no commit da tag `v0.1.0-preview.1`, incluindo submódulos recursivos no CI.
- [x] GitHub Actions que prepara o SDK/NDK, compila o APK e publica o artefato.
- [x] Auditoria automática do código upstream publicada separadamente como `Android-port-audit`.
- [x] ISO, assets, arquivos gerados do jogo e saves excluídos do Git.

## Bloqueios antes de gameplay

1. O upstream não inclui `Port/generated/`; seus arquivos C++ são produzidos de uma ISO suportada.
2. As ferramentas de geração atualmente assumem executáveis `.exe` e scripts `.bat`; é preciso tornar a geração executável no host Linux do CI.
3. O runtime e o renderer ainda têm chamadas/flags de Windows/MSVC e caminhos x86 SIMD a adaptar para ARM64.
4. O renderer GS depende de caminhos gráficos específicos e precisa de uma implementação Android correta; o launcher não deve declarar sucesso de jogo só por desenhar um frame.
5. Ainda faltam a plataforma de áudio/entrada Android e os controles de toque integrados ao runtime.

## Verificação

- Workflow: [Android APK](https://github.com/Ph04/ShadowColossusAndroid/actions/workflows/android-apk.yml)
- Relatório da CI: artefato `Android-port-audit`
- Critério de sucesso atual: APK de diagnóstico compila. Isso não significa que o motor do jogo funciona.
