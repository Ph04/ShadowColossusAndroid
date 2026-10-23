# Estado do porte — experiência Celosia/USA

**Base experimental:** Celosia (`ed1025701cfa943d7c1649d88e5175a51ff33d5e`) + Shadow of the Colossus USA (`SCUS-97472`, boot ELF `SCUS_974.72`).

**Situação atual:** o pipeline Android está sendo migrado. O build de CI sem ISO do usuário é apenas diagnóstico; não contém as fontes de jogo geradas. Não marque como jogável até testar uma compilação personalizada em aparelho real.

## Implementado nesta branch

- [x] Launcher Android/Kotlin, importação para armazenamento privado e overlay touch existentes preservados.
- [x] Clone local do Celosia em revisão fixa, validado por `scripts/ensure_celosia.py`; mudanças Android de build são aplicadas a um espelho descartável em `build/celosia-source`, não ao checkout upstream.
- [x] `scripts/patch_celosia_android_input.py` para integrar o overlay touch ao polling de controle Celosia, com os bits de botão PS2 em active-low.
- [x] `scripts/prepare_celosia_android.py` para executar o pipeline Celosia com a ISO própria do usuário, mantendo dumps, módulos extraídos e C++ gerado localmente.
- [x] Leitor/importador ISO-9660 que verifica `SYSTEM.CNF`, `SCUS_974.72`, `KERNEL.XFF`, `MANAGER.XFF` e `GAMECORE.XFF` antes de publicar os arquivos.
- [x] Ponte JNI ajustada para o layout privado `files/game/disc/SCUS_974.72`, logcat, fallback de GS por software e pasta privada de memory cards.
- [x] CI atualizada para testar helpers e tentar compilar APKs ARM64 a partir do runtime Celosia.

## Bloqueios ainda não resolvidos

1. **CI/compilação NDK:** a integração Celosia deve compilar em CI; acompanhar os resultados do PR experimental antes de confiar no APK.
2. **Boot real com código USA:** o fluxo de preparação precisa ser executado com uma ISO compatível do usuário, gerando as funções da edição americana e ligando as fontes ao runner Android.
3. **Renderização Android:** Celosia tem renderer nativo Metal no macOS e OpenGL ES no Vita; isso não constitui por si só um backend nativo Android. A configuração Android começa com o GS em software, como modo de compatibilidade; desempenho e imagem ainda não foram confirmados no aparelho.
4. **Áudio, controles e estabilidade:** o overlay está ligado em código, mas precisa ser exercitado depois do boot real. O áudio, menus, carregamento de área, saves e gameplay precisam de validação.
5. **Licença:** Celosia declara GPL-3.0. Preservar avisos e cumprir as condições aplicáveis antes de distribuir binários derivados.

## Build local

Consulte [docs/ANDROID_BUILD_FROM_ISO.md](docs/ANDROID_BUILD_FROM_ISO.md). Em termos gerais:

- `python3 scripts/prepare_celosia_android.py --check-source-tree` valida o checkout e os arquivos de fonte sem exigir ISO.
- `python3 scripts/prepare_celosia_android.py --iso /caminho/da/sua/ISO-USA.iso --codegen-only --jobs 1` gera/revisa localmente os artefatos do Celosia, sem montar APK.
- O comando completo sem `--codegen-only` segue para Gradle/NDK e gera o APK local.

## Política de dados

Não versionar nem enviar ao GitHub a ISO, o diretório extraído `build/celosia-disc`, capturas de RAM/módulos, saves, nem o código C++ gerado em `build/celosia`. A ISO temporária importada no Android é removida após extração verificada; o app mantém os arquivos extraídos privados e a pasta de saves.
