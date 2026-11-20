package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.cartoes.CalendarioDoCartao;
import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.CartoesConsulta;
import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import com.flux.lancamentos.LancamentosImportados;
import com.flux.lancamentos.interno.dto.FaturasParaPagamentoResponse;
import com.flux.lancamentos.interno.dto.ItemDaLista;
import com.flux.lancamentos.interno.dto.PagamentoResponse;
import com.flux.lancamentos.interno.dto.SalvarPagamentoRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * O pagamento da fatura (Fatia 05b, decisões 30 a 33 e 37, ADR 0018): um lançamento PAGAMENTO_FATURA, negativo, na
 * conta, ligado à fatura que paga. Fica fora de receitas e despesas e entra no saldo da conta. Lançar, editar, excluir,
 * converter a despesa importada e desfazer a conversão pegam a trava de contas e depois a de cartões (decisão 49).
 */
@Service
public class PagamentoService {

    /** Decisão 37: a fatura sugerida vence a até 5 dias da data da despesa. */
    static final int DIAS_DA_SUGESTAO = 5;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_E_MES = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter MES_E_ANO = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR);
    private static final String CONTA_QUE_PAGA = "Escolha uma conta corrente, poupança ou carteira.";
    private static final String FATURA_QUE_SERVE = "Escolha uma fatura deste cartão que já fechou ou está aberta.";

    private final LancamentoRepository lancamentos;
    private final FaturaRepository faturas;
    private final RegrasDosLancamentos regras;
    private final ContasConsulta contas;
    private final CartoesConsulta cartoes;
    private final ListaDosLancamentos lista;

    public PagamentoService(LancamentoRepository lancamentos, FaturaRepository faturas, RegrasDosLancamentos regras, ContasConsulta contas,
                            CartoesConsulta cartoes, ListaDosLancamentos lista) {
        this.lancamentos = lancamentos;
        this.faturas = faturas;
        this.regras = regras;
        this.contas = contas;
        this.cartoes = cartoes;
        this.lista = lista;
    }

    /**
     * Decisão 30: "Pagar fatura". A fatura aberta (pagamento antecipado) e as que já fecharam recebem pagamento; a
     * futura, não. O valor pode ser parcial ou passar do pendente (decisão 31).
     */
    @Transactional
    public PagamentoResponse criar(Long usuarioId, Long cartaoId, String mes, SalvarPagamentoRequest pedido) {
        YearMonth mesDaFatura = FaturaService.mesValido(mes);
        exigirDataAteHoje(pedido.data());
        regras.travarContasECartoes(usuarioId);
        CartaoResumo cartao = cartoes.buscar(usuarioId, cartaoId).orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado."));
        exigirCartaoAtivo(cartao, "pagar a fatura");
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        if (doCartao.situacao(mesDaFatura, regras.hoje()) == CalendarioDoCartao.Situacao.FUTURA) {
            throw new RegraDeNegocioException("A fatura de " + nomeDoMes(mesDaFatura)
                    + " ainda não começou: pague a aberta ou uma que já fechou.", "FATURA_FUTURA");
        }
        ContaResumo conta = contaQuePaga(usuarioId, pedido.contaId());
        regras.exigirDataDesdeOSaldoInicial(pedido.data(), List.of(conta));
        regras.exigirLimiteTotal(usuarioId, 1);
        regras.exigirLimiteDoMes(usuarioId, pedido.data(), 1);
        Instant agora = regras.agora();
        Fatura fatura = doCartao.obter(faturas, mesDaFatura, agora);
        Lancamento pagamento = Lancamento.pagamento(usuarioId, conta.id(), fatura.getId(), pedido.data(), pedido.valor().negate(),
                "Pagamento da fatura de " + nomeDoMes(mesDaFatura), pedido.observacao(), agora);
        lancamentos.save(pagamento);
        return resposta(usuarioId, pagamento);
    }

    @Transactional(readOnly = true)
    public PagamentoResponse buscar(Long usuarioId, Long id) {
        return resposta(usuarioId, encontrar(usuarioId, id));
    }

    /**
     * Decisão 33: conta, data, valor e observação. No pagamento importado (decisão 37), conta, data e valor vêm do
     * extrato e não mudam; só a observação muda. Com a conta ou o cartão arquivado, só leitura.
     */
    @Transactional
    public PagamentoResponse atualizar(Long usuarioId, Long id, SalvarPagamentoRequest pedido) {
        regras.travarContasECartoes(usuarioId);
        Lancamento pagamento = encontrar(usuarioId, id);
        exigirAtivos(usuarioId, pagamento, "editar ou excluir este pagamento");
        if (pagamento.importado()) {
            if (!pedido.contaId().equals(pagamento.getContaId()) || !pedido.data().equals(pagamento.getData())
                    || pedido.valor().compareTo(pagamento.getValor().negate()) != 0) {
                throw new RegraDeNegocioException("Conta, data e valor de um pagamento importado não mudam.", "IMPORTADO_TRAVADO");
            }
            pagamento.alterarObservacaoDoPagamento(pedido.observacao(), regras.agora());
            return resposta(usuarioId, pagamento);
        }
        exigirDataAteHoje(pedido.data());
        ContaResumo conta = pedido.contaId().equals(pagamento.getContaId())
                ? contas.buscar(usuarioId, pedido.contaId()).orElseThrow() : contaQuePaga(usuarioId, pedido.contaId());
        regras.exigirDataDesdeOSaldoInicial(pedido.data(), List.of(conta));
        if (!YearMonth.from(pedido.data()).equals(YearMonth.from(pagamento.getData()))) {
            regras.exigirLimiteDoMes(usuarioId, pedido.data(), 1);
        }
        pagamento.alterarPagamento(conta.id(), pedido.data(), pedido.valor().negate(), pedido.observacao(), regras.agora());
        return resposta(usuarioId, pagamento);
    }

    /** Decisão 33: o pagamento lançado à mão sai, e a fatura volta a ter o pendente; o importado se desfaz. */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        regras.travarContasECartoes(usuarioId);
        Lancamento pagamento = encontrar(usuarioId, id);
        exigirAtivos(usuarioId, pagamento, "editar ou excluir este pagamento");
        if (pagamento.importado()) {
            throw new RegraDeNegocioException("Este pagamento veio do extrato: desfaça a conversão para ele voltar a ser despesa.",
                    "PAGAMENTO_IMPORTADO");
        }
        lancamentos.delete(pagamento);
    }

    /**
     * Decisão 37: o pagamento convertido volta a ser a despesa importada, sem categoria, com a data, o valor, a conta, a
     * descrição e a chave de antes. O lançado à mão não se desfaz: ele se exclui.
     */
    @Transactional
    public ItemDaLista desfazer(Long usuarioId, Long id) {
        regras.travarContasECartoes(usuarioId);
        Lancamento pagamento = encontrar(usuarioId, id);
        if (!pagamento.importado()) {
            throw new RegraDeNegocioException("Este pagamento foi lançado à mão: para tirá-lo, exclua o pagamento.", "PAGAMENTO_MANUAL");
        }
        exigirAtivos(usuarioId, pagamento, "desfazer este pagamento");
        pagamento.desfazerPagamento(regras.agora());
        return lista.itemDoLancamento(usuarioId, pagamento);
    }

    /**
     * Decisão 37: a despesa importada e, por cartão ativo, as faturas que ela pode pagar: as que já fecharam ou estão
     * abertas, dos dois meses antes ao mês depois da data dela. Vêm primeiro as sugeridas (fechadas, com o pendente igual ao
     * valor e o vencimento a até 5 dias da data); depois, o vencimento mais perto da data.
     */
    @Transactional(readOnly = true)
    public FaturasParaPagamentoResponse faturasParaPagamento(Long usuarioId, Long lancamentoId) {
        Lancamento despesa = encontrarDespesa(usuarioId, lancamentoId);
        exigirQueConverte(usuarioId, despesa);
        LocalDate hoje = regras.hoje();
        BigDecimal valor = despesa.getValor().negate();
        List<FaturasParaPagamentoResponse.Cartao> porCartao = new ArrayList<>();
        Map<Long, List<Fatura>> gravadas = faturas.findAllByUsuarioId(usuarioId).stream().collect(Collectors.groupingBy(Fatura::getCartaoId));
        List<TotalDaFatura> totais = lancamentos.totaisDasFaturas(usuarioId);
        List<PagoDaFatura> pagos = lancamentos.pagosDasFaturas(usuarioId);
        List<CartaoResumo> ativos = cartoes.listar(usuarioId).stream().filter(cartao -> !cartao.arquivado())
                .sorted(Comparator.comparing(CartaoResumo::nome, TextoComparavel.ordemAlfabetica()).thenComparing(CartaoResumo::id)).toList();
        for (CartaoResumo cartao : ativos) {
            FaturasDoCartao doCartao = new FaturasDoCartao(usuarioId, cartao, gravadas.getOrDefault(cartao.id(), List.of()));
            PagamentosDasFaturas pagamentos = new PagamentosDasFaturas(doCartao, totais, pagos);
            List<FaturasParaPagamentoResponse.Fatura> opcoes = new ArrayList<>();
            YearMonth mesDaData = YearMonth.from(despesa.getData());
            for (YearMonth mes = mesDaData.minusMonths(2); !mes.isAfter(mesDaData.plusMonths(1)); mes = mes.plusMonths(1)) {
                PagamentosDasFaturas.Resumo resumo = pagamentos.resumo(mes, hoje);
                if (resumo.situacao() == CalendarioDoCartao.Situacao.FUTURA) {
                    continue;
                }
                LocalDate vencimento = doCartao.datas(mes).vencimento();
                boolean sugerida = resumo.situacao() == CalendarioDoCartao.Situacao.FECHADA && resumo.pendente().compareTo(valor) == 0
                        && Math.abs(ChronoUnit.DAYS.between(despesa.getData(), vencimento)) <= DIAS_DA_SUGESTAO;
                opcoes.add(new FaturasParaPagamentoResponse.Fatura(mes.toString(), resumo.situacao().name(), vencimento, resumo.total(),
                        resumo.pendente(), sugerida));
            }
            opcoes.sort(Comparator.comparing((FaturasParaPagamentoResponse.Fatura fatura) -> !fatura.sugerida())
                    .thenComparingLong(fatura -> Math.abs(ChronoUnit.DAYS.between(despesa.getData(), fatura.vencimento())))
                    .thenComparing(FaturasParaPagamentoResponse.Fatura::mes));
            porCartao.add(new FaturasParaPagamentoResponse.Cartao(cartao.id(), cartao.nome(), cartao.cor(), opcoes));
        }
        return new FaturasParaPagamentoResponse(lista.itemDoLancamento(usuarioId, despesa), porCartao);
    }

    /**
     * Decisão 37: a despesa importada vira o pagamento da fatura escolhida, que já fechou ou está aberta. Perde a
     * categoria; data, valor, conta, descrição e chave ficam (reimportar o extrato continua dando "já importada").
     */
    @Transactional
    public PagamentoResponse converter(Long usuarioId, Long lancamentoId, Long cartaoId, String mes) {
        YearMonth mesDaFatura;
        try {
            mesDaFatura = FaturaService.mesValido(mes);
        } catch (CampoInvalidoException ex) {
            throw new RegraDeNegocioException(FATURA_QUE_SERVE, "FATURA_INVALIDA").com("campo", "mes");
        }
        regras.travarContasECartoes(usuarioId);
        Lancamento despesa = encontrarDespesa(usuarioId, lancamentoId);
        exigirQueConverte(usuarioId, despesa);
        CartaoResumo cartao = cartoes.buscar(usuarioId, cartaoId)
                .orElseThrow(() -> new RegraDeNegocioException("Cartão não encontrado.", "CARTAO_INVALIDO").com("campo", "cartaoId"));
        exigirCartaoAtivo(cartao, "pagar a fatura");
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        if (doCartao.situacao(mesDaFatura, regras.hoje()) == CalendarioDoCartao.Situacao.FUTURA) {
            throw new RegraDeNegocioException(FATURA_QUE_SERVE, "FATURA_INVALIDA").com("campo", "mes");
        }
        Instant agora = regras.agora();
        Fatura fatura = doCartao.obter(faturas, mesDaFatura, agora);
        despesa.converterEmPagamento(fatura.getId(), agora);
        return resposta(usuarioId, despesa);
    }

    /**
     * Decisões 33 e 37: desfazer uma importação apaga também o pagamento convertido de uma despesa dela, e a fatura volta a
     * ter o pendente. Com o cartão dele arquivado, o desfazer inteiro é recusado, como o da conversão em transferência com a
     * outra conta arquivada (Fatia 04c). Roda na transação e na trava de contas do desfazer; pega a de cartões.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void desfazerDaImportacao(Long usuarioId, Long importacaoId) {
        List<Lancamento> pagamentos = lancamentos.findAllByUsuarioIdAndImportacaoIdAndFaturaPagaIdIsNotNull(usuarioId, importacaoId);
        if (pagamentos.isEmpty()) {
            return;
        }
        cartoes.travar(usuarioId);
        Map<Long, CartaoResumo> doUsuario = cartoes.listar(usuarioId).stream().collect(Collectors.toMap(CartaoResumo::id, Function.identity()));
        Map<Long, Fatura> pagas = faturas.findAllByUsuarioIdAndIdIn(usuarioId, pagamentos.stream().map(Lancamento::getFaturaPagaId).toList())
                .stream().collect(Collectors.toMap(Fatura::getId, Function.identity()));
        pagamentos.stream().sorted(Comparator.comparing(Lancamento::getData).thenComparing(Lancamento::getId))
                .filter(pagamento -> doUsuario.get(pagas.get(pagamento.getFaturaPagaId()).getCartaoId()).arquivado())
                .findFirst()
                .ifPresent(pagamento -> {
                    throw new RegraDeNegocioException("O pagamento de " + pagamento.getData().format(DIA_E_MES) + " liga esta importação a "
                            + doUsuario.get(pagas.get(pagamento.getFaturaPagaId()).getCartaoId()).nome()
                            + ", que está arquivado. Reative o cartão para desfazer esta importação.", "CARTAO_ARQUIVADO");
                });
    }

    /**
     * Fatia 05b (dono, 2026-11-20): os pagamentos convertidos de despesas importadas, de todas as importações do usuário,
     * com o cartão e o mês da fatura, para o histórico avisar antes do desfazer e bloquear com o cartão arquivado.
     */
    @Transactional(readOnly = true)
    public List<LancamentosImportados.PagamentoConvertido> convertidosDasImportacoes(Long usuarioId) {
        List<Lancamento> convertidos =
                lancamentos.findAllByUsuarioIdAndImportacaoIdIsNotNullAndFaturaPagaIdIsNotNullOrderByDataAscIdAsc(usuarioId);
        if (convertidos.isEmpty()) {
            return List.of();
        }
        Map<Long, CartaoResumo> doUsuario = cartoes.listar(usuarioId).stream().collect(Collectors.toMap(CartaoResumo::id, Function.identity()));
        Map<Long, Fatura> pagas = faturas.findAllByUsuarioIdAndIdIn(usuarioId, convertidos.stream().map(Lancamento::getFaturaPagaId).toList())
                .stream().collect(Collectors.toMap(Fatura::getId, Function.identity()));
        return convertidos.stream().map(pagamento -> {
            Fatura fatura = pagas.get(pagamento.getFaturaPagaId());
            CartaoResumo cartao = doUsuario.get(fatura.getCartaoId());
            return new LancamentosImportados.PagamentoConvertido(pagamento.getImportacaoId(), pagamento.getData(), cartao.id(), cartao.nome(),
                    cartao.arquivado(), fatura.mes().toString());
        }).toList();
    }

    private Lancamento encontrar(Long usuarioId, Long id) {
        return lancamentos.findByIdAndUsuarioIdAndFaturaPagaIdIsNotNull(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento não encontrado."));
    }

    /** Lançamento de conta, não excluído; o de fatura e o ignorado são um 404, como no resto de /api/lancamentos. */
    private Lancamento encontrarDespesa(Long usuarioId, Long id) {
        return lancamentos.findByIdAndUsuarioIdAndFaturaIdIsNullAndIgnoradoEmIsNull(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Lançamento não encontrado."));
    }

    /** Decisão 37: só a despesa importada, fora de transferência, numa conta ativa que paga fatura e com data até hoje. */
    private void exigirQueConverte(Long usuarioId, Lancamento despesa) {
        if (despesa.pagamento()) {
            throw new RegraDeNegocioException("Este lançamento já é um pagamento de fatura.", "JA_E_PAGAMENTO");
        }
        if (despesa.transferencia()) {
            throw new RegraDeNegocioException("Este lançamento já está numa transferência.", "JA_EM_TRANSFERENCIA");
        }
        if (!despesa.importado()) {
            throw new RegraDeNegocioException("Só um lançamento importado vira pagamento de fatura.", "SO_IMPORTADO");
        }
        if (despesa.getTipo() != TipoLancamento.DESPESA) {
            throw new RegraDeNegocioException("Só uma despesa importada vira pagamento de fatura.", "SO_DESPESA");
        }
        ContaResumo conta = regras.exigirContaAtivaPara(usuarioId, despesa.getContaId(), "converter este lançamento");
        if ("INVESTIMENTO".equals(conta.tipo())) {
            throw new RegraDeNegocioException("Pagamento de fatura sai de uma conta corrente, poupança ou carteira.", "CONTA_INVALIDA");
        }
        if (despesa.getData().isAfter(regras.hoje())) {
            throw new RegraDeNegocioException("Este lançamento tem data futura: ele só vira pagamento de fatura quando a data chegar.",
                    "DATA_FUTURA");
        }
    }

    /** Decisões 4 e 30: a conta do usuário, ativa, corrente, poupança ou carteira (422 com o campo da conta). */
    private ContaResumo contaQuePaga(Long usuarioId, Long contaId) {
        ContaResumo conta = contas.buscar(usuarioId, contaId)
                .orElseThrow(() -> new RegraDeNegocioException("Conta não encontrada.", "CONTA_INVALIDA").com("campo", "contaId"));
        if (conta.arquivada()) {
            throw new RegraDeNegocioException(conta.nome() + " está arquivada. Escolha uma conta ativa.", "CONTA_INVALIDA")
                    .com("campo", "contaId");
        }
        if ("INVESTIMENTO".equals(conta.tipo())) {
            throw new RegraDeNegocioException(CONTA_QUE_PAGA, "CONTA_INVALIDA").com("campo", "contaId");
        }
        return conta;
    }

    /** Decisão 30: a data vai até hoje ("A data pode ir até hoje, 12/11/2026."). */
    private void exigirDataAteHoje(LocalDate data) {
        LocalDate hoje = regras.hoje();
        if (data.isAfter(hoje)) {
            throw new CampoInvalidoException("data", "A data pode ir até hoje, " + hoje.format(DATA_CURTA) + ".");
        }
    }

    /** Decisão 33: com a conta ou o cartão arquivado, o pagamento fica só para leitura. */
    private void exigirAtivos(Long usuarioId, Lancamento pagamento, String acao) {
        regras.exigirContaAtivaPara(usuarioId, pagamento.getContaId(), acao);
        Fatura fatura = faturas.findByIdAndUsuarioId(pagamento.getFaturaPagaId(), usuarioId).orElseThrow();
        exigirCartaoAtivo(cartoes.buscar(usuarioId, fatura.getCartaoId()).orElseThrow(), acao);
    }

    private static void exigirCartaoAtivo(CartaoResumo cartao, String acao) {
        if (cartao.arquivado()) {
            throw new RegraDeNegocioException(cartao.nome() + " está arquivado. Reative o cartão para " + acao + ".", "CARTAO_ARQUIVADO");
        }
    }

    private PagamentoResponse resposta(Long usuarioId, Lancamento pagamento) {
        Fatura fatura = faturas.findByIdAndUsuarioId(pagamento.getFaturaPagaId(), usuarioId).orElseThrow();
        CartaoResumo cartao = cartoes.buscar(usuarioId, fatura.getCartaoId()).orElseThrow();
        Map<Long, ContaResumo> doUsuario = contas.listar(usuarioId).stream().collect(Collectors.toMap(ContaResumo::id, Function.identity()));
        ContaResumo conta = doUsuario.get(pagamento.getContaId());
        return new PagamentoResponse(pagamento.getId(), cartao.id(), cartao.nome(), cartao.arquivado(), fatura.mes().toString(),
                fatura.getDataVencimento(), conta.id(), conta.nome(), conta.arquivada(), pagamento.getData(), pagamento.getValor().negate(),
                pagamento.getObservacao(), pagamento.getOrigem(), pagamento.getDescricaoBanco(), conta.arquivada() || cartao.arquivado());
    }

    static String nomeDoMes(YearMonth mes) {
        return mes.atDay(1).format(MES_E_ANO);
    }
}
