package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.flux.cartoes.CalendarioDoCartao;
import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.CartoesConsulta;
import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.lancamentos.interno.dto.CompraResponse;
import com.flux.lancamentos.interno.dto.ParcelaDaCompra;
import com.flux.lancamentos.interno.dto.PreviaDaCompraRequest;
import com.flux.lancamentos.interno.dto.PreviaDaCompraResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compras e estornos no cartão (decisões 14 a 21, ADR 0017). Cada parcela é um lançamento na fatura certa:
 * a 1ª na fatura da data da compra, a k-ésima k − 1 faturas depois. O estorno é uma receita só, na fatura da
 * data dele. Gravar pega a trava de cartões e depois a de categorias (decisão 49). Desde a Fatia 05b (decisão 35), a
 * compra com alguma parcela numa fatura paga só muda a descrição, a categoria e a observação, e não se exclui.
 */
@Service
public class CompraService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Decisão 16: a data da compra e a do estorno vão de 10 anos antes de hoje (inclusive) a 12 meses depois. */
    static final int ANOS_PARA_TRAS = 10;

    private final CompraRepository compras;
    private final LancamentoRepository lancamentos;
    private final FaturaRepository faturas;
    private final RegrasDosLancamentos regras;
    private final CartoesConsulta cartoes;
    private final CategoriasConsulta categorias;

    public CompraService(CompraRepository compras, LancamentoRepository lancamentos, FaturaRepository faturas,
                         RegrasDosLancamentos regras, CartoesConsulta cartoes, CategoriasConsulta categorias) {
        this.compras = compras;
        this.lancamentos = lancamentos;
        this.faturas = faturas;
        this.regras = regras;
        this.cartoes = cartoes;
        this.categorias = categorias;
    }

    /** O que define as parcelas; {@code valorTotal} positivo. No estorno, uma parcela só. */
    public record Dados(TipoCompra tipo, Long cartaoId, LocalDate data, String descricao, BigDecimal valorTotal, int parcelas,
                        int parcelaInicial, Long categoriaId, String observacao) {
    }

    /** Decisão 18: a divisão e a fatura de cada parcela, sem gravar nada (as faturas que não existem saem pelas datas). */
    @Transactional(readOnly = true)
    public PreviaDaCompraResponse previa(Long usuarioId, PreviaDaCompraRequest pedido) {
        exigirDataEParcelas(pedido.data(), pedido.valorTotal(), pedido.parcelas(), pedido.parcelaInicial());
        CartaoResumo cartao = cartoes.buscar(usuarioId, pedido.cartaoId())
                .orElseThrow(() -> new RegraDeNegocioException("Cartão não encontrado."));
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        YearMonth primeira = doCartao.faturaDe(pedido.data());
        List<BigDecimal> valores = Parcelamento.dividir(pedido.valorTotal(), pedido.parcelas());
        List<ParcelaDaCompra> parcelas = new ArrayList<>();
        for (int numero = 1; numero <= pedido.parcelas(); numero++) {
            YearMonth mes = primeira.plusMonths(numero - 1L);
            parcelas.add(new ParcelaDaCompra(numero, mes.toString(), doCartao.datas(mes).vencimento(), valores.get(numero - 1),
                    numero >= pedido.parcelaInicial()));
        }
        CalendarioDoCartao.Datas datas = doCartao.datas(primeira);
        // Decisão 38: o disponível com as parcelas no Flux somadas às faturas delas (o estorno abate).
        boolean estorno = pedido.tipo() == TipoCompra.ESTORNO;
        Map<YearMonth, BigDecimal> acrescimos = new TreeMap<>();
        parcelas.stream().filter(ParcelaDaCompra::noFlux).forEach(parcela -> acrescimos.put(YearMonth.parse(parcela.mes()),
                estorno ? parcela.valor().negate() : parcela.valor()));
        // Na edição (dono, 2026-11-20), a prévia desconta as parcelas que a compra ou o estorno já tem neste cartão: o que muda
        // em cada fatura é o novo menos o atual.
        boolean edicao = pedido.compraId() != null;
        Map<YearMonth, BigDecimal> deltas = new TreeMap<>(acrescimos);
        if (edicao) {
            Compra editada = compras.findByIdAndUsuarioIdAndTipo(pedido.compraId(), usuarioId, pedido.tipo())
                    .orElseThrow(() -> new RegraDeNegocioException(estorno ? "Estorno não encontrado." : "Compra não encontrada."));
            if (editada.getCartaoId().equals(cartao.id())) {
                lancamentos.findAllByUsuarioIdAndCompraIdInOrderByCompraIdAscNumeroParcelaAsc(usuarioId, List.of(editada.getId()))
                        .forEach(atual -> deltas.merge(YearMonth.from(atual.getData()), atual.getValor(), BigDecimal::add));
            }
        }
        PagamentosDasFaturas pagamentos = PagamentosDasFaturas.carregar(lancamentos, usuarioId, doCartao);
        BigDecimal pendente = pagamentos.pendenteDeTodasCom(deltas);
        LocalDate hoje = regras.hoje();
        // Decisão 35 (dono, 2026-11-20): a parcela que, lançada, fica numa fatura paga entra travada, e a prévia avisa. Na
        // edição, a fatura que já está paga é a da trava (a compra já está nela) ou a da recusa da edição, que o PUT diz.
        String aviso = null;
        for (ParcelaDaCompra parcela : parcelas) {
            YearMonth mes = YearMonth.parse(parcela.mes());
            boolean jaPaga = pagamentos.paga(mes, hoje);
            if (parcela.noFlux() && !(edicao && jaPaga) && pagamentos.ficaPagaCom(mes, hoje, deltas.get(mes))) {
                aviso = avisoDaFaturaPaga(estorno, edicao, pedido.parcelas(), parcela.numero(), mes, hoje, jaPaga,
                        pagamentos.resumo(mes, hoje).pagaFora());
                break;
            }
        }
        return new PreviaDaCompraResponse(parcelas, new PreviaDaCompraResponse.Fatura(primeira.toString(), datas.fechamento(),
                datas.vencimento(), doCartao.situacao(primeira, hoje).name()),
                cartao.limite().subtract(pendente).setScale(2, RoundingMode.UNNECESSARY), aviso);
    }

    /**
     * Decisão 35, antes de salvar (dono, 2026-11-20): "A compra vai para a fatura de setembro, que já foi paga fora do Flux:
     * ela entra travada, e só a descrição, a categoria e a observação vão mudar. Para corrigir um engano, desmarque a
     * fatura." Com pagamento, "que já foi paga" e "exclua ou edite o pagamento dela"; parcelada, "A parcela 2/3 vai… a compra
     * entra travada…"; o estorno, "O estorno vai… ele entra travado…". Na fatura que passa a ser paga pelo lançamento, "que
     * fica paga com ele"; na edição, "A compra vai ficar na fatura de setembro, que fica paga com ela: ela fica travada…".
     */
    private static String avisoDaFaturaPaga(boolean estorno, boolean edicao, int parcelas, int numero, YearMonth mes, LocalDate hoje,
                                            boolean jaPaga, boolean pagaFora) {
        String quem = estorno ? "O estorno" : parcelas == 1 ? "A compra" : "A parcela " + numero + "/" + parcelas;
        String travado = (estorno ? "ele " : parcelas == 1 ? "ela " : "a compra ") + (edicao ? "fica" : "entra")
                + (estorno ? " travado" : " travada");
        return quem + (edicao ? " vai ficar na fatura de " : " vai para a fatura de ") + FaturaService.nomeDoMes(mes, hoje)
                + (jaPaga ? ", que já foi paga" + (pagaFora ? " fora do Flux" : "") : ", que fica paga com " + (estorno ? "ele" : "ela"))
                + ": " + travado + ", e só a descrição, a categoria e a observação vão mudar. "
                + (pagaFora ? "Para corrigir um engano, desmarque a fatura." : "Para corrigir um engano, exclua ou edite o pagamento dela.");
    }

    @Transactional(readOnly = true)
    public CompraResponse buscar(Long usuarioId, Long id, TipoCompra tipo) {
        return resposta(usuarioId, encontrar(usuarioId, id, tipo));
    }

    @Transactional
    public CompraResponse criar(Long usuarioId, Dados dados) {
        exigirDataEParcelas(dados.data(), dados.valorTotal(), dados.parcelas(), dados.parcelaInicial());
        regras.travarCartoesECategorias(usuarioId);
        CartaoResumo cartao = regras.cartaoParaLancar(usuarioId, dados.cartaoId());
        if (dados.categoriaId() != null) {
            regras.exigirCategoria(usuarioId, dados.categoriaId(), natureza(dados.tipo()), null);
        }
        Compra compra = new Compra(usuarioId, dados.tipo(), regras.agora());
        compra.alterar(cartao.id(), dados.data(), dados.valorTotal(), dados.parcelas(), dados.parcelaInicial(), regras.agora());
        compras.save(compra);
        gravarParcelas(usuarioId, compra, cartao, dados, null);
        return resposta(usuarioId, compra);
    }

    /**
     * Decisão 19: mudar cartão, data, valor, parcelas ou a primeira parcela regrava as parcelas (apaga e grava de
     * novo, conferindo os limites só pelo que entra); descrição, categoria e observação só atualizam as que existem.
     * A faixa de datas só vale quando a data muda.
     */
    @Transactional
    public CompraResponse atualizar(Long usuarioId, Long id, Dados dados) {
        exigirParcelas(dados.valorTotal(), dados.parcelas(), dados.parcelaInicial());
        regras.travarCartoesECategorias(usuarioId);
        Compra compra = encontrar(usuarioId, id, dados.tipo());
        // Decisão 16: a faixa vale para a data que se lança; a que a compra já tem fica, mesmo com mais de 10 anos.
        if (!compra.getDataCompra().equals(dados.data())) {
            exigirFaixaDeDatas(dados.data());
        }
        regras.exigirCartaoAtivoPara(usuarioId, compra.getCartaoId(), "editar ou excluir " + oQue(dados.tipo()));
        CartaoResumo cartao = regras.cartaoParaLancar(usuarioId, dados.cartaoId());
        List<Lancamento> atuais = lancamentos.findAllByUsuarioIdAndCompraIdInOrderByCompraIdAscNumeroParcelaAsc(usuarioId, List.of(id));
        if (dados.categoriaId() != null) {
            regras.exigirCategoria(usuarioId, dados.categoriaId(), natureza(dados.tipo()), atuais.getFirst().getCategoriaId());
        }
        boolean regravar = !compra.getCartaoId().equals(cartao.id()) || !compra.getDataCompra().equals(dados.data())
                || compra.getValorTotal().compareTo(dados.valorTotal()) != 0 || compra.getParcelas() != dados.parcelas()
                || compra.getParcelaInicial() != dados.parcelaInicial();
        Instant agora = regras.agora();
        if (!regravar) {
            atuais.forEach(parcela -> parcela.alterarTextosDaFatura(dados.descricao(), dados.categoriaId(), dados.observacao(), agora));
            return resposta(usuarioId, compra);
        }
        if (compra.importada()) {
            String quem = compra.estorno() ? "Este estorno" : "Esta compra";
            throw new RegraDeNegocioException(quem + " veio de uma fatura importada: só a descrição, a categoria e a observação mudam.",
                    "IMPORTADA");
        }
        // Decisão 35: com uma parcela em fatura paga, só os textos mudam.
        Optional<String> trava = travaDaFaturaPaga(usuarioId, compra, atuais);
        if (trava.isPresent()) {
            throw new RegraDeNegocioException(trava.get(), "FATURA_PAGA");
        }
        // Decisão 7: com a data e o cartão iguais, a 1ª parcela fica no mês em que está, sem a fatura ser buscada pela
        // data de novo. Depois de uma troca de dias, o mês da data pode não ter gravados os dias de antes (a compra em
        // andamento não grava a fatura das parcelas de antes do Flux), e a busca usaria os dias novos.
        Lancamento primeiraNoFlux = atuais.getFirst();
        YearMonth mesDaPrimeira = compra.getCartaoId().equals(cartao.id()) && compra.getDataCompra().equals(dados.data())
                ? YearMonth.from(primeiraNoFlux.getData()).minusMonths(primeiraNoFlux.getNumeroParcela() - 1L) : null;
        exigirForaDasPagas(usuarioId, cartao, dados, mesDaPrimeira);
        // O delete limpa o contexto de persistência: a compra volta a ser gravada pelo save.
        lancamentos.apagarDaCompra(usuarioId, id);
        compra.alterar(cartao.id(), dados.data(), dados.valorTotal(), dados.parcelas(), dados.parcelaInicial(), agora);
        Compra gravada = compras.saveAndFlush(compra);
        gravarParcelas(usuarioId, gravada, cartao, dados, mesDaPrimeira);
        return resposta(usuarioId, gravada);
    }

    /**
     * Decisão 35 (dono, 2026-11-20): a edição que regrava as parcelas não leva nenhuma para uma fatura paga (ou marcada
     * como paga fora do Flux), pela data, pelo cartão ou pela primeira parcela, como o ajuste (decisão 12). Para lançar
     * nela, o usuário exclui e lança de novo: a compra nova entra travada, com o aviso da prévia.
     */
    private void exigirForaDasPagas(Long usuarioId, CartaoResumo cartao, Dados dados, YearMonth mesDaPrimeira) {
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        YearMonth primeira = mesDaPrimeira != null ? mesDaPrimeira : doCartao.faturaDe(dados.data());
        PagamentosDasFaturas pagamentos = PagamentosDasFaturas.carregar(lancamentos, usuarioId, doCartao);
        LocalDate hoje = regras.hoje();
        for (int numero = dados.parcelaInicial(); numero <= dados.parcelas(); numero++) {
            YearMonth mes = primeira.plusMonths(numero - 1L);
            if (pagamentos.paga(mes, hoje)) {
                boolean estorno = dados.tipo() == TipoCompra.ESTORNO;
                String oQue = estorno ? "o estorno" : dados.parcelas() == 1 ? "a compra" : "a parcela " + numero + "/" + dados.parcelas();
                throw new RegraDeNegocioException("A edição levaria " + oQue + " para a fatura de " + FaturaService.nomeDoMes(mes, hoje)
                        + ", que já foi paga. " + (estorno ? "Para lançá-lo nessa fatura, exclua este estorno e lance de novo."
                        : dados.parcelas() == 1 ? "Para lançá-la nessa fatura, exclua esta compra e lance de novo."
                        : "Para lançar a compra nessa fatura, exclua esta e lance de novo."), "FATURA_PAGA");
            }
        }
    }

    /** Decisão 19: as parcelas saem junto; as faturas ficam (decisão 9). */
    @Transactional
    public void excluir(Long usuarioId, Long id, TipoCompra tipo) {
        regras.travarCartoesECategorias(usuarioId);
        Compra compra = encontrar(usuarioId, id, tipo);
        regras.exigirCartaoAtivoPara(usuarioId, compra.getCartaoId(), "editar ou excluir " + oQue(tipo));
        // Decisão 35: com uma parcela em fatura paga, a compra não se exclui.
        Optional<String> trava = travaDaFaturaPaga(usuarioId, compra,
                lancamentos.findAllByUsuarioIdAndCompraIdInOrderByCompraIdAscNumeroParcelaAsc(usuarioId, List.of(id)));
        if (trava.isPresent()) {
            throw new RegraDeNegocioException(trava.get(), "FATURA_PAGA");
        }
        lancamentos.apagarDaCompra(usuarioId, id);
        compras.delete(compra);
    }

    /**
     * Decisão 35: a primeira parcela (pelo número) que está numa fatura paga, dita na frase da trava: "A parcela 1/3 está
     * na fatura de novembro, que já foi paga: só a descrição, a categoria e a observação mudam." (à vista, "A compra está…";
     * o estorno, "O estorno está…").
     */
    private Optional<String> travaDaFaturaPaga(Long usuarioId, Compra compra, List<Lancamento> parcelas) {
        CartaoResumo cartao = cartoes.buscar(usuarioId, compra.getCartaoId()).orElseThrow();
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        PagamentosDasFaturas pagamentos = PagamentosDasFaturas.carregar(lancamentos, usuarioId, doCartao);
        LocalDate hoje = regras.hoje();
        return parcelas.stream().filter(parcela -> pagamentos.paga(YearMonth.from(parcela.getData()), hoje)).findFirst()
                .map(parcela -> {
                    String quem = compra.estorno() ? "O estorno está" : compra.getParcelas() == 1 ? "A compra está"
                            : "A parcela " + parcela.getNumeroParcela() + "/" + compra.getParcelas() + " está";
                    return quem + " na fatura de " + FaturaService.nomeDoMes(YearMonth.from(parcela.getData()), hoje)
                            + ", que já foi paga: só a descrição, a categoria e a observação mudam.";
                });
    }

    /**
     * Grava as parcelas da {@code parcelaInicial} à última (decisão 17), cada uma na fatura do mês dela, que é
     * criada se ainda não existe. A 1ª parcela vai para {@code mesDaPrimeira} ou, nulo, para a fatura da data da
     * compra. Antes, os limites: o total e o de cada mês (decisão 21).
     */
    private void gravarParcelas(Long usuarioId, Compra compra, CartaoResumo cartao, Dados dados, YearMonth mesDaPrimeira) {
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        YearMonth primeira = mesDaPrimeira != null ? mesDaPrimeira : doCartao.faturaDe(dados.data());
        List<BigDecimal> valores = Parcelamento.dividir(dados.valorTotal(), dados.parcelas());
        Map<YearMonth, Long> porMes = new TreeMap<>();
        for (int numero = dados.parcelaInicial(); numero <= dados.parcelas(); numero++) {
            porMes.put(primeira.plusMonths(numero - 1L), 1L);
        }
        regras.exigirLimiteTotal(usuarioId, porMes.size());
        regras.exigirLimiteDosMeses(usuarioId, porMes, dados.tipo() == TipoCompra.ESTORNO ? "Este estorno passaria" : "Esta compra passaria");
        Instant agora = regras.agora();
        List<Lancamento> parcelas = new ArrayList<>();
        for (int numero = dados.parcelaInicial(); numero <= dados.parcelas(); numero++) {
            Fatura fatura = doCartao.obter(faturas, primeira.plusMonths(numero - 1L), agora);
            BigDecimal valor = dados.tipo() == TipoCompra.ESTORNO ? valores.get(numero - 1) : valores.get(numero - 1).negate();
            parcelas.add(Lancamento.daFatura(usuarioId, cartao.id(), fatura.getId(), fatura.getDataVencimento(), compra.getId(), numero,
                    valor, dados.descricao(), dados.categoriaId(), dados.observacao(), agora));
        }
        lancamentos.saveAll(parcelas);
    }

    /** Decisões 14 a 17: a faixa de datas e as parcelas, na prévia e ao lançar a compra ou o estorno. */
    private void exigirDataEParcelas(LocalDate data, BigDecimal valorTotal, int parcelas, int parcelaInicial) {
        exigirFaixaDeDatas(data);
        exigirParcelas(valorTotal, parcelas, parcelaInicial);
    }

    /** Decisão 16: de 10 anos antes de hoje a 12 meses depois de hoje; as duas pontas valem. */
    private void exigirFaixaDeDatas(LocalDate data) {
        regras.exigirDataNoLimite(data);
        LocalDate minima = regras.hoje().minusYears(ANOS_PARA_TRAS);
        if (data.isBefore(minima)) {
            throw new CampoInvalidoException("data", "A data precisa ser " + minima.format(DATA_CURTA) + " ou depois.");
        }
    }

    /** Decisões 15 e 17: a primeira parcela no Flux entre a 1ª e a última e ao menos 1 centavo por parcela. */
    private void exigirParcelas(BigDecimal valorTotal, int parcelas, int parcelaInicial) {
        if (parcelaInicial < 1 || parcelaInicial > parcelas) {
            throw new CampoInvalidoException("parcelaInicial", "A primeira parcela no Flux vai da 1ª à " + parcelas + "ª.");
        }
        BigDecimal minimo = Parcelamento.minimo(parcelas);
        if (valorTotal.compareTo(minimo) < 0) {
            throw new CampoInvalidoException("valorTotal", "Em " + parcelas + "x, o valor precisa ser de pelo menos R$ "
                    + String.format(PT_BR, "%,.2f", minimo) + ".");
        }
    }

    private Compra encontrar(Long usuarioId, Long id, TipoCompra tipo) {
        return compras.findByIdAndUsuarioIdAndTipo(id, usuarioId, tipo).orElseThrow(() -> new RecursoNaoEncontradoException(
                tipo == TipoCompra.ESTORNO ? "Estorno não encontrado." : "Compra não encontrada."));
    }

    private CompraResponse resposta(Long usuarioId, Compra compra) {
        List<Lancamento> parcelas = lancamentos.findAllByUsuarioIdAndCompraIdInOrderByCompraIdAscNumeroParcelaAsc(usuarioId,
                List.of(compra.getId()));
        CartaoResumo cartao = cartoes.buscar(usuarioId, compra.getCartaoId()).orElseThrow();
        Lancamento primeira = parcelas.getFirst();
        CategoriaResumo categoria = primeira.getCategoriaId() == null ? null
                : categorias.buscar(usuarioId, primeira.getCategoriaId()).orElse(null);
        List<ParcelaDaCompra> noFlux = parcelas.stream()
                .map(parcela -> new ParcelaDaCompra(parcela.getNumeroParcela(), YearMonth.from(parcela.getData()).toString(),
                        parcela.getData(), parcela.getValor().abs(), true))
                .toList();
        return new CompraResponse(compra.getId(), compra.getTipo().name(), cartao.id(), cartao.nome(), cartao.arquivado(),
                compra.getDataCompra(), primeira.getDescricao(), compra.getValorTotal(), compra.getParcelas(), compra.getParcelaInicial(),
                categoria == null ? null : categoria.id(), categoria == null ? null : categoria.nome(),
                categoria == null ? null : categoria.nomeDaMae(), categoria != null && categoria.arquivada(), primeira.getObservacao(),
                noFlux, travaDaFaturaPaga(usuarioId, compra, parcelas).orElse(null), compra.importada());
    }

    private static TipoLancamento natureza(TipoCompra tipo) {
        return tipo == TipoCompra.ESTORNO ? TipoLancamento.RECEITA : TipoLancamento.DESPESA;
    }

    private static String oQue(TipoCompra tipo) {
        return tipo == TipoCompra.ESTORNO ? "este estorno" : "esta compra";
    }
}
