package com.flux.lancamentos.interno;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.flux.cartoes.UsoDoCartao;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Decisão 28: as compras e os estornos são uso do cartão: "12 compras e 1 estorno". */
@Component
public class UsoDoCartaoPorCompras implements UsoDoCartao {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final CompraRepository compras;

    public UsoDoCartaoPorCompras(CompraRepository compras) {
        this.compras = compras;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> uso(Long usuarioId, Long cartaoId) {
        long daCompra = compras.countByUsuarioIdAndCartaoIdAndTipo(usuarioId, cartaoId, TipoCompra.COMPRA);
        long deEstorno = compras.countByUsuarioIdAndCartaoIdAndTipo(usuarioId, cartaoId, TipoCompra.ESTORNO);
        List<String> partes = new ArrayList<>();
        if (daCompra > 0) {
            partes.add(daCompra == 1 ? "1 compra" : String.format(PT_BR, "%,d compras", daCompra));
        }
        if (deEstorno > 0) {
            partes.add(deEstorno == 1 ? "1 estorno" : String.format(PT_BR, "%,d estornos", deEstorno));
        }
        return partes.isEmpty() ? Optional.empty() : Optional.of(String.join(" e ", partes));
    }
}
