# AutoMirror Lab (0.1.0)

Protótipo experimental Android para capturar a tela com a API oficial `MediaProjection` e transmiti-la como MJPEG por HTTP na rede local. **Esta versão não integra diretamente com a tela do Android Auto.** É a primeira etapa para validar captura, codificação e transporte antes de desenvolver a integração não oficial com a central.

## Requisitos
- Android Studio recente
- JDK 17 (normalmente fornecido pelo Android Studio)
- Celular Android 8.0+ (API 26+); o alvo de teste é Android 13 ou anterior
- Celular e receptor conectados à mesma rede Wi-Fi

## Compilar APK
1. Descompacte o projeto.
2. Abra a pasta `AutoMirrorLab` no Android Studio.
3. Aguarde a sincronização do Gradle.
4. Se necessário, instale o Android SDK Platform 35 e Build Tools solicitados pelo Android Studio.
5. Use **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. O APK de debug costuma ficar em `app/build/outputs/apk/debug/app-debug.apk`.

## Testar
1. Instale e abra o app no celular.
2. Toque em **Iniciar captura e transmissão** e aceite a caixa de diálogo do Android para compartilhar a tela.
3. Descubra o IP local do celular nas configurações de Wi-Fi do Android.
4. Em outro dispositivo na mesma rede, abra `http://IP_DO_CELULAR:8765/`.
5. Para encerrar, volte ao app e toque em **Parar transmissão**.

Se a porta 8765 estiver indisponível, altere `port` em `CaptureService.kt` e compile novamente. O roteador pode bloquear a comunicação entre dispositivos Wi-Fi; nesse caso, use uma rede local confiável que permita tráfego entre clientes.

## Limitações conhecidas
- O receptor é um navegador comum; não é a interface do Android Auto.
- A transmissão é unidirecional (sem toque remoto), sem autenticação e não deve ser exposta à internet. Use somente numa rede confiável e para testes.
- O conteúdo protegido por DRM pode aparecer preto; o Android pode ocultar telas seguras e diálogos do sistema.
- A captura consome bateria, rede e CPU; a resolução é limitada a 1280 px de largura e a qualidade JPEG é reduzida para teste.
- Para aparecer na central original do Android Auto, é necessária uma integração não oficial separada. O Android Auto não fornece uma API pública genérica de espelhamento de tela. A compatibilidade varia conforme Android Auto, Google Play Services e firmware da central.
- Use exclusivamente com o veículo estacionado. Não utilize espelhamento de vídeo durante a condução.

## Próxima etapa: integração com Android Auto
Investigar uma implementação comunitária atual de superfície de navegação/projeção e validar se ela funciona com a versão instalada do Android Auto. Essa integração não deve ser confundida com a API oficial `MediaProjection`; são componentes distintos.
