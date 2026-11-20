# derivBot 1.0

Aplicativo Android experimental de **simulação local** de operações binárias. Não se conecta à IQ Option, não envia ordens e não promete rentabilidade.

## Compilar
Abra o projeto no Android Studio (JDK 17, Android SDK 35) ou execute `gradle assembleDebug` com Gradle 8.9 instalado. O APK será gerado em `app/build/outputs/apk/debug/`.

## Backtest offline no derivBot

Abra **Executar estratégia CALL/WAIT (offline)** no painel. Use a série sintética incluída ou selecione um CSV local em UTF-8 com o cabeçalho `timestamp,open,high,low,close`. Use ponto decimal nos preços e timestamps Unix em milissegundos UTC correspondentes à abertura de cada candle M5. Cada arquivo pode conter de 69 a 10.000 candles consecutivos e até 2 MiB.

A carteira de backtest começa com R$ 100 fictícios. Ajuste o valor fixo por operação, o retorno líquido de um acerto, o limite de perda sobre o saldo inicial e o máximo de operações. A simulação bloqueia a próxima entrada se uma perda integral ultrapassar o limite configurado. Um empate conta como perda. O relatório mostra saldo final, resultado, queda máxima desde o pico, acertos, perdas e EMA 9, EMA 21, RSI 14 e ATR 14 do último candle M5.

O projeto usa o identificador `com.iqbot.demo.lab` para instalação lado a lado com a versão anterior. O backtest usa a estratégia EMA/RSI/ATR de `StrategyEngine` e o retorno fixo informado na tela. Consulte [a política de dados históricos](HISTORICAL_DATA_POLICY.md) para registrar a origem dos dados importados.

**Executar backtest** roda o histórico em sequência. **Reproduzir em segundo plano** roda a mesma estratégia com progresso acompanhado por notificação e ação **Parar**. O Android pode encerrar a sessão. A seleção do CSV pertence à tela atual e o último resultado fica no aparelho. O motor aplica uma pausa no candle de choque e nos três candles fechados seguintes, usando ATR conhecido antes do choque. O consenso de cinco modelos é um diagnóstico separado.

[Resultados e reprodução do teste histórico](docs/backtest-btcusdt-2024-06.md) · [Validação no Android](docs/VALIDACAO_APK_SIMULACAO.md)

## Limitações
- Simulação apenas, sem autenticação ou API da corretora.
- Capital inicial fictício de R$ 100.
- A reprodução em segundo plano é uma sessão local finita e iniciada pelo usuário.
- Integrações IQ Option e Deriv estão suspensas. Esta versão não autentica contas nem transmite ordens. Qualquer integração futura depende de validação regulatória e autorização da plataforma.
