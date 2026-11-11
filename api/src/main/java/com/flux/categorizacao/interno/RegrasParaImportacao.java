package com.flux.categorizacao.interno;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import com.flux.categorizacao.RegrasDeCategorizacao;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** A sugestão das regras na revisão da importação (decisão 14). Só lê. */
@Component
public class RegrasParaImportacao implements RegrasDeCategorizacao {

    private final RegraRepository repositorio;
    private final ContasConsulta contas;
    private final CategoriasConsulta categorias;

    public RegrasParaImportacao(RegraRepository repositorio, ContasConsulta contas, CategoriasConsulta categorias) {
        this.repositorio = repositorio;
        this.contas = contas;
        this.categorias = categorias;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Sugestao> sugerir(Long usuarioId, Long contaId, List<Alvo> alvos) {
        List<Regra> regras = repositorio.findAllByUsuarioIdOrderByPosicao(usuarioId);
        if (regras.isEmpty()) {
            return Collections.nCopies(alvos.size(), null);
        }
        RegrasAtivas ativas = new RegrasAtivas(regras,
                categorias.listar(usuarioId).stream().collect(Collectors.toMap(CategoriaResumo::id, Function.identity())),
                contas.listar(usuarioId).stream().collect(Collectors.toMap(ContaResumo::id, Function.identity())));
        return alvos.stream().map(alvo -> {
            Regra regra = ativas.primeira(contaId, RegrasAtivas.natureza(alvo.valor()), Comparacao.chave(alvo.descricao()));
            return regra == null ? null : new Sugestao(regra.getId(), regra.getPosicao(), regra.getCondicao().name(), regra.getTexto(),
                    regra.getCategoriaId(), regra.getDescricao());
        }).toList();
    }
}
