# Simulação offline — IQBot Lab 0.4.1

No painel, abra **Executar estratégia CALL/WAIT (offline)**. Use o CSV local ou a série sintética. **Executar backtest** processa o histórico de uma vez. **Reproduzir em segundo plano** processa a mesma lógica em sequência e mostra o andamento na notificação. A reprodução pode ser interrompida no app ou pelo botão **Parar** da notificação. Após encerrar o serviço, uma posição pendente é descartada. A reprodução exige início explícito e não reinicia após encerramento pelo Android.

## Backtest histórico de 16/11/2026

Binance Vision, BTCUSDT no mercado à vista, preços OHLC de negócios em USDT, M5. Foram validados 8.928 candles consecutivos de outubro de 2024. Todas as regras já estavam definidas antes de consultar o resultado.

- [Arquivo bruto](https://data.binance.vision/data/spot/monthly/klines/BTCUSDT/5m/BTCUSDT-5m-2024-10.zip), 458990 bytes conforme o arquivo baixado. SHA-256 conferido contra o [checksum do provedor](https://data.binance.vision/data/spot/monthly/klines/BTCUSDT/5m/BTCUSDT-5m-2024-10.zip.CHECKSUM), `5cd2d0dcc2dc193fc7b31be69166021fca41b9fce61ab6a0771ce00db864c327`.
- Normalização selecionou as cinco primeiras colunas, adicionou o cabeçalho `timestamp,open,high,low,close` e gravou com quebra LF. Timestamps já estavam em milissegundos UTC de abertura. SHA-256 normalizado `b572e81f95613a24d644291cb26414ccacf559ce896f0bf79c44354bda7d001a`.
- Uso para pesquisa pessoal segundo os [termos do dataset](https://github.com/binance/binance-public-data/blob/master/TERMS_AND_CONDITIONS.md). O arquivo de dados fica fora do APK e do repositório.

| Sessão independente | Candles disponíveis | Candles processados | Acertos / perdas | Saldo virtual final | Queda máxima |
| --- | ---: | ---: | ---: | ---: | ---: |
| Mês inteiro | 8.928 | 83 | 5 / 5 | R$ 98,00 | R$ 5,20 |
| Primeiros 70% | 6.249 | 83 | 5 / 5 | R$ 98,00 | R$ 5,20 |
| Últimos 30% | 2.679 | 250 | 4 / 6 | R$ 94,40 | R$ 8,40 |

O limite de dez operações encerrou cada sessão. A segunda janela começa em 22/10/2024, 16:45 UTC, com saldo fictício reiniciado e aquecimento dentro do próprio trecho. A primeira janela e o mês inteiro coincidem porque ambos atingem o limite em 01/10/2024, às 06:55 UTC. Hipóteses por sessão: saldo inicial de R$ 100, entrada de R$ 2, retorno líquido de 80% no acerto, perda máxima de R$ 10 sobre o saldo inicial, empate como perda e liquidação na próxima vela. Os números refletem essa janela curta de execução e parâmetros hipotéticos. O simulador não modela spread, taxas, latência ou cotações da IQ Option.

Para reproduzir com o CSV normalizado:

```bash
mkdir -p /tmp/iqbot-research
javac -d /tmp/iqbot-research app/src/main/java/com/iqbot/demo/{StrategyEngine,StrategySelector,CandleCsv,OfflineBacktest,ReplaySession}.java app/src/test/java/com/iqbot/demo/HistoricalReplay.java
java -cp /tmp/iqbot-research com.iqbot.demo.HistoricalReplay BTCUSDT-5m.csv
```
