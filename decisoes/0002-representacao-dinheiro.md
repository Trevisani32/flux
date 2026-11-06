# ADR 0002 — Representação de dinheiro

- **Status:** Aceito
- **Data:** 2026-11-06

## Contexto

Erros de centavo destroem a confiança num sistema financeiro. `double` não
representa valores como 0,10 com exatidão, e somas de muitos lançamentos acumulam
erro. Também é preciso definir como o sinal (entrada ou saída) é guardado, porque
isso muda todas as somas de saldo e de relatório.

## Decisão

- **Tipo:**
  - no Java, `BigDecimal` com escala 2;
  - no banco, `NUMERIC(19,2)`;
  - nunca `double` nem `float` para dinheiro, em nenhuma camada.
- **Moeda:** só BRL. Não há coluna de moeda. Multimoeda exigiria um ADR novo.
- **Sinal:** o valor é guardado **com sinal, do ponto de vista do usuário**.
  Dinheiro entrando é positivo e dinheiro saindo é negativo. Assim, o saldo de
  qualquer conta é uma soma simples.
  - Despesa: negativa. Receita: positiva.
  - Compra no cartão: negativa. Estorno no cartão: positivo.
  - Transferência: negativa na origem, positiva no destino.
- **API:**
  - na criação, o cliente envia o **valor positivo** e o **tipo**, e o serviço
    aplica o sinal;
  - nas respostas, o valor vem com sinal e o tipo vem junto;
  - no JSON, o valor é um número com até 2 casas (`1234.56`);
  - uma entrada com mais de 2 casas (`10.005`) é recusada com `400`.
- **Arredondamento:** as operações comuns (somar, subtrair) não geram frações de
  centavo. Há duas exceções:
  - divisão de parcelas, com regra própria: truncar e jogar o resto na 1ª parcela
    ([ADR 0005](0005-cartao-fatura-parcelas.md));
  - percentuais de orçamento e de comparação, que servem só para exibição: são
    calculados com `HALF_UP` e 1 casa e nunca são gravados.
- **Frontend:** não faz conta com dinheiro. Totais e saldos vêm prontos da API, e o
  frontend só formata com `Intl.NumberFormat('pt-BR', { currency: 'BRL' })`.

## Alternativas consideradas

- **`long` em centavos:** exato e rápido. Foi descartado porque piora a leitura no
  SQL e na API e espalha conversões ×100 / ÷100 pelo código.
- **Valor sempre positivo + coluna de tipo:** cada soma precisaria de um
  `CASE WHEN`, e o risco de esquecer o sinal em algum relatório é alto.
- **JSON como string (`"1234.56"`):** protege contra perda de precisão em
  JavaScript, mas como o frontend não faz contas o ganho é pequeno, e o contrato
  fica menos natural.

## Consequências

- Saldo = `SUM(valor)`, sem casos especiais.
- Relatórios de despesa precisam inverter o sinal na exibição ("gastou R$ 50", não
  "−50"). Isso fica concentrado no módulo de relatórios.
- A validação de escala acontece na borda da API, com Bean Validation `@Digits`.
