# Integração IQ Option — verificação inicial (2026-11-16)

## Resultado
Não foi identificada documentação pública de API oficial de trading para contas individuais da IQ Option. A biblioteca comunitária `iqoptionapi` declara explicitamente ser não oficial; não deve ser tratada como integração autorizada.

Referências:
- https://github.com/iqoptionapi/iqoptionapi/blob/master/README.md
- https://iatraderpro.com/iq-option-tem-api-oficial-2026/

## Fontes da plataforma revisadas

- [Conta demonstrativa](https://iqoption.com/pt/demo-trading) descreve a seleção do saldo de prática na sala de negociação e os clientes web e móveis.
- [Termos da conta de demonstração](https://iqoption.com/pt/terms-and-conditions/practice-account-terms) descrevem a conta de prática e o saldo virtual. A documentação pública consultada não oferece credenciais, endpoints ou autorização para um cliente externo controlar essa conta.
- [Termos gerais da plataforma](https://iqoption.com/pt/terms-and-conditions/terms-conditions?company_id=2&country_id=30&locale=pt&platform=2) e [canal oficial de contato](https://iqoption.com/pt/contacts).

A existência de uma API privada ou para parceiros depende de confirmação pela IQ Option. O aplicativo abre o site oficial no navegador e não declara a permissão Android INTERNET.

## Critérios para avançar
1. Obter confirmação/documentação oficial de acesso autorizado antes de implementar autenticação ou execução de ordens.
2. Não armazenar senhas, tokens ou cookies no repositório, APK ou logs.
3. Até lá, manter apenas simulação local e dados de mercado obtidos de fontes com licença adequada.
4. Desabilitar por padrão qualquer execução de ordens; exigir bloqueio explícito de conta real e testes automatizados.
5. Continuar testes de sinais CALL, gestão de risco e compilação do APK, sem alegar conexão à corretora.

## Status
Integração da conta demonstrativa: BLOQUEADA até validação de acesso autorizado. Nenhum login foi implementado ou testado nesta verificação.
