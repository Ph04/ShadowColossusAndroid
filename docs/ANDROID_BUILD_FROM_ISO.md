# Gerar o código Celosia da edição USA e compilar para Android

Esta branch experimental usa a base Celosia/PS2Recomp e a edição americana de Shadow of the Colossus. A ISO, os módulos capturados, dumps de RAM e o código C++ recompilado permanecem no computador de desenvolvimento; não os envie ao GitHub.

## Pré-requisitos

- Uma cópia própria da ISO americana de Shadow of the Colossus que contenha o boot ELF `SCUS_974.72`, `SYSTEM.CNF`, `KERNEL.XFF`, `MANAGER.XFF` e `GAMECORE.XFF`.
- Git, Python 3, CMake, Ninja, compilador C/C++ e ferramentas POSIX.
- `bsdtar` e `pkg-config`. A preparação usa o script oficial do Celosia, que captura módulos por meio de execuções do runner host.
- Para montar o APK: JDK 17, Android SDK, NDK `28.2.13676358`, CMake Android `3.22.1` e Gradle 8.9 no PATH.
- Espaço livre considerável para o clone Celosia, builds host, capturas de módulos e código gerado. Não faça a geração massiva no celular.

No Windows, use WSL2 com a toolchain Linux configurada. A ISO pode ficar em um caminho montado como `/mnt/d/...`.

## Validar a base

Na raiz do checkout:

    python3 scripts/prepare_celosia_android.py --check-source-tree

O helper fixa Celosia no commit `ed1025701cfa943d7c1649d88e5175a51ff33d5e` em `upstream/celosia/`. Os patches Android são aplicados apenas a um espelho descartável em `build/celosia-source/`; o checkout Celosia fixado permanece limpo.

## Geração local, sem APK

Exemplo Linux/WSL2:

    python3 scripts/prepare_celosia_android.py --iso "/caminho/para/SOTC-USA.iso" --codegen-only --jobs 1

O helper executa o pipeline Celosia de setup, extração, captura de módulos e recompilação. As saídas esperadas incluem:

- `build/celosia-disc/SCUS_974.72` e os módulos extraídos da ISO.
- `build/celosia/runner/`: funções C++ do EE recompiladas.
- `build/celosia/vu1rec/`: programas VU1 recompilados quando o pipeline os captura.

Se o script do Celosia não conseguir capturar um módulo, preserve os logs completos e não prossiga tratando uma geração incompleta como válida.

## Build completo do APK

    python3 scripts/prepare_celosia_android.py --iso "/caminho/para/SOTC-USA.iso" --jobs 1 --build-type release

O pipeline gera o código específico da sua cópia e então executa Gradle/NDK. Para depuração, use `--build-type debug`; o perfil `diagnostic` mantém relatórios adicionais. O APK gerado é local e assinado pela chave debug padrão para instalação de teste, não para publicação na Play Store.

Não execute o comando completo antes de conseguir gerar as fontes; se já existe uma geração verificada em `build/celosia`, `--skip-setup` permite reutilizá-la:

    python3 scripts/prepare_celosia_android.py --skip-setup --build-type debug

O helper verifica que existem fontes recompiladas e os quatro arquivos principais do disco antes de chamar Gradle. A opção `--skip-setup` não corrige nem completa uma geração faltante.

## Exportar o pacote de preparação pelo celular

Na build Android, toque em **Exportar pacote para geração Celosia** e salve `Celosia-USA-preparation.zip`. O pacote inclui apenas `SCUS_974.72`, os três módulos XFF, `SYSTEM.CNF`, logs e o marcador de validação; ele não inclui a ISO inteira nem os saves. Se existirem saídas em `files/celosia-output/{runner,vu1rec,captures}`, também serão incluídas. Essa build Android **não gera C++ no próprio telefone**: o ZIP reúne entradas e eventuais saídas que já existirem, para permitir executar a etapa Celosia num ambiente de compilação.

Depois de extrair o ZIP num computador Linux/WSL2, a pasta `disc/` contém os arquivos mínimos para continuar sem pedir a ISO completa. Na raiz do checkout, execute:

    python3 scripts/prepare_celosia_android.py --disc-dir "/caminho/extraido/disc" --codegen-only --jobs 1

O helper copia os arquivos exigidos para `build/celosia-disc/` e executa o pipeline Celosia, que ainda precisa abrir o runner host para capturar os módulos. Se esse ambiente concluir as capturas e a geração VU1, o próximo passo é compilar o APK no mesmo ambiente, ou exportar os artefatos gerados para integração posterior. O envio do ZIP não garante que a geração complete; os logs devem indicar em qual etapa parou.

## Como funciona a importação no Android

Selecione sua ISO americana no launcher. O importador:

1. Copia a imagem temporariamente para o armazenamento privado e calcula o SHA-256 de origem para diagnóstico (o valor não é tratado como um hash oficial de autenticidade).
2. Lê o descritor primário ISO-9660 e verifica o caminho de boot americano em `SYSTEM.CNF`.
3. Extrai arquivos e exige `SCUS_974.72`, `KERNEL.XFF`, `MANAGER.XFF` e `GAMECORE.XFF`.
4. Publica a pasta privada `files/game/disc/` e grava um marcador de validação estrutural. A ISO temporária é removida após a conclusão para economizar espaço.

Durante a importação, mantenha espaço livre aproximadamente equivalente a duas cópias da ISO mais uma margem de segurança. O importador não usa o tamanho/hash específico da edição PAL como critério para aceitar a edição USA. O marcador significa que a estrutura esperada foi extraída, não é uma prova criptográfica independente da procedência da ISO.

Os saves ficam em `files/memcards/` no armazenamento privado da aplicação.

## Limitações importantes

- O build automático da CI é um **APK de diagnóstico**. Não contém fontes C++ recompiladas de uma ISO pessoal.
- Celosia fornece runtime Android/raylib no seu código atual, mas o renderer nativo Metal/OpenGL ES do port não é um backend Android pronto. O primeiro teste Android usa o GS por software.
- Uma compilação NDK bem-sucedida prova apenas que o código foi compilado e ligado. Ela não comprova boot, saída gráfica, som, controles, carregamento de níveis, saves nem desempenho.
- Não envie a ISO, dados extraídos, capturas de RAM, módulos capturados, memória card, assets ou fontes C++ geradas ao GitHub. A licença do Celosia é GPL-3.0; preserve os avisos e cumpra as obrigações da licença antes de redistribuir trabalhos derivados.
