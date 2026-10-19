# Abordagem de recompilação Android: BT3 e Shadow of the Colossus

Esta análise compara a implementação pública de [Tenkaichi3DecompAndroid / Dragon Rage](https://github.com/dfdx047/Tenkaichi3DecompAndroid) com este porte de Shadow of the Colossus. A ideia que vale adotar é separar o build nativo feito durante o desenvolvimento da instalação dos dados feita pelo jogador. O recompilador Android de BT3 não pode ser copiado como se fosse genérico: os projetos partem de representações diferentes do código do jogo.

## Como BT3 chega ao Android

O repositório de BT3 já possui o código do jogo decompilado em C, além da camada de plataforma do port de PC. O script [port/tools/android.py](https://github.com/dfdx047/Tenkaichi3DecompAndroid/blob/main/port/tools/android.py) transforma e compila esse código para ARM64 com uma toolchain LLVM específica, integra renderização e áudio e produz a biblioteca nativa libbt3.so. O loader Android carrega essa biblioteca, enquanto o app valida a ISO do jogador e extrai os arquivos de dados para a estrutura esperada pelo motor. Veja também [GameDataInstaller.kt](https://github.com/dfdx047/Tenkaichi3DecompAndroid/blob/main/android/app/src/main/java/com/dfdx047/dragonrage/data/GameDataInstaller.kt) e [loader.c](https://github.com/dfdx047/Tenkaichi3DecompAndroid/blob/main/android/app/src/main/cpp/loader.c).

Em resumo, o código principal de BT3 já existe como fontes no checkout; a ISO fornece sobretudo os dados que o motor usa em execução. O APK distribuído já contém a engine ARM64 compilada.

## Por que o fluxo atual de Shadow of the Colossus é diferente

O port upstream de [Shadow of the Colossus](https://github.com/LightVelox/sotc-vibe-pc) usa PS2Recomp. Este repositório não contém uma decompilação completa e independente do jogo em fontes: o código específico é gerado a partir de uma imagem compatível. O pipeline precisa:

1. validar a ISO PAL SCES-53326 v1.00 e os hashes do ELF/módulos;
2. relocar os módulos e gerar C++ do EE em upstream/sotc-vibe-pc/Port/generated/;
3. opcionalmente gerar os programas VU1 em C++;
4. verificar a cobertura das relocações;
5. compilar as fontes geradas junto ao runtime e à camada Android/ARM64 com o NDK;
6. criar um APK específico para essa geração.

O APK normal da CI constrói o runtime, mas não tem Port/generated/ porque o projeto não inclui nem publica a ISO nem o código gerado dela. Por isso, importar a ISO no aplicativo Android não pode ativar sozinho o botão Jogar: a importação e o build da engine são etapas diferentes.

O fluxo correspondente já existe em scripts/prepare_sotc_android.py. Para simplificar o uso no Windows, foi adicionado o wrapper scripts/prepare_sotc_android.cmd.

## O que estamos adotando de BT3

- Build nativo antecipado (AOT): gerar e compilar a engine ARM64 antes de instalar o APK, em vez de tentar executar um compilador C++ dentro do jogo.
- Separar engine e arquivos do jogador: o APK personalizado contém as bibliotecas nativas; a ISO continua sendo fornecida pelo usuário e armazenada no telefone.
- Verificações e logs: validar a versão do disco antes de gerar, manter fontes e ISO fora do Git e registrar erros para diagnóstico.
- Compilação controlada por memória: usar um processo paralelo como padrão neste helper, importante em computadores modestos.

## Procedimento recomendado

1. Em um computador Windows, clone ou atualize este projeto com os submódulos recursivos. Instale Python 3.10+, Git, CMake, JDK 17, Gradle 8.9 e o Android SDK com NDK 28.2.13676358 e CMake 3.22.1. A compilação host também precisa de uma toolchain C/C++ compatível com CMake, por exemplo Visual Studio com C++ Desktop.
2. Disponibilize nesse computador sua própria ISO SCES-53326 v1.00. O arquivo copiado para o armazenamento privado do app não é um caminho acessível diretamente ao compilador do PC; use a cópia original no armazenamento acessível. O helper também procura ISOs do tamanho esperado nas pastas Game/, game/ e isos/.
3. Execute scripts/prepare_sotc_android.cmd e informe o caminho completo da ISO quando solicitado. O wrapper inicia a geração e a compilação Release com um processo paralelo. Para uma primeira compilação mais simples, use:
   
       scripts/prepare_sotc_android.cmd "D:\caminho\SCES-53326.iso" -SkipVu1

   Isso pode reduzir trabalho de geração, mas a interpretação VU1 pode resultar em desempenho menor.
4. Quando o APK personalizado for produzido, transfira e instale esse arquivo no telefone. Abra o app e importe a ISO novamente para o armazenamento privado do novo APK.
5. Teste o boot real, gráficos, som e controles. Uma build bem-sucedida não comprova por si só que o renderer funciona corretamente no Android.

Se ocorrer erro, guarde a saída completa do primeiro comando que falhou. Para tentar gerar apenas as fontes, sem build Gradle, use:

    scripts/prepare_sotc_android.cmd "D:\caminho\SCES-53326.iso" -CodegenOnly

## Limites conhecidos

- Não é realista executar a geração e compilação completas no Galaxy A15 com 4 GB de RAM. É um fluxo de desenvolvimento para computador; usar um processo paralelo reduz o pico de memória.
- O fallback de GS por software é experimental quanto ao desempenho e à saída gráfica. Após a integração do código, ainda precisamos testar boot e renderer no dispositivo e, se necessário, adaptar o renderer especificamente para Android.
- Não fazer commit da ISO, de Port/generated/, de objetos de compilação nem de dados do jogo. O helper não os envia ao GitHub.
