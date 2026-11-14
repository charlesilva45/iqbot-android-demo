# Validação do IQBot Lab — APK de simulação

## Estado e escopo
Este projeto é uma simulação local. Não autentica na IQ Option, não acessa cotações reais e não envia ordens. Uma compilação bem-sucedida não comprova que as estratégias funcionam ou que a execução em segundo plano está disponível.

## Critérios antes de disponibilizar APK de teste
- [ ] Compilar o commit atual em ambiente limpo com JDK e Android SDK documentados.
- [ ] Registrar commit, data, versão e hash SHA-256 do APK gerado.
- [ ] Instalar em Android físico e confirmar abertura sem travamentos.
- [ ] Confirmar que saldo inicial fictício é R$ 100 e que a interface indica **SIMULAÇÃO** de forma inequívoca.
- [ ] Verificar que nenhuma ordem real, login na corretora ou requisição a endpoint de negociação ocorre.
- [ ] Executar testes determinísticos com candles históricos/fictícios, sem acesso a candles futuros.
- [ ] Testar mercado lateral (abstenção), tendência de alta/baixa, choque de volatilidade, cooldown e expiração.
- [ ] Verificar limites de risco: nunca executar sinal com dados incompletos ou inválidos.
- [ ] Testar pausa, retomada e encerramento da simulação sem duplicar operações.
- [ ] Testar rotação de tela, perda de conectividade e retorno do aplicativo.
- [ ] Documentar claramente que o app não opera em segundo plano com a tela apagada até haver implementação e teste específicos.

## Bloqueios para integração futura
Antes de considerar conexão com conta demonstrativa, comprovar existência de interface oficial autorizada pela plataforma, revisar termos e segurança das credenciais, implementar autenticação sem armazenar senhas em texto puro e obter consentimento explícito do usuário. Nunca habilitar conta real por padrão.

## Evidências necessárias
Anexar ao lançamento: resultado de build, relatório de testes, versão do Android, logs relevantes sem dados sensíveis e link do artefato APK. Se qualquer critério estiver pendente, classificar o pacote como **experimental, não validado**.
