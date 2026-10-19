# Gerar as fontes do jogo e compilar para Android

Este projeto recompila código do disco suportado. O APK da CI é somente o protótipo sem as fontes geradas, porque o repositório não possui nem deve distribuir uma ISO ou código derivado dela.

## Requisitos

- Cópia própria da versão PAL europeia **SCES-53326 v1.00**, ISO com exatamente **4.635.918.336 bytes**.
- Clone do repositório com submódulos recursivos. O submódulo **External/PS2Recomp** também precisa estar inicializado.
- Python 3.10+, CMake e compilador C/C++ para o computador que vai gerar o código. Não tente gerar milhares de arquivos C++ diretamente no Galaxy A15 de 4 GB; faça a geração e o build personalizado em um PC com espaço livre e memória suficiente.
- Para compilar o APK: JDK 17, Android SDK, NDK **28.2.13676358**, CMake **3.22.1** e Gradle 8.9 no PATH. Defina **ANDROID_HOME** ou **ANDROID_SDK_ROOT**.

## Comando completo

No Windows PowerShell:

    py -3 scripts/prepare_sotc_android.py --iso "D:\Jogos\SCES-53326.iso" --jobs 1

No Linux:

    python3 scripts/prepare_sotc_android.py --iso "/caminho/SCES-53326.iso" --jobs 1

O script compila as ferramentas host **ps2_recomp** e **vu1_recomp**, valida o boot ELF e os módulos usando o verificador upstream, gera as fontes em **upstream/sotc-vibe-pc/Port/generated/**, verifica os pontos de relocação e compila **:app:assembleDebug**. Para o alvo VU1, o script habilita o subprojeto upstream que define `vu1_recomp` e mantém os testes não relacionados desabilitados. A geração é feita no computador, não no Android Runtime.

Para preparar as fontes sem compilar o APK:

    python3 scripts/prepare_sotc_android.py --iso "/caminho/SCES-53326.iso" --codegen-only --jobs 1

Para uma tentativa inicial mais rápida, deixando VU1 no interpretador (potencialmente mais lento no jogo):

    python3 scripts/prepare_sotc_android.py --iso "/caminho/SCES-53326.iso" --skip-vu1 --jobs 1

O script não envia nem inclui a ISO, arquivos gerados, assets ou saves no GitHub. Não faça commit de **Port/generated/**. Durante o configure Android, o CMake aplica localmente pequenos patches idempotentes no checkout do submódulo: um injeta o estado do touch no polling do controle e outro envia o stdout/stderr do entry point para logcat e para `files/logs/sotc-boot.log`. O launcher permite compartilhar esse último log pelo menu de compartilhamento do Android. Esses patches não alteram o commit upstream armazenado no repositório principal. Se o script falhar, preserve a mensagem de erro completa: um tamanho correto não substitui a validação dos hashes dos módulos.

## O que esse build ainda não garante

Uma compilação bem-sucedida comprova somente que as fontes do jogo e o runtime linkaram. O renderizador GS acelerado original foi desenvolvido para o port de PC; no Android a configuração força o caminho por software como fallback, cujo desempenho ainda precisa ser medido. A ponte touch está ligada ao polling do controle, mas a resposta dos controles, o boot, a saída gráfica, o áudio e o gameplay precisam ser testados no aparelho. O APK padrão da CI não inclui as fontes geradas; para uma build personalizada, gere o código localmente com a sua ISO e compile o APK no mesmo checkout. Só classifique a build personalizada como jogável depois de confirmar boot, menu, entrada e gameplay real.
