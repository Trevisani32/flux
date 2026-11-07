# ADR 0011 — Limite de tentativas e envio de e-mail

- **Status:** Aceito
- **Data:** 2026-11-06

## Contexto

O [ADR 0009](0009-contas-de-usuario-publicas.md) abriu o cadastro público, a
confirmação de e-mail e a recuperação de senha, e deixou para o plano da Fatia 02 três
pontos: onde contar o limite de tentativas, se o cadastro teria captcha e como montar o
envio de e-mail depois do commit. O sistema roda numa única instância da API, atrás de
um nginx.

## Decisão

### Limite de tentativas

- Fica **em memória**, atrás da interface `LimiteDeTentativas`, com janela deslizante
  por chave (ação + IP, ação + e-mail ou ação + usuário). Reiniciar a API zera os
  contadores.
- No login, só as tentativas erradas contam: a tentativa é contada antes de conferir a
  senha e devolvida quando a senha confere. Nas outras ações, todo pedido conta, salvo a
  troca de e-mail (abaixo). Um pedido barrado não conta.
- A resposta é `429` com `ProblemDetail` e `Retry-After` em segundos.
- O IP é o `getRemoteAddr()` depois da `RemoteIpValve` do Tomcat
  (`server.forward-headers-strategy: native`): o `X-Forwarded-For` só vale quando a
  requisição vem de um proxy interno. O nginx sobrescreve o cabeçalho com o IP que ele
  viu.
- Sem captcha. Confirmação de e-mail obrigatória, limite por IP e por e-mail e a
  limpeza dos cadastros não confirmados bastam por enquanto.
- Bloqueio direcionado de um e-mail (10 senhas erradas de propósito travam o login
  daquele e-mail por 15 min, e dá para renovar): aceito. Reiniciar a API libera.
  Reavaliar na Fatia 08.
- O pedido de troca de e-mail manda o link para um endereço que ninguém confirmou. Por
  isso, além do limite da senha atual, tem 3 por hora por endereço novo e 3 por hora por
  conta. Os três são contados antes de conferir a senha, para o `429` não revelar se ela
  estava certa; a senha errada e o e-mail igual ao atual devolvem os dois da troca; só
  conta a troca aceita.
- Bloqueio direcionado de um endereço na troca (uma conta que pede 3 trocas por hora para
  o endereço X impede outras contas de pedirem a troca para X): é consequência do limite
  decidido pelo dono. Aceito; reiniciar a API libera; reavaliar na Fatia 08.

### Envio de e-mail

- `EnvioDeEmail` é uma interface do módulo `compartilhado`; a implementação usa o
  `JavaMailSender` do Spring Mail. Dev, testes e e2e usam o Mailpit; produção usa SMTP
  configurado por variáveis de ambiente.
- Os e-mails saem por listeners `@ApplicationModuleListener` (assíncronos, numa
  transação nova, depois do commit), com o **registro persistente de eventos do Spring
  Modulith** em JDBC. A tabela `event_publication` é criada pelo Flyway.
  - Concluído o envio, a publicação é apagada.
  - Uma publicação que falhou é reenviada a cada 5 minutos e em toda subida da API.
  - Publicações com mais de 7 dias são apagadas.
- **O valor de um link nunca vai para o banco nem para o evento.** O pedido grava o
  link sem valor; o listener gera o valor, guarda só o hash SHA-256 e envia o e-mail na
  mesma transação. Se o envio falhar, nada fica gravado e o reenvio gera outro valor.
- Os listeners rodam no executor de tarefas do Spring com **2 threads**: cada envio
  segura uma conexão do banco durante o SMTP, e o pool tem 10.
- Os pedidos que não podem revelar se um e-mail tem conta (cadastro, reenvio, esqueci a
  senha e troca de e-mail) respondem igual nos dois casos. Na troca para um e-mail de
  outra conta, a resposta e a pendência são as de uma troca normal, mas o link de
  confirmação não é enviado e o dono desse endereço não é avisado.
- O nome digitado vai em e-mails para endereços não confirmados: o "Olá, {primeiro
  nome}" só usa a primeira palavra quando ela tem apenas letras, apóstrofos e hífens.
- Travas: sempre o usuário antes do link. Quem usa um link descobre o dono sem travar,
  trava o usuário e só então o link, na mesma ordem de quem cria um link novo.

## Alternativas consideradas

- **Limite no PostgreSQL ou no Redis:** sobrevive a reinícios e serve a várias
  instâncias, mas custa uma escrita por tentativa, índice e limpeza, sem ganho com uma
  instância só. A interface permite trocar depois.
- **Captcha (Turnstile ou hCaptcha):** traria script de terceiro, mudança na CSP, um
  item novo na política de privacidade e uma chave de teste no e2e. Reavaliar na Fatia 08.
- **Registro de eventos em JPA:** a entidade do Modulith seria validada pelo
  `ddl-auto: validate`, sem vantagem sobre o JDBC.
- **Enviar o e-mail dentro da transação do pedido:** um SMTP lento seguraria a
  transação, e uma falha no envio desfaria o cadastro.
- **Pôr o valor do link no evento:** o registro guarda o evento em JSON, e o valor
  ficaria no banco até o envio.

## Consequências

- Uma segunda instância da API exige trocar a implementação do limite.
- O e-mail não depende de o SMTP estar no ar na hora do pedido: ele sai quando o SMTP
  voltar, com um link novo.
- O `/actuator/health` não olha o SMTP.
- Renomear um listener deixa órfãs as publicações pendentes dele.
- Todo e-mail novo do sistema segue o mesmo caminho: evento, listener, `EnvioDeEmail`.
- O listener segura a trava do link durante o SMTP: um pedido que invalida esse link
  espera o envio terminar (até os timeouts de 10 s).
- Um proxy na frente do nginx (Fatia 08) faz o `$remote_addr` virar o IP do proxy, e os
  limites por IP passam a valer para todos os clientes juntos. Antes desse deploy, o
  nginx precisa confiar no proxy (`set_real_ip_from` e `real_ip_header`), ou a API
  precisa incluí-lo na lista de proxies internos.
