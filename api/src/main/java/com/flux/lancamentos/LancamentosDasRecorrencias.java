package com.flux.lancamentos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;

/**
 * O que as recorrências (Fatia 07a, ADR 0022) pedem aos lançamentos: gravar o lançamento ou a compra da confirmação,
 * achar os lançamentos e as compras do "Já lancei" e do histórico e a fatura de uma data no cartão. Só lançamentos grava
 * nas tabelas de lançamentos, compras e faturas.
 */
public interface LancamentosDasRecorrencias {

    /**
     * Grava a receita ou a despesa à mão com as regras do POST /api/lancamentos: data até hoje + 12 meses e desde o saldo
     * inicial, conta ativa, categoria do usuário, da natureza e ativa, e os limites; o do mês diz "Este lançamento passaria
     * do limite de 1.000 lançamentos em dezembro de 2026.". Exige uma transação aberta, em que quem chama já pegou as
     * travas de contas, cartões e categorias, nessa ordem.
     */
    Lancado lancar(Long usuarioId, NovoLancamento novo);

    /** Grava a compra à vista (uma parcela) no cartão, com as regras do POST /api/compras. A mesma transação e as travas. */
    Comprado comprar(Long usuarioId, NovaCompra nova);

    /** As receitas e as despesas da conta, não ignoradas, com a data de {@code de} a {@code ate}, por data e id. */
    List<Alvo> lancamentosDaConta(Long usuarioId, Long contaId, LocalDate de, LocalDate ate);

    /** As compras do cartão (não os estornos) com a data da compra de {@code de} a {@code ate}, por data e id. */
    List<Alvo> comprasDoCartao(Long usuarioId, Long cartaoId, LocalDate de, LocalDate ate);

    /** Os lançamentos do usuário com esses ids, de qualquer tipo e inclusive os ignorados; os de outro usuário não vêm. */
    List<Alvo> lancamentos(Long usuarioId, Collection<Long> ids);

    /** As compras e os estornos do usuário com esses ids; os de outro usuário não vêm. */
    List<Alvo> compras(Long usuarioId, Collection<Long> ids);

    /** A fatura de cada data no cartão do usuário, na mesma ordem: pelas faturas gravadas (com os ajustes) ou pelos dias. */
    List<FaturaDaData> faturasDe(Long usuarioId, Long cartaoId, List<LocalDate> datas);

    /** {@code valor} positivo; {@code tipo} "RECEITA" ou "DESPESA"; {@code categoriaId} e {@code observacao} opcionais. */
    record NovoLancamento(Long contaId, String tipo, LocalDate data, String descricao, BigDecimal valor, Long categoriaId,
                          String observacao) {
    }

    /** {@code valor} positivo, o total da compra à vista; {@code data} é a da compra. */
    record NovaCompra(Long cartaoId, LocalDate data, String descricao, BigDecimal valor, Long categoriaId, String observacao) {
    }

    record Lancado(Long lancamentoId, LocalDate data) {
    }

    /** A compra gravada, a data dela e a fatura em que caiu (o mês e o vencimento). */
    record Comprado(Long compraId, LocalDate data, YearMonth fatura, LocalDate vencimento) {
    }

    /**
     * Um lançamento ou uma compra. {@code tipo}: no lançamento, "RECEITA", "DESPESA", "TRANSFERENCIA" ou
     * "PAGAMENTO_FATURA"; na compra, "COMPRA" ou "ESTORNO". {@code data}: a do lançamento ou a da compra. {@code valor}: com
     * sinal e escala 2; na compra, o total (negativo na compra, positivo no estorno). Na compra, {@code descricao} e
     * {@code categoriaId} são os da primeira parcela no Flux, e {@code mesNaLista} é o mês do vencimento dela; no
     * lançamento, o mês da data. {@code origem}: "MANUAL" ou "IMPORTADO". {@code ignorado}: o importado excluído.
     */
    record Alvo(Long id, Long contaId, Long cartaoId, String tipo, LocalDate data, BigDecimal valor, String descricao,
                String origem, Long categoriaId, YearMonth mesNaLista, boolean ignorado) {
    }

    record FaturaDaData(LocalDate data, YearMonth mes, LocalDate fechamento, LocalDate vencimento) {
    }
}
