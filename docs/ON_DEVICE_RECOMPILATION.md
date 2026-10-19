# Modelo de motor pré-compilado (arquitetura tipo Budokai)

## Decisão

O ShadowColossusAndroid seguirá o modelo de execução do Tenkaichi3DecompAndroid: **o motor ARM64 deve ser compilado antes de instalar o APK**. O telefone não compilará milhares de arquivos C++ durante a importação ou primeira inicialização. No aparelho, o launcher copia a ISO para o armazenamento privado e o motor nativo a lê em tempo de execução.

A semelhança é arquitetural, não uma igualdade da cadeia de desenvolvimento. O Tenkaichi3DecompAndroid tem o código decompilado do jogo presente nos fontes. Neste projeto, os fontes específicos ainda são gerados pelo PS2Recomp a partir da versão exata da ISO.

## Cadeia de build deste projeto

1. Compilar as ferramentas host necessárias do PS2Recomp.
2. Validar a ISO PAL SCES-53326 v1.00 por hashes dos módulos/ELF no linker upstream.
3. Gerar os arquivos C++ específicos em `Port/generated/`.
4. Verificar a cobertura das relocations e os cabeçalhos gerados.
5. Compilar esses arquivos para ARM64 com Android NDK/CMake e ligá-los ao runner.
6. Instalar no telefone o APK já construído; na execução, o aplicativo importa a ISO e chama a engine nativa.

O fluxo local já é iniciado por `scripts/prepare_sotc_android.py`; a build da CI sem ISO não passa pelas etapas 2–5 e é apenas um scaffold de teste. A presença do runtime PS2Recomp, sozinha, não torna o APK jogável.

## Comando para construir um APK com a engine incluída

Com submódulos recursivos, Python 3.10+, JDK 17, CMake, compilador host C/C++, Android SDK/NDK e Gradle configurados:

```sh
python scripts/prepare_sotc_android.py --iso "CAMINHO/SCES-53326.iso" --jobs 1 --build-type diagnostic --skip-vu1
```

O `--skip-vu1` pula somente a geração estática opcional da VU1 e pode deixar o jogo mais lento. Remova-o para a compilação completa. Para a versão final, use `--build-type release`. O APK diagnóstico aparece em `app/build/outputs/apk/diagnostic/app-diagnostic.apk`.

As fontes geradas e a ISO continuam no computador de build e não devem ser adicionadas ao repositório. Depois de instalar o APK personalizado, importe a ISO correspondente no launcher. A versão do jogo ainda precisa ser testada no aparelho; gerar um APK com sucesso não comprova boot, renderização ou desempenho.

## Estado da engine

A UI distingue duas coisas independentes:
- **Runtime PS2Recomp presente**: bibliotecas gerais e runner compilados.
- **Engine específica do jogo presente**: fontes C++ geradas ligadas ao APK.

Quando a engine específica está ausente, o botão Jogar permanece bloqueado. Não há uma barra de “recompilação no celular”, porque a geração e compilação acontecem antes de empacotar o aplicativo. A importação verifica o tamanho e o SHA-256 completo da ISO, e o runtime mantém os checks independentes de boot ELF e módulos.

## Por que a CI não entrega o jogo pronto

Os runners públicos não têm acesso a uma ISO de jogo fornecida pelo usuário e não devem receber nem publicar arquivos derivados da imagem. A CI verifica se o runtime, o launcher e as bibliotecas compilam. Um APK jogável precisa ser gerado no ambiente de build após a geração local dos fontes específicos e ainda exige testes reais de execução.
