# derivBot 1.0 — bloqueio regulatório e escopo offline

Consulta em 20/11/2026. A CVM publicou em 14/06/2023 (atualização 29/01/2024) aviso sobre Deriv.com/Binary.com, Ato Declaratório CVM 20.948, determinando suspensão de oferta pública de intermediação de valores mobiliários a residentes no Brasil: https://www.gov.br/cvm/pt-br/assuntos/noticias/2023/cvm-alerta-para-atuacao-irregular-de-deriv-com-e-binary.com

A existência de uma API técnica não equivale a autorização regulatória. Documentação introdutória oficial: https://deriv.com/pt/partners-help-center-questions/what-is-deriv-api

**Decisão de segurança:** não implementar autenticação, acesso a contas, conexão autenticada, cotações Deriv ou envio de ordens enquanto a autorização aplicável não estiver comprovada. Sem VPN, contorno territorial, tokens, credenciais ou segredos em repositório. Somente simulação local com dados sintéticos ou CSV de origem autorizada. Este documento não atesta situação jurídica atual definitiva; qualquer mudança requer nova verificação formal.

O código histórico IQ Option e branches anteriores permanecem preservados; a interface derivBot não fornece mais acesso ao site da corretora. O APK não declara INTERNET e a CI deve rejeitar alterações que reintroduzam o acesso.
