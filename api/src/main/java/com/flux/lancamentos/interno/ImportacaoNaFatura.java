package com.flux.lancamentos.interno;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.cartoes.CalendarioDoCartao;
import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.CartoesConsulta;
import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.lancamentos.LancamentosImportados;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Operações da importação que gravam ou consultam parcelas e estornos numa fatura. */
@Component
class ImportacaoNaFatura {

    private final FaturaRepository faturas;
    private final LancamentoRepository lancamentos;
    private final CompraRepository compras;
    private final CartoesConsulta cartoes;
    private final CategoriasConsulta categorias;
    private final RegrasDosLancamentos regras;

    ImportacaoNaFatura(FaturaRepository faturas, LancamentoRepository lancamentos, CompraRepository compras, CartoesConsulta cartoes,
                       CategoriasConsulta categorias, RegrasDosLancamentos regras) {
        this.faturas = faturas;
        this.lancamentos = lancamentos;
        this.compras = compras;
        this.cartoes = cartoes;
        this.categorias = categorias;
        this.regras = regras;
    }

    List<LancamentosImportados.FaturaParaImportar> faturasParaImportar(Long usuarioId, Long cartaoId, java.time.LocalDate data) {
        CartaoResumo cartao = cartao(usuarioId, cartaoId);
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        YearMonth meio = doCartao.faturaDe(data);
        return List.of(meio.minusMonths(1), meio, meio.plusMonths(1)).stream().map(mes -> {
            CalendarioDoCartao.Datas datas = doCartao.datas(mes);
            return new LancamentosImportados.FaturaParaImportar(mes, datas.fechamento(), datas.vencimento(),
                    doCartao.situacao(mes, regras.hoje()).name());
        }).toList();
    }

    Long faturaParaImportar(Long usuarioId, Long cartaoId, YearMonth mes) {
        CartaoResumo cartao = cartao(usuarioId, cartaoId);
        return FaturasDoCartao.carregar(faturas, usuarioId, cartao).obter(faturas, mes, regras.agora()).getId();
    }

    List<LancamentosImportados.ExistenteNoCartao> existentes(Long usuarioId, Long cartaoId, YearMonth mes,
                                                               java.time.LocalDate inicio, java.time.LocalDate fim,
                                                               Collection<String> chaves) {
        CartaoResumo cartao = cartao(usuarioId, cartaoId);
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        Long faturaId = doCartao.gravada(mes).map(Fatura::getId).orElse(-1L);
        Collection<String> procuradas = chaves.isEmpty() ? List.of("-") : chaves;
        List<Lancamento> encontrados = lancamentos.existentesNoCartao(usuarioId, cartaoId, faturaId, inicio, fim, procuradas);
        Map<Long, Compra> porCompra = compras.findAllByUsuarioIdAndIdIn(usuarioId,
                        encontrados.stream().map(Lancamento::getCompraId).toList()).stream()
                .collect(Collectors.toMap(Compra::getId, Function.identity()));
        PagamentosDasFaturas pagamentos = new PagamentosDasFaturas(doCartao, lancamentos.totaisDoCartao(usuarioId, cartaoId),
                lancamentos.pagosDoCartao(usuarioId, cartaoId));
        return encontrados.stream().map(lancamento -> {
            Compra compra = porCompra.get(lancamento.getCompraId());
            YearMonth fatura = YearMonth.from(lancamento.getData());
            return new LancamentosImportados.ExistenteNoCartao(lancamento.getId(), compra.getDataCompra(), fatura,
                    lancamento.getValor(), lancamento.getDescricao(), lancamento.getDescricaoBanco(), lancamento.getNumeroParcela(),
                    compra.getParcelas(), lancamento.getChaveImportacao(), lancamento.getImportacaoId(), lancamento.getCriadoEm(),
                    pagamentos.paga(fatura, regras.hoje()));
        }).toList();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    int gravar(Long usuarioId, Long cartaoId, YearMonth mes, Long importacaoId, List<LancamentosImportados.NovaNaFatura> novas) {
        regras.travarCartoesECategorias(usuarioId);
        CartaoResumo cartao = cartao(usuarioId, cartaoId);
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        Map<YearMonth, Long> porMes = new HashMap<>();
        for (LancamentosImportados.NovaNaFatura nova : novas) {
            int total = nova.parcelas() == null ? 1 : nova.parcelas();
            int inicial = nova.parcela() == null ? 1 : nova.parcela();
            for (int parcela = inicial; parcela <= total; parcela++) {
                porMes.merge(mes.plusMonths(parcela - inicial), 1L, Long::sum);
            }
        }
        regras.exigirLimiteDosMeses(usuarioId, porMes, "Esta importação passaria");
        Map<Long, CategoriaResumo> categoriasDoUsuario = categorias.listar(usuarioId).stream()
                .collect(Collectors.toMap(CategoriaResumo::id, Function.identity()));
        int gravados = 0;
        for (LancamentosImportados.NovaNaFatura nova : novas) {
            if (nova.categoriaId() != null) {
                CategoriaResumo categoria = categoriasDoUsuario.get(nova.categoriaId());
                String natureza = nova.valor().signum() > 0 ? "RECEITA" : "DESPESA";
                if (categoria == null || categoria.arquivada() || !natureza.equals(categoria.natureza())) {
                    throw new RegraDeNegocioException("Uma das categorias escolhidas foi arquivada ou excluída. Revise as linhas marcadas.",
                            "CATEGORIA_INVALIDA").com("linha", nova.linha());
                }
            }
            int total = nova.parcelas() == null ? 1 : nova.parcelas();
            int inicial = nova.parcela() == null ? 1 : nova.parcela();
            TipoCompra tipo = nova.valor().signum() > 0 ? TipoCompra.ESTORNO : TipoCompra.COMPRA;
            Compra compra = Compra.importada(usuarioId, tipo, importacaoId, regras.agora());
            compra.alterar(cartaoId, nova.data(), nova.valor().abs().multiply(BigDecimal.valueOf(total)), total, inicial, regras.agora());
            compra = compras.saveAndFlush(compra);
            for (int parcela = inicial; parcela <= total; parcela++) {
                YearMonth daParcela = mes.plusMonths(parcela - inicial);
                Fatura fatura = doCartao.obter(faturas, daParcela, regras.agora());
                lancamentos.save(Lancamento.importadoNaFatura(usuarioId, cartaoId, fatura.getId(), fatura.getDataVencimento(), compra.getId(),
                        parcela, nova.valor(), nova.descricao(), nova.descricaoBanco(),
                        total == 1 ? nova.chave() : nova.chave() + ":" + parcela, importacaoId, nova.categoriaId(), regras.agora()));
                gravados++;
            }
        }
        return gravados;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    int corrigir(Long usuarioId, List<LancamentosImportados.ValorDoBanco> valores) {
        if (valores.isEmpty()) {
            return 0;
        }
        Map<Long, Lancamento> porId = lancamentos.findAllByUsuarioIdAndIdIn(usuarioId,
                        valores.stream().map(LancamentosImportados.ValorDoBanco::lancamentoId).toList()).stream()
                .collect(Collectors.toMap(Lancamento::getId, Function.identity()));
        Map<Long, Compra> porCompra = compras.findAllByUsuarioIdAndIdIn(usuarioId,
                        porId.values().stream().map(Lancamento::getCompraId).toList()).stream()
                .collect(Collectors.toMap(Compra::getId, Function.identity()));
        int corrigidas = 0;
        for (LancamentosImportados.ValorDoBanco valor : valores) {
            Lancamento lancamento = porId.get(valor.lancamentoId());
            if (lancamento == null || !lancamento.importado() || lancamento.getCompraId() == null
                    || lancamento.getValor().compareTo(valor.valor()) == 0) {
                continue;
            }
            Compra compra = porCompra.get(lancamento.getCompraId());
            if (compra == null || !compra.importada()) {
                continue;
            }
            compra.corrigirTotal(compra.getValorTotal().add(valor.valor().abs()).subtract(lancamento.getValor().abs()), regras.agora());
            lancamento.corrigirValorDoBanco(valor.valor());
            corrigidas++;
        }
        return corrigidas;
    }

    Map<Long, YearMonth> meses(Long usuarioId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return faturas.findAllByUsuarioIdAndIdIn(usuarioId, ids).stream().collect(Collectors.toMap(Fatura::getId, Fatura::mes));
    }

    int apagarCompras(Long usuarioId, Long importacaoId) {
        exigirForaDasPagas(usuarioId, importacaoId);
        lancamentos.findAllByUsuarioIdAndImportacaoIdAndFaturaIdIsNotNull(usuarioId, importacaoId).forEach(lancamentos::delete);
        return compras.apagarDaImportacao(usuarioId, importacaoId);
    }

    private void exigirForaDasPagas(Long usuarioId, Long importacaoId) {
        List<Lancamento> parcelas = lancamentos.findAllByUsuarioIdAndImportacaoIdAndFaturaIdIsNotNull(usuarioId, importacaoId);
        if (parcelas.isEmpty()) {
            return;
        }
        CartaoResumo cartao = cartao(usuarioId, parcelas.getFirst().getCartaoId());
        FaturasDoCartao doCartao = FaturasDoCartao.carregar(faturas, usuarioId, cartao);
        PagamentosDasFaturas pagamentos = PagamentosDasFaturas.carregar(lancamentos, usuarioId, doCartao);
        LocalDate hoje = regras.hoje();
        Optional<YearMonth> paga = parcelas.stream().map(parcela -> YearMonth.from(parcela.getData())).distinct().sorted()
                .filter(mes -> pagamentos.paga(mes, hoje)).findFirst();
        if (paga.isPresent()) {
            throw new RegraDeNegocioException("Esta importação tem lançamento na fatura de " + FaturaService.nomeDoMes(paga.get(), hoje)
                    + ", que já foi paga. Para desfazê-la, exclua o pagamento dessa fatura ou desmarque a fatura paga fora do Flux.",
                    "FATURA_PAGA");
        }
    }

    private CartaoResumo cartao(Long usuarioId, Long cartaoId) {
        return cartoes.buscar(usuarioId, cartaoId).orElseThrow(() -> new RegraDeNegocioException("Cartão não encontrado."));
    }
}
