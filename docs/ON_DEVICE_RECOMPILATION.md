# Recompilação no Android: referência e estado

## O que o Tenkaichi3DecompAndroid faz

No Tenkaichi3DecompAndroid, o código decompilado do jogo já faz parte dos arquivos-fonte do projeto. O script `port/tools/android.py` é uma ferramenta de desenvolvimento: transforma e compila esses fontes para a biblioteca ARM64 antes de o APK ser montado. O README descreve que, no aparelho, a aplicação lê a ISO do usuário, verifica a edição e copia os dados para a estrutura esperada pelo motor.

Portanto, esse projeto é uma referência útil para a arquitetura Android nativa, o tratamento privado da ISO e a interface de importação, mas não prova que uma ISO de PS2 pode ser recompilada inteira em tempo de execução pelo launcher.

## Por que o fluxo de Shadow of the Colossus é diferente

Neste repositório, a geração específica do jogo usa ferramentas do PS2Recomp e o linker do projeto upstream. O fluxo atual no computador é:

1. compilar as ferramentas host;
2. analisar a ISO e validar o boot ELF/módulos;
3. executar `ps2_recomp` para gerar fontes C++;
4. opcionalmente gerar código VU1;
5. verificar os pontos de relocação;
6. compilar e ligar as fontes geradas ao runner Android por meio do NDK/Gradle.

A compilação Gradle dentro do APK atual constrói o runtime e as bibliotecas do launcher; ela não dispõe do gerador nem de um compilador C/C++ para compilar milhões de bytes de fontes durante o uso.

## Progresso visual atual

A home contém a ação **Verificar requisitos da recompilação**. A barra e os estados refletem verificações reais: ISO e tamanho, espaço livre, ABI ARM64 e presença de componentes esperados. O resultado para antes da geração caso o gerador ou o compilador não esteja instalado/empacotado. A barra mede apenas o preflight, e o texto esclarece isso para não inventar porcentagem de compilação.

O log de diagnóstico também grava eventos `codegen_preflight_progress` e `codegen_preflight_blocked` em `files/logs/sotc-app.log`.

## Trabalho necessário para recompilar de verdade no aparelho

Ainda são necessários, e precisam ser validados em ARM64/Android:

- adaptar o linker/analisador de ISO para operar no app sem Python de desktop;
- expor o gerador PS2Recomp por uma API segura para o processo Android, com saída e erros capturados;
- fornecer a toolchain/compilador/linker compatível com Android e definir como as fontes geradas serão compiladas e carregadas;
- instrumentar cada etapa com eventos estruturados para uma barra de progresso real;
- testar memória máxima, armazenamento temporário, cancelamento, recuperação após falha e instalação da biblioteca resultante.

Até que esses componentes existam, não se deve mostrar progresso de recompilação nem ativar o botão **Jogar**: completar somente a importação da ISO não torna o APK jogável.
