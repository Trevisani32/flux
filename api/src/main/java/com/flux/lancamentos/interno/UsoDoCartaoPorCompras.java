package com.flux.lancamentos.interno;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.flux.cartoes.UsoDoCartao;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decisão 28: as compras e os estornos são uso do cartão; desde a Fatia 05b (decisão 5), também os pagamentos das
 * faturas dele: "12 compras, 1 estorno e 2 pagamentos".
 */
@Component
public class UsoDoCartaoPorCompras implements UsoDoCartao {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final CompraRepository compras;
    private final LancamentoRepository lancamentos;

    public UsoDoCartaoPorCompras(CompraRepository compras, LancamentoRepository lancamentos) {
        this.compras = compras;
        this.lancamentos = lancamentos;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> uso(Long usuarioId, Long cartaoId) {
        long daCompra = compras.countByUsuarioIdAndCartaoIdAndTipo(usuarioId, cartaoId, TipoCompra.COMPRA);
        long deEstorno = compras.countByUsuarioIdAndCartaoIdAndTipo(usuarioId, cartaoId, TipoCompra.ESTORNO);
        long dePagamento = lancamentos.pagosDoCartao(usuarioId, cartaoId).stream().mapToLong(PagoDaFatura::pagamentos).sum();
        List<String> partes = new ArrayList<>();
        if (daCompra > 0) {
            partes.add(daCompra == 1 ? "1 compra" : String.format(PT_BR, "%,d compras", daCompra));
        }
        if (deEstorno > 0) {
            partes.add(deEstorno == 1 ? "1 estorno" : String.format(PT_BR, "%,d estornos", deEstorno));
        }
        if (dePagamento > 0) {
            partes.add(dePagamento == 1 ? "1 pagamento" : String.format(PT_BR, "%,d pagamentos", dePagamento));
        }
        if (partes.isEmpty()) {
            return Optional.empty();
        }
        String ultima = partes.removeLast();
        return Optional.of(partes.isEmpty() ? ultima : String.join(", ", partes) + " e " + ultima);
    }
}
