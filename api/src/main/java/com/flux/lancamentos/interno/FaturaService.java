package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.flux.cartoes.CalendarioDoCartao;
import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.CartoesConsulta;
import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.lancamentos.interno.dto.AjusteResponse;
import com.flux.lancamentos.interno.dto.FaturaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A fatura de um mês e o ajuste do fechamento dela (decisões 8 a 13, ADR 0017). Uma fatura que ainda não existe
 * aparece com as datas do cartão (decisão 9). O ajuste pega só a trava de cartões (decisão 49).
 */
@Service
public class FaturaService {

    private static final Pattern MES = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter NOME_DO_MES = DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("pt-BR"));
    /** Decisão 13: as setas vão até 12 meses depois da aberta, ou até a última fatura com lançamento. */
    private static final int MESES_DEPOIS_DA_ABERTA = 12;

    private final FaturaRepository faturas;
    private final LancamentoRepository lancamentos;
    private final CompraRepository compras;
    private final RegrasDosLancamentos regras;
    private final CartoesConsulta cartoes;
    private final CategoriasConsulta categorias;
    private final Recolocacao recolocacao;

    public FaturaService(FaturaRepository faturas, LancamentoRepository lancamentos, CompraRepository compras, RegrasDosLancamentos regras,
                         CartoesConsulta cartoes, CategoriasConsulta categorias, Recolocacao recolocacao) {
        this.faturas = faturas;
        this.lancamentos = lancamentos;
        this.compras = compras;
        this.regras = regras;
        this.cartoes = cartoes;
        this.categorias = categorias;
        this.recolocacao = recolocacao;
    }

    @Transactional(readOnly = true)
    public FaturaResponse buscar(Long usuarioId, Long cartaoId, String mes) {
        YearMonth mesDaFatura = mesValido(mes);
        CartaoResumo cartao = encontrar(usuarioId, cartaoId);
        return resposta(usuarioId, FaturasDoCartao.carregar(faturas, usuarioId, cartao), mesDaFatura);
    }

    /** Decisão 12: o fechamento só desta fatura; as compras do trecho que mudou trocam de fatura. */
    @Transactional
    public AjusteResponse ajustar(Long usuarioId, Long cartaoId, String mes, LocalDate data) {
        return mudarFechamento(usuarioId, cartaoId, mes, data);
    }

    /** "Voltar ao fechamento normal" (decisão 12). */
    @Transactional
    public AjusteResponse voltarAoNormal(Long usuarioId, Long cartaoId, String mes) {
        return mudarFechamento(usuarioId, cartaoId, mes, null);
    }

    /** {@code pedida} nula é o fechamento normal. A fatura que não existe é criada para guardar o ajuste (decisão 9). */
    private AjusteResponse mudarFechamento(Long usuarioId, Long cartaoId, String mes, LocalDate pedida) {
        YearMonth mesDaFatura = mesValido(mes);
        regras.travarCartoes(usuarioId);
        CartaoResumo cartao = encontrar(usuarioId, cartaoId);
        if (cartao.arquivado()) {
            throw new RegraDeNegocioException(cartao.nome() + " está arquivado. Reative o cartão para ajustar o fechamento.",
                    "CARTAO_ARQUIVADO");
        }
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        CalendarioDoCartao calendario = doCartao.calendario();
        CalendarioDoCartao.Limites limites = calendario.limitesDoAjuste(mesDaFatura, doCartao.gravadas());
        LocalDate normal = doCartao.fechamentoNormal(mesDaFatura);
        LocalDate nova = pedida == null ? normal : pedida;
        if (nova.isBefore(limites.de()) || nova.isAfter(limites.ate())) {
            throw new RegraDeNegocioException("Escolha uma data de " + limites.de().format(DATA_CURTA) + " a "
                    + limites.ate().format(DATA_CURTA) + ": depois do fechamento de " + nomeDoMes(mesDaFatura.minusMonths(1))
                    + " e antes " + (limites.ateOVencimento() ? "do vencimento de " + nomeDoMes(mesDaFatura)
                    : "do fechamento de " + nomeDoMes(mesDaFatura.plusMonths(1))) + ".", "FECHAMENTO_FORA_DOS_LIMITES")
                    .com("campo", "data");
        }
        LocalDate atual = doCartao.datas(mesDaFatura).fechamento();
        boolean ajustado = !nova.equals(normal);
        Optional<Fatura> gravada = doCartao.gravada(mesDaFatura);
        if (nova.equals(atual) && gravada.map(Fatura::isFechamentoAjustado).orElse(false) == ajustado) {
            return new AjusteResponse(0, 0, resposta(usuarioId, doCartao, mesDaFatura));
        }
        Instant agora = regras.agora();
        Fatura fatura = doCartao.obter(faturas, mesDaFatura, agora);
        fatura.ajustarFechamento(nova, ajustado);
        faturas.saveAndFlush(fatura);
        Recolocacao.Movidas movidas = new Recolocacao.Movidas(0, 0);
        if (!nova.equals(atual)) {
            LocalDate de = (nova.isBefore(atual) ? nova : atual).plusDays(1);
            LocalDate ate = nova.isBefore(atual) ? atual : nova;
            movidas = recolocacao.recolocar(usuarioId, doCartao,
                    compras.findAllByUsuarioIdAndCartaoIdAndDataCompraBetween(usuarioId, cartao.id(), de, ate), "O ajuste passaria");
        }
        return new AjusteResponse(movidas.compras(), movidas.estornos(), resposta(usuarioId, doCartao, mesDaFatura));
    }

    private CartaoResumo encontrar(Long usuarioId, Long cartaoId) {
        return cartoes.buscar(usuarioId, cartaoId).orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado."));
    }

    private static YearMonth mesValido(String mes) {
        if (mes == null || !MES.matcher(mes).matches()) {
            throw new CampoInvalidoException("mes", "Mês inválido.");
        }
        return YearMonth.parse(mes);
    }

    private static String nomeDoMes(YearMonth mes) {
        return mes.atDay(1).format(NOME_DO_MES);
    }

    private FaturaResponse resposta(Long usuarioId, FaturasDoCartao doCartao, YearMonth mes) {
        CartaoResumo cartao = doCartao.cartao();
        CalendarioDoCartao calendario = doCartao.calendario();
        LocalDate hoje = regras.hoje();
        CalendarioDoCartao.Datas datas = doCartao.datas(mes);
        CalendarioDoCartao.Limites limites = calendario.limitesDoAjuste(mes, doCartao.gravadas());
        Map<Long, TotalDaFatura> totais = lancamentos.totaisDoCartao(usuarioId, cartao.id()).stream()
                .collect(Collectors.toMap(TotalDaFatura::faturaId, Function.identity()));
        Map<YearMonth, TotalDaFatura> porMes = doCartao.porMes().entrySet().stream()
                .filter(entrada -> totais.containsKey(entrada.getValue().getId()))
                .collect(Collectors.toMap(Map.Entry::getKey, entrada -> totais.get(entrada.getValue().getId())));
        Optional<Fatura> gravada = doCartao.gravada(mes);
        TotalDaFatura desta = porMes.get(mes);
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        BigDecimal depois = porMes.entrySet().stream().filter(entrada -> entrada.getKey().isAfter(mes))
                .map(entrada -> entrada.getValue().total()).reduce(zero, BigDecimal::add).setScale(2, RoundingMode.UNNECESSARY);
        Optional<YearMonth> depoisAte = porMes.keySet().stream().filter(outro -> outro.isAfter(mes)).max(Comparator.naturalOrder());
        YearMonth aberta = doCartao.faturaDe(hoje);
        YearMonth primeiro = porMes.keySet().stream().min(Comparator.naturalOrder()).filter(outro -> outro.isBefore(aberta)).orElse(aberta);
        YearMonth ate = aberta.plusMonths(MESES_DEPOIS_DA_ABERTA);
        YearMonth ultimo = porMes.keySet().stream().max(Comparator.naturalOrder()).filter(outro -> outro.isAfter(ate)).orElse(ate);
        return new FaturaResponse(cartao.id(), cartao.nome(), cartao.cor(), cartao.arquivado(), mes.toString(), doCartao.inicio(mes),
                datas.fechamento(), datas.vencimento(), gravada.isPresent(), gravada.map(Fatura::isFechamentoAjustado).orElse(false),
                doCartao.fechamentoNormal(mes), limites.de(), limites.ate(), limites.ateOVencimento(),
                doCartao.situacao(mes, hoje).name(), desta == null ? zero : desta.somaDasCompras(), desta == null ? 0 : desta.compras(),
                desta == null ? zero : desta.somaDosEstornos(), desta == null ? 0 : desta.quantidadeDeEstornos(),
                desta == null ? zero : desta.total(), depois, depoisAte.map(YearMonth::toString).orElse(null), primeiro.toString(),
                ultimo.toString(), gravada.map(fatura -> itens(usuarioId, fatura)).orElse(List.of()));
    }

    /** Os lançamentos da fatura, a compra mais recente primeiro (no mesmo dia, o último gravado em cima). */
    private List<FaturaResponse.Item> itens(Long usuarioId, Fatura fatura) {
        List<Lancamento> daFatura = lancamentos.findAllByUsuarioIdAndFaturaId(usuarioId, fatura.getId());
        if (daFatura.isEmpty()) {
            return List.of();
        }
        Map<Long, Compra> porId = compras.findAllByUsuarioIdAndIdIn(usuarioId, daFatura.stream().map(Lancamento::getCompraId).toList())
                .stream().collect(Collectors.toMap(Compra::getId, Function.identity()));
        Map<Long, CategoriaResumo> doUsuario = categorias.listar(usuarioId).stream()
                .collect(Collectors.toMap(CategoriaResumo::id, Function.identity()));
        return daFatura.stream()
                .sorted(Comparator.comparing((Lancamento lancamento) -> porId.get(lancamento.getCompraId()).getDataCompra())
                        .thenComparing(Lancamento::getId).reversed())
                .map(lancamento -> {
                    Compra compra = porId.get(lancamento.getCompraId());
                    CategoriaResumo categoria = lancamento.getCategoriaId() == null ? null : doUsuario.get(lancamento.getCategoriaId());
                    return new FaturaResponse.Item(lancamento.getId(), compra.getId(), compra.getTipo().name(), compra.getDataCompra(),
                            lancamento.getDescricao(), lancamento.getValor(), lancamento.getNumeroParcela(), compra.getParcelas(),
                            compra.getParcelaInicial(), compra.getValorTotal(), categoria == null ? null : categoria.id(),
                            categoria == null ? null : categoria.nome(), categoria == null ? null : categoria.nomeDaMae());
                })
                .toList();
    }
}
