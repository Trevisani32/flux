# ADR 0010 — Base visual do frontend

- **Status:** Aceito
- **Data:** 2026-11-06

## Contexto

A Fatia 01 aplica a identidade visual aprovada no mockup (preto, grafite e
dourado, com a fonte Manrope) e cria a base que as próximas fatias vão usar em
formulários, listas e valores em dinheiro. Sem regras comuns, cada tela
escolheria o próprio jeito de pintar, de montar um campo e de mostrar um valor.
O modo claro, previsto para depois, exigiria mexer em todas elas.

## Decisão

- **Tokens próprios como fonte da verdade.** Cores, cantos, margens, espaços,
  tipografia e sombras ficam em custom properties `--flux-*`
  (`web/src/styles/_tokens.scss`), em cinco blocos: marca, áreas sempre
  escuras, forma e espaço (cantos, margem e a grade de 4 px em
  `--flux-espaco-1` a `--flux-espaco-10`), tipografia
  (`--flux-fonte-titulo-pagina`, `--flux-fonte-titulo-card`,
  `--flux-fonte-rotulo-grupo` e `--flux-fonte-valor`) e tema. Os componentes usam
  só os tokens. O modo claro vai redefinir apenas o bloco de tema.
- **Angular Material com tema M3 próprio.** `mat.theme` com `theme-type: dark`,
  paleta primária gerada a partir do dourado `#D4AF37` pelo schematic
  `@angular/material:theme-color` e neutros a partir de `#71717A`. As variáveis de
  sistema (`--mat-sys-*`) e as dos componentes usados (`mat.*-overrides`) apontam
  para os tokens `--flux-*`.
- **Campos de formulário nativos.** `<label for>` acima do `<input>`, com as
  classes `.flux-campo` e `.flux-entrada`. A mensagem de erro fica ligada ao
  campo por `aria-describedby`, e o campo inválido tem `aria-invalid`. O
  `mat-form-field` não é usado.
- **Ícones em SVG próprio.** O componente `app-icone` desenha traços guardados no
  código e é sempre decorativo (`aria-hidden`). Não há fonte de ícones.
- **Dinheiro na tela.** O pipe `moeda` formata com `Intl.NumberFormat('pt-BR', {
  style: 'currency', currency: 'BRL' })`, usa o sinal de menos tipográfico (−),
  mostra "—" quando não há número e só aceita `number`. O componente `app-valor`
  mostra valores de lançamentos e saldos com sinal e cor: entrada com "+", saída
  com "−", transferência sem sinal e saldo sem "+". O sinal também é escrito por
  extenso para leitores de tela. Totais de despesa em relatórios continuam sem
  sinal ("gastou R$ 50"), como manda o [ADR 0002](0002-representacao-dinheiro.md).
  O frontend continua sem fazer conta com dinheiro.
- **Responsividade.** A quebra é de 960 px. Acima dela a navegação é uma lateral
  fixa; abaixo, uma gaveta sobreposta (`mat-sidenav` no modo `over`).

## Alternativas consideradas

- **`mat-form-field` com aparência `outline`:** o rótulo fica dentro do contorno,
  e o Material não oferece token para o fundo do campo. Reproduzir o desenho
  aprovado exigiria CSS sobre classes internas do componente, frágil a cada
  atualização.
- **Material Symbols ou Material Icons (fonte):** dependem de baixar uma fonte de
  terceiros, mostram o nome do ícone em texto até a fonte carregar e não têm o
  traço fino do desenho aprovado.
- **Biblioteca de ícones (ex.: lucide-angular):** acrescenta uma dependência para
  uma dúzia de ícones, com traços diferentes dos aprovados.
- **`CurrencyPipe` e `DatePipe` do Angular:** o `DatePipe` não aceita fuso IANA
  (`America/Sao_Paulo`), e manter o `Intl` direto deixa a regra igual à do
  ADR 0002.
- **CSS próprio sem Angular Material:** perderia menu, gaveta e snackbar já
  acessíveis (foco, teclado e ARIA), que custariam caro para refazer.

## Consequências

- Mudar uma cor é mudar um token. O modo claro não exige mexer nos componentes.
- Todo formulário novo usa `.flux-campo` e `.flux-entrada`, com `<label for>`, e
  as mensagens de erro seguem o padrão de `aria-describedby`.
- Todo valor com sinal na tela passa pelo `app-valor`; o pipe `moeda` fica para
  valores sem sinal semântico.
- Um ícone novo é um traço novo em `web/src/app/shared/icone/icones.ts`.
- Algumas regras do menu do usuário dependem de classes internas do Angular
  Material (`.mat-mdc-menu-*`) e precisam ser conferidas a cada atualização
  maior do Material.
