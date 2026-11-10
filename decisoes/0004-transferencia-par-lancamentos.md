# ADR 0004 — Transferência como par de lançamentos

- **Status:** Aceito; substituído em parte pelo [ADR 0013](0013-lancamentos-e-transferencias.md)
- **Data:** 2026-11-06

## Contexto

Mover R$ 500 da conta corrente para a poupança não é receita nem despesa. Se for
registrado como uma despesa mais uma receita comuns, os dois totais ficam inflados
e os gastos por categoria ficam errados.

Além disso, cada conta tem seu próprio extrato. A transferência aparece nos dois
extratos importados e precisa casar com eles.

## Decisão

- Tabela `transferencias` com `id`, `usuario_id`, `data`, `valor` (positivo),
  `conta_origem_id`, `conta_destino_id` e `descricao`.
- Cada transferência gera **dois lançamentos** com `tipo = TRANSFERENCIA` e o
  mesmo `transferencia_id`:
  - na conta de origem, valor `−X`;
  - na conta de destino, valor `+X`;
  - data igual nos dois e sem categoria.
- Criar, editar e excluir altera a transferência e os dois lançamentos **na mesma
  transação**.
- A conta de origem precisa ser diferente da de destino, e as duas precisam ser do
  mesmo usuário.
- Relatórios de receita, despesa e categoria consideram só os tipos `RECEITA` e
  `DESPESA`. O saldo considera todos os tipos.
- **Importação (Fatia 04):** um lançamento importado pode ser convertido em
  transferência e ligado a um lançamento da outra conta, manual ou importado, com
  o mesmo valor e sinal oposto. A chave de importação dos dois é mantida, para que
  as reimportações continuem reconhecendo os lançamentos.

## Alternativas consideradas

- **Um único lançamento com conta de origem e de destino:** descartado. O saldo de
  uma conta viraria uma soma com dois casos (sou a origem? sou o destino?), o
  extrato de cada conta precisaria de `UNION`, e cada lado importado não teria
  onde casar.
- **Receita + despesa comuns com uma categoria "Transferência":** descartado
  porque infla os totais, e qualquer relatório que esqueça de filtrar a categoria
  fica errado.

## Consequências

- O saldo continua sendo `SUM(valor)` por conta ([ADR 0003](0003-saldo-derivado.md)).
- O extrato de cada conta é uma consulta simples por `conta_id`.
- Excluir um dos lados sozinho é proibido. O serviço sempre opera no par.
- O pagamento de fatura de cartão segue a mesma ideia: é uma saída da conta que
  não é despesa ([ADR 0005](0005-cartao-fatura-parcelas.md)).
