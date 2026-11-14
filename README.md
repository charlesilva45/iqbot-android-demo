# IQBot Android Demo

Aplicativo Android experimental de **simulação local** de operações binárias. Não se conecta à IQ Option, não envia ordens e não promete rentabilidade.

## Compilar
Abra o projeto no Android Studio (JDK 17, Android SDK 35) ou execute `gradle assembleDebug` com Gradle 8.9 instalado. O APK será gerado em `app/build/outputs/apk/debug/`.

## Backtest offline no IQBot Lab 0.4

Abra **Backtest offline** no painel. Use a série sintética incluída ou selecione um CSV local em UTF-8 com o cabeçalho `timestamp,open,high,low,close`. Use ponto decimal nos preços e timestamps Unix em milissegundos UTC correspondentes à abertura de cada candle M5. Cada arquivo pode conter de 69 a 10.000 candles consecutivos e até 2 MiB.

A carteira de backtest começa com R$ 100 fictícios. Ajuste o valor fixo por operação, o retorno líquido de um acerto, o limite de perda sobre o saldo inicial e o máximo de operações. A simulação bloqueia a próxima entrada se uma perda integral ultrapassar o limite configurado. Um empate conta como perda. O relatório mostra saldo final, resultado, queda máxima desde o pico, acertos, perdas e EMA 9, EMA 21, RSI 14 e ATR 14 do último candle M5.

O APK 0.4 usa o identificador `com.iqbot.demo.lab` para instalação lado a lado com a versão anterior. O backtest usa a estratégia EMA/RSI/ATR de `StrategyEngine` e o retorno fixo informado na tela. Consulte [a política de dados históricos](HISTORICAL_DATA_POLICY.md) para registrar a origem dos dados importados.

## Limitações
- Simulação apenas, sem autenticação ou API da corretora.
- Capital inicial fictício de R$ 100.
- Nenhuma operação real ou serviço persistente em segundo plano nesta versão.
- Integração futura depende de API oficial e autorização da plataforma.
