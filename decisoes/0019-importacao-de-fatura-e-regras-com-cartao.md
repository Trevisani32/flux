# ADR 0019 — Importação de fatura e regras com cartão

- **Status:** Aceito. Complementa os ADRs 0014, 0015 e 0017.
- **Data:** 2026-11-23

## Contexto

A importação de extratos recusava faturas de cartão. Compras parceladas importadas precisam continuar identificáveis em
meses seguintes, inclusive quando a divisão deixa centavos diferentes, e regras de categorização ainda não alcançavam
compras e estornos de cartão.

## Decisão

- A V10 permite que uma importação e um perfil CSV pertençam a uma conta ou a um cartão, e guarda a fatura escolhida.
  Compra importada aponta para a importação; lançamentos importados de fatura levam cartão na FK.
- CSV Nubank de fatura troca o sinal: compras positivas viram despesas. OFX de cartão e CSV com perfil são aceitos no
  cartão; extratos de conta continuam no destino conta.
- A chave de parcela `CPAR` usa data, loja, número de parcelas e ordinal, sem valor. Parcela da mesma compra pode diferir
  até N − 1 centavos; uma já importada é corrigida para o valor do banco, salvo se estiver em fatura paga.
- A importação cria compra e parcelas a partir da parcela indicada; desfazer apaga primeiro parcelas e depois compras. A
  compra importada só muda textos e categoria, e sua exclusão é definitiva.
- Cartão com importação, mesmo desfeita, não é vazio para troca de dias. Regras podem filtrar um cartão e categorizam a
  compra inteira, propagando a categoria para todas as parcelas.

## Consequências

Cada fatura importada fica igual ao arquivo recebido, sem duplicar compras parceladas. Uma parcela em fatura paga mantém
o valor anterior e bloqueia desfazer. Excluir um cartão sem compras remove seus perfis, registros de importação e regras.
