package com.flux.categorias.interno;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.flux.acesso.UsuarioCriado;
import com.flux.compartilhado.texto.TextoComparavel;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Categorias que todo usuário novo recebe (produto.md, módulo 3). Os usuários que já
 * existiam na Fatia 03a receberam a mesma lista pela migration V3.
 */
@Component
public class CategoriasPadrao {

    public record Padrao(Natureza natureza, String nome, List<String> subcategorias) {
    }

    public static final List<Padrao> LISTA = List.of(
            new Padrao(Natureza.DESPESA, "Moradia", List.of("Aluguel", "Condomínio", "Contas da casa")),
            new Padrao(Natureza.DESPESA, "Alimentação", List.of("Mercado", "Restaurante", "Delivery")),
            new Padrao(Natureza.DESPESA, "Transporte", List.of("Combustível", "App de transporte", "Transporte público")),
            new Padrao(Natureza.DESPESA, "Saúde", List.of()),
            new Padrao(Natureza.DESPESA, "Educação", List.of()),
            new Padrao(Natureza.DESPESA, "Lazer", List.of()),
            new Padrao(Natureza.DESPESA, "Assinaturas", List.of()),
            new Padrao(Natureza.DESPESA, "Compras", List.of()),
            new Padrao(Natureza.DESPESA, "Impostos e taxas", List.of()),
            new Padrao(Natureza.DESPESA, "Outras despesas", List.of()),
            new Padrao(Natureza.RECEITA, "Salário", List.of()),
            new Padrao(Natureza.RECEITA, "Freelance", List.of()),
            new Padrao(Natureza.RECEITA, "Rendimentos", List.of()),
            new Padrao(Natureza.RECEITA, "Reembolsos", List.of()),
            new Padrao(Natureza.RECEITA, "Outras receitas", List.of()));

    private final CategoriaRepository repositorio;
    private final Clock clock;

    public CategoriasPadrao(CategoriaRepository repositorio, Clock clock) {
        this.repositorio = repositorio;
        this.clock = clock;
    }

    /**
     * Roda na transação de quem gravou o usuário (MANDATORY): usuário e categorias são
     * gravados juntos, ou nada é gravado. Publicar o evento fora de uma transação falha.
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void aoCriarUsuario(UsuarioCriado evento) {
        Instant agora = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        for (Padrao padrao : LISTA) {
            Categoria principal = repositorio.save(new Categoria(evento.usuarioId(), padrao.natureza(), null,
                    padrao.nome(), TextoComparavel.chave(padrao.nome()), agora));
            for (String subcategoria : padrao.subcategorias()) {
                repositorio.save(new Categoria(evento.usuarioId(), padrao.natureza(), principal.getId(),
                        subcategoria, TextoComparavel.chave(subcategoria), agora));
            }
        }
    }
}
