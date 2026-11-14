# IQBot 0.3 — motor experimental (em desenvolvimento)

Código inicial em `app/src/main/java/com/iqbot/demo/StrategyEngine.java`.

## Regras
- EMA(9) e EMA(21) no M5 e no M15 fechado, RSI(14), ATR(14).
- CALL: tendência de alta em ambos os períodos, RSI 52–68, candle positivo e fechamento acima da máxima anterior.
- PUT: condições inversas, RSI 32–48, candle negativo e fechamento abaixo da mínima anterior.
- Caso contrário: WAIT. Não há integração com corretora nem envio de ordens.
- Entrada simulada na abertura do candle M5 seguinte ao sinal, expiração no fechamento desse candle.
- Saldo e payout parametrizáveis, sem Martingale. Empates são perdas no teste.

## Dados e validação pendentes
O motor aceita candles OHLC em memória, mas **ainda não possui importação CSV, feed de preços reais, interface Android 0.3 ou testes automatizados**. Não é uma estratégia validada e não há resultado de backtest real. Antes de avaliar rentabilidade, implementar importação de dados com timezone e timestamp de abertura/fechamento, rejeição de lacunas, agregação M15 alinhada ao relógio da bolsa, custos/payout históricos e validação fora da amostra.

## Limites
As 10 simulações do aplicativo 0.2 continuam aleatórias. Esta adição ao repositório não altera automaticamente o APK já instalado. Não utilizar para operar dinheiro real.
