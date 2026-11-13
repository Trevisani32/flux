package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * data dele. Gravar pega a trava de cartões e depois a de categorias (decisão 49).
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
        return new PreviaDaCompraResponse(parcelas, new PreviaDaCompraResponse.Fatura(primeira.toString(), datas.fechamento(),
                datas.vencimento(), doCartao.situacao(primeira, regras.hoje()).name()));
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
        // Decisão 7: com a data e o cartão iguais, a 1ª parcela fica no mês em que está, sem a fatura ser buscada pela
        // data de novo. Depois de uma troca de dias, o mês da data pode não ter gravados os dias de antes (a compra em
        // andamento não grava a fatura das parcelas de antes do Flux), e a busca usaria os dias novos.
        Lancamento primeiraNoFlux = atuais.getFirst();
        YearMonth mesDaPrimeira = compra.getCartaoId().equals(cartao.id()) && compra.getDataCompra().equals(dados.data())
                ? YearMonth.from(primeiraNoFlux.getData()).minusMonths(primeiraNoFlux.getNumeroParcela() - 1L) : null;
        // O delete limpa o contexto de persistência: a compra volta a ser gravada pelo save.
        lancamentos.apagarDaCompra(usuarioId, id);
        compra.alterar(cartao.id(), dados.data(), dados.valorTotal(), dados.parcelas(), dados.parcelaInicial(), agora);
        Compra gravada = compras.saveAndFlush(compra);
        gravarParcelas(usuarioId, gravada, cartao, dados, mesDaPrimeira);
        return resposta(usuarioId, gravada);
    }

    /** Decisão 19: as parcelas saem junto; as faturas ficam (decisão 9). */
    @Transactional
    public void excluir(Long usuarioId, Long id, TipoCompra tipo) {
        regras.travarCartoesECategorias(usuarioId);
        Compra compra = encontrar(usuarioId, id, tipo);
        regras.exigirCartaoAtivoPara(usuarioId, compra.getCartaoId(), "editar ou excluir " + oQue(tipo));
        lancamentos.apagarDaCompra(usuarioId, id);
        compras.delete(compra);
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
                noFlux);
    }

    private static TipoLancamento natureza(TipoCompra tipo) {
        return tipo == TipoCompra.ESTORNO ? TipoLancamento.RECEITA : TipoLancamento.DESPESA;
    }

    private static String oQue(TipoCompra tipo) {
        return tipo == TipoCompra.ESTORNO ? "este estorno" : "esta compra";
    }
}
