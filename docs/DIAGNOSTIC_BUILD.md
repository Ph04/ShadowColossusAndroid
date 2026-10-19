# Primeiro teste: gerar logs úteis da recompilação e do Android

A versão de diagnóstico usa dois registros porque a geração do código ocorre no PC, não no telefone.

## 1. Diagnosticar a geração da ISO no PC

No clone completo, em um terminal PowerShell ou Linux, execute (troque o caminho pela sua ISO compatível):

    py -3 scripts/diagnostic_build.py --iso "D:\Jogos\SCES-53326.iso" --codegen-only --jobs 1 --skip-vu1

O script salva um arquivo em build/diagnostics com nome sotc-diagnostic-DATA-HORA.log. Ele contém versões das ferramentas, status dos submódulos, espaço em disco, tamanho do arquivo ISO, todos os comandos relevantes e sua saída até o primeiro erro. A validação de hashes do ELF e dos módulos fica a cargo do linker upstream; o wrapper não calcula um hash de 4,6 GB inteiro.

Se a geração de C++ terminar, compile também a versão de diagnóstico:

    py -3 scripts/diagnostic_build.py --iso "D:\Jogos\SCES-53326.iso" --build-type diagnostic --jobs 1 --skip-vu1

O APK personalizado aparecerá em app/build/outputs/apk/diagnostic/app-diagnostic.apk. O parâmetro --skip-vu1 é apenas uma forma de reduzir o trabalho inicial; pode deixar a execução mais lenta.

## 2. Testar o APK de diagnóstico no telefone

A CI também gera um APK de scaffold para diagnóstico, mas ele **não inclui a engine específica do jogo** porque a ISO não está disponível na CI. Para investigar o boot do jogo de verdade, use o APK diagnóstico produzido no PC depois que a geração local tiver sucesso.

Instale-o, importe a ISO novamente e toque em **Gerar e compartilhar diagnóstico completo**. O relatório inclui:
- modelo do aparelho, Android, ABI, memória e espaço livre;
- tamanho e presença da ISO privada;
- estado da engine e informações registradas pela ponte JNI;
- presença dos arquivos .so nativos;
- eventos do launcher e o trecho mais recente do log de boot.

Arquivos usados pelo aplicativo: files/logs/sotc-app.log e files/logs/sotc-boot.log. As tentativas de iniciar a engine também geram mensagens com a tag SOTC_DIAG no logcat.

## 3. O que enviar para análise

Se **a geração falhar no PC**, envie o arquivo sotc-diagnostic-*.log; ele é o mais importante para esse caso. Se a geração completar e o jogo não iniciar no telefone, envie o relatório compartilhado pelo aplicativo e, se disponível, o logcat completo.

Revise os caminhos locais e os detalhes do aparelho antes de compartilhar. Não envie a ISO nem arquivos extraídos do jogo.

## Limite importante

A build de diagnóstico melhora a coleta de evidências; ela não corrige automaticamente uma falha da ferramenta ou do renderizador. O APK da CI é apenas um scaffold de teste, não um jogo pronto. Somente um APK construído depois da geração local poderá confirmar se a engine pré-compilada está integrada; ainda será necessário testar boot e renderização no aparelho.
