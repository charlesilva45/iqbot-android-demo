# IQBot Android Demo

Aplicativo Android experimental de **simulação local** de operações binárias. Não se conecta à IQ Option, não envia ordens e não promete rentabilidade.

## Compilar
Abra o projeto no Android Studio (JDK 17, Android SDK 35) ou execute `gradle assembleDebug` com Gradle 8.9 instalado. O APK será gerado em `app/build/outputs/apk/debug/`.

## Limitações
- Simulação apenas, sem autenticação ou API da corretora.
- Capital inicial fictício de R$ 100.
- Nenhuma operação real ou serviço persistente em segundo plano nesta versão.
- Integração futura depende de API oficial e autorização da plataforma.
