# Validação do IQBot Lab 0.4.1

## Verificações automatizadas

O GitHub Actions executa os testes Java antes de compilar o APK debug. O artefato inclui saída dos testes, identificação do pacote, permissões, verificação da assinatura e SHA-256. Os testes cobrem indicadores, dados inválidos, cronologia, risco, CALL/WAIT, choque e equivalência entre reprodução e backtest.

## Roteiro no aparelho

1. Instale o APK e abra **Executar estratégia CALL/WAIT (offline)**.
2. Execute o backtest da série sintética. Confira o saldo inicial fictício de R$ 100, o relatório e o rótulo da fonte. Importe um CSV M5 local para testar outro conjunto de candles.
3. Toque em **Reproduzir em segundo plano**, permita notificações e bloqueie a tela. Confirme que a execução termina e que o resultado aparece ao reabrir o app. Se usar a notificação na tela bloqueada, respeite a configuração de visibilidade do Android.
4. Repita e toque em **Parar** na notificação antes do fim. Confirme que a notificação some e a tela indica interrupção. Abra uma nova execução e verifique que começa de R$ 100 fictícios.
5. Repita alternando de app e girando a tela durante a execução. O Android pode interromper um processo encerrado à força. A sessão não é reiniciada automaticamente.

A validação física no POCO F7 Ultra permanece pendente. O serviço só inicia mediante ação no app e tem limite de 90 segundos por sessão. Ordens e logins na corretora estão fora do simulador.
