# Estado do porte

**Status:** infraestrutura inicial de compilação, ainda não jogável.

## Confirmado no repositório

- Projeto Android Gradle com alvo `arm64-v8a`.
- Aplicativo Kotlin de diagnóstico.
- Biblioteca C++ mínima via JNI.
- Workflow GitHub Actions `.github/workflows/android-apk.yml` para compilar e publicar o APK de depuração como artefato.
- Nenhum arquivo de jogo, ISO ou asset proprietário incluído.

## Próximo trabalho de engenharia

1. Gerar uma matriz de dependências e licenças do runtime PS2Recomp.
2. Separar as ferramentas host de geração do código recompilado do runtime Android.
3. Compilar o runtime em Clang/NDK e resolver dependências Windows/x86.
4. Criar o adaptador Android para a engine e renderização.
5. Validar boot real com arquivos do usuário e controles.

## Compilação de teste

Abra **Actions → Android APK → Run workflow**. Baixe o artefato `ShadowColossusAndroid-debug-apk` ao terminar.

O APK inicial só valida a instalação e a ponte JNI, não executa o jogo.
