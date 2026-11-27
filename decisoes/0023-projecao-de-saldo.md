# ADR 0023 — Projeção de saldo calculada e o módulo projecao

- **Status:** Aceito. Complementa os ADRs 0003, 0017, 0018, 0020 e 0022.
- **Data:** 2026-11-26

## Contexto

O módulo 12 do `produto.md` precisa mostrar o saldo previsto sem gravá-lo. As ocorrências das recorrências e a fatura de
uma data são calculadas em Java (ADRs 0017 e 0022), então essa projeção não cabe no módulo `relatorios`, que lê por SQL.

## Decisão

- A projeção é calculada a cada pedido, sem tabela nem outro dado gravado.
- O módulo `projecao` depende de contas, cartões, lançamentos e recorrências pelas APIs públicas
  `RecorrenciasConsulta` e `LancamentosDaProjecao`; nenhum módulo depende dele.
- A saída de uma fatura é o pendente calculado com as previstas no total. Assim, um pagamento maior que o total ou uma
  fatura paga fora do Flux também absorve as compras previstas.
- A fatura vencida com pendente, a recorrência atrasada e a prevista de hoje entram nas pendências de hoje.
- A seleção de contas e o período ficam no endereço. A API devolve somente dias com movimento, já com o saldo depois
  dele.
- O gráfico é de degraus, desenhado pelo próprio Flux, sem biblioteca externa.
- Cada pedido usa um número fixo de consultas, no máximo 14; um teste de integração exige a quantidade exata.

## Alternativas consideradas

- Colocar uma aba em Relatórios.
- Usar um ponto por mês no gráfico de linha da 06a.
- Calcular a saída como `pendente + previsto`.
- Deixar as pendências fora da projeção, mostrando apenas um aviso.

## Consequências

O tempo da projeção não cresce com a quantidade de recorrências, e o teste conta as consultas. A projeção depende do
cálculo das ocorrências da 07a, que passou a ler os dados necessários uma vez. A 07d pode usar a mesma
`RecorrenciasConsulta` para o previsto do orçamento.
