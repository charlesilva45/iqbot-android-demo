# Backtest histórico BTC/USDT · junho de 2024

Dados públicos de candles spot M5 da Binance, consultados em 16/11/2026. Os preços OHLC vêm das negociações nesse mercado e os timestamps indicam a abertura em UTC, em milissegundos. O estudo usa os preços como entrada para pesquisa local. Nenhum dado bruto ou normalizado é redistribuído com o código ou o APK.

- [Documentação do provedor](https://github.com/binance/binance-public-data)
- [ZIP original](https://data.binance.vision/data/spot/monthly/klines/BTCUSDT/5m/BTCUSDT-5m-2024-06.zip), 440.069 bytes, SHA-256 `627dd48125d89759e498d2db2e523166787bda4dd9a64ca56abf9fba235ba71e`
- [SHA-256 publicado pelo provedor](https://data.binance.vision/data/spot/monthly/klines/BTCUSDT/5m/BTCUSDT-5m-2024-06.zip.CHECKSUM), conferido com os bytes baixados
- CSV normalizado com cabeçalho `timestamp,open,high,low,close`, cinco primeiras colunas do arquivo original e LF ao final de cada linha. SHA-256 `6c2e701870ef304a9582f4170327b52b42c05ace7d526c48065df2ce6631ab36`

A validação aceitou 8.640 candles consecutivos sem lacunas ou duplicatas. Foi usado o motor do aplicativo com regras fixas e sem ajuste de parâmetros no histórico. Cada segmento parte de R$ 100 fictícios, usa R$ 2 por CALL, retorno líquido hipotético de 80% em acertos, máximo de dez operações e saldo mínimo de R$ 90 após uma perda integral. O segundo segmento usa 66 candles anteriores apenas para aquecimento. A entrada simulada ocorre na abertura do próximo M5 e o encerramento ocorre no fechamento desse M5. Empates entram como perda.

| Segmento UTC | Candles | CALL | Acertos | Perdas | Resultado simulado | Queda máxima |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 01–21/06 | 6.048 | 5 | 0 | 5 | −R$ 10,00 | R$ 10,00 |
| 22–30/06 | 2.592 | 10 | 5 | 5 | −R$ 2,00 | R$ 8,00 |

No primeiro segmento o saldo atingiu o piso. No segundo o limite de dez operações foi atingido. Houve zero empates. A modelagem usa o OHLC da Binance, payout fixo e execução hipotética, sem spread, taxas ou latência da IQ Option. A amostra de operações é pequena.

## Reproduzir com JDK 17

Após conferir o SHA-256 e baixar o ZIP para o diretório atual:

```bash
{ printf 'timestamp,open,high,low,close\n'
  unzip -p BTCUSDT-5m-2024-06.zip | cut -d, -f1-5 | tr -d '\r'
} > btcusdt-m5.csv
mkdir -p /tmp/iqbot-classes
javac --release 17 -d /tmp/iqbot-classes \
  app/src/main/java/com/iqbot/demo/{CandleCsv,OfflineBacktest,StrategyEngine}.java tools/HistoricalReplay.java
java -cp /tmp/iqbot-classes com.iqbot.demo.HistoricalReplay btcusdt-m5.csv
```
