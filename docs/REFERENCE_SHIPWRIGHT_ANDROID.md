# Referência técnica: Shipwright-Android

Referência analisada: [Waterdish/Shipwright-Android](https://github.com/Waterdish/Shipwright-Android), usando o README, o projeto Gradle Android, o CMake da raiz e o código de extração da ROM.

## O que o Shipwright-Android faz

### 1. Compila o motor nativo antes de instalar o APK

O arquivo [Android/app/build.gradle](https://github.com/Waterdish/Shipwright-Android/blob/main/Android/app/build.gradle) aponta o build nativo do Gradle para o `CMakeLists.txt` da raiz e configura o uso de OpenGL ES. O CMake da raiz compila o alvo `soh` e os componentes auxiliares para a ABI Android selecionada. Assim, a engine decompilada é compilada durante a build do aplicativo, não a partir da ROM na primeira execução.

### 2. Distribui recursos próprios e prepara os dados do usuário

O APK contém um pacote `soh.otr` de recursos do port e os arquivos de configuração/XML usados pelo extrator. Na preparação inicial, [MainActivity.java](https://github.com/Waterdish/Shipwright-Android/blob/main/Android/app/src/main/java/com/dishii/soh/MainActivity.java) copia esses arquivos para a pasta de dados do jogo.

### 3. Usa a ROM para extrair recursos, não para compilar o código do jogo

Em [Extract.cpp](https://github.com/Waterdish/Shipwright-Android/blob/main/soh/soh/Extractor/Extract.cpp), o extrator verifica tamanho e CRC da ROM, corrige a ordem de bytes conforme necessário e chama `zapd_main` com a ROM escolhida, XMLs e listas de arquivos. A saída é um pacote `oot.otr` ou `oot-mq.otr` que contém os dados extraídos para o runtime. Isso é uma etapa de preparação de **assets**, não recompilação do executável do jogo para C++.

### 4. Passa a seleção do arquivo do Java para o código nativo

A Activity escolhe a ROM por meio do seletor de arquivos e envia o caminho a `nativeHandleSelectedFile`. O código nativo passa a tratar essa imagem. Esse padrão JNI é útil para manter a UI Android pequena enquanto o motor/extrator vive em C++.

### 5. Build reprodutível com ferramentas fixadas

O workflow e os scripts Docker do repositório preparam Java, Gradle, Android SDK/NDK e CMake antes de compilar o APK. Isso é uma boa referência para builds reprodutíveis e diagnóstico de ferramentas, embora as versões exatas do Shipwright não precisem ser copiadas para este projeto.

## O que podemos aproveitar no ShadowColossusAndroid

- **Separar engine de dados do disco.** O APK deve carregar o runtime/engine ARM64 já compilado e deixar a ISO privada no armazenamento do usuário.
- **Cadeia nativa explícita.** Manter o launcher Kotlin como UI e ligar o código do jogo, PS2Recomp runtime, renderizador, input e logs por CMake/NDK.
- **Validação antes do boot.** Verificar a versão exata da ISO antes de habilitar Jogar. Esta implementação já verifica tamanho e SHA-256; os checks de boot ELF/módulos do linker/runtime continuam sendo uma validação separada.
- **Preparação de dados idempotente.** Guardar dados e caches em diretório privado, identificar o que já foi preparado e não repetir trabalho caro sem necessidade.
- **Progresso real e erros rastreáveis.** Mostrar cópia/validação de arquivo com base em bytes processados e as etapas nativas com eventos concretos. O próprio Shipwright não fornece um percentual fino para a extração em todas as plataformas; não devemos imitar uma porcentagem fictícia.
- **Build/teste por ABI.** O projeto de referência usa CMake via Gradle e distingue configuração de desenvolvimento da configuração de release. Podemos manter as versões de SDK/NDK compatíveis com o nosso runtime, sem copiar cegamente as versões antigas do outro projeto.

## O que não é possível copiar diretamente

A diferença decisiva está nos códigos-fonte. Shipwright parte de um projeto decompilado cujo código do jogo está nos fontes C/C++ e usa a ROM principalmente para extrair os recursos necessários. O upstream de Shadow of the Colossus usado aqui gera fontes C++ específicas usando PS2Recomp a partir do ELF e dos módulos da ISO.

Por isso, copiar somente o Gradle, o CMake ou o seletor JNI do Shipwright **não resolve** a ausência das fontes geradas do Shadow of the Colossus. O fluxo atual de [prepare_sotc_android.py](../scripts/prepare_sotc_android.py) ainda precisa gerar essas fontes no ambiente de build e ligá-las ao APK.

Para que uma build pública pré-compilada funcione sem um build personalizado por ISO, seria necessário ter uma implementação estática/decompilada do código do jogo ou uma solução de distribuição autorizada para a engine gerada. Isso é uma frente de trabalho muito maior que portar o launcher.

## Plano recomendado

1. Continuar gerando e compilando a engine específica em ambiente de build antes de empacotar o APK personalizado.
2. Manter a ISO fora do APK e fora do Git; no celular, validá-la e passá-la ao runtime nativo.
3. Depois de obter uma engine ligada, implementar a preparação dos dados que realmente forem necessários, sem criar um segundo gerador de C++ dentro do app.
4. Medir boot, renderização, áudio, memória e controles no Galaxy A15 antes de otimizar o pipeline de assets.

## Fontes consultadas

- [README do Shipwright-Android](https://github.com/Waterdish/Shipwright-Android/blob/main/README.md)
- [Build Android (Gradle/NDK)](https://github.com/Waterdish/Shipwright-Android/blob/main/Android/app/build.gradle)
- [Preparação dos arquivos no Android](https://github.com/Waterdish/Shipwright-Android/blob/main/Android/app/src/main/java/com/dishii/soh/MainActivity.java)
- [Extração da ROM para OTR no código nativo](https://github.com/Waterdish/Shipwright-Android/blob/main/soh/soh/Extractor/Extract.cpp)
- [Workflow de build](https://github.com/Waterdish/Shipwright-Android/blob/main/.github/workflows/project_build.yml)
