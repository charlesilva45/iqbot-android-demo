# Integração IQ Option — verificação inicial (2026-11-16)

## Resultado
Não foi identificada documentação pública de API oficial de trading para contas individuais da IQ Option. A biblioteca comunitária `iqoptionapi` declara explicitamente ser não oficial; não deve ser tratada como integração autorizada.

Referências:
- https://github.com/iqoptionapi/iqoptionapi/blob/master/README.md
- https://iatraderpro.com/iq-option-tem-api-oficial-2026/

## Critérios para avançar
1. Obter confirmação/documentação oficial de acesso autorizado antes de implementar autenticação ou execução de ordens.
2. Não armazenar senhas, tokens ou cookies no repositório, APK ou logs.
3. Até lá, manter apenas simulação local e dados de mercado obtidos de fontes com licença adequada.
4. Desabilitar por padrão qualquer execução de ordens; exigir bloqueio explícito de conta real e testes automatizados.
5. Continuar testes de sinais CALL, gestão de risco e compilação do APK, sem alegar conexão à corretora.

## Status
Integração da conta demonstrativa: BLOQUEADA até validação de acesso autorizado. Nenhum login foi implementado ou testado nesta verificação.
