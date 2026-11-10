package com.flux.categorias.interno;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import com.flux.categorias.UsoDaCategoria;
import com.flux.categorias.interno.dto.CategoriaResponse;
import com.flux.categorias.interno.dto.CategoriasResponse;
import com.flux.categorias.interno.dto.CriarCategoriaRequest;
import com.flux.categorias.interno.dto.EditarCategoriaRequest;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.compartilhado.texto.TextoComparavel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoriaService {

    static final int LIMITE_DE_CATEGORIAS = 200;

    private final CategoriaRepository repositorio;
    private final ObjectProvider<UsoDaCategoria> usos;
    private final Clock clock;

    public CategoriaService(CategoriaRepository repositorio, ObjectProvider<UsoDaCategoria> usos, Clock clock) {
        this.repositorio = repositorio;
        this.usos = usos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CategoriasResponse listar(Long usuarioId) {
        Arvore arvore = new Arvore(repositorio.findAllByUsuarioId(usuarioId));
        return new CategoriasResponse(arvore.grupo(Natureza.DESPESA), arvore.grupo(Natureza.RECEITA));
    }

    @Transactional(readOnly = true)
    public CategoriaResponse buscar(Long usuarioId, Long id) {
        Arvore arvore = new Arvore(repositorio.findAllByUsuarioId(usuarioId));
        return arvore.resposta(arvore.exigir(id));
    }

    @Transactional
    public CategoriaResponse criar(Long usuarioId, CriarCategoriaRequest pedido) {
        repositorio.travarCategoriasDoUsuario(usuarioId);
        Arvore arvore = new Arvore(repositorio.findAllByUsuarioId(usuarioId));
        if (arvore.todas.size() >= LIMITE_DE_CATEGORIAS) {
            throw new RegraDeNegocioException("Você chegou ao limite de " + LIMITE_DE_CATEGORIAS + " categorias.",
                    "LIMITE_DE_CATEGORIAS");
        }
        Categoria mae = pedido.categoriaMaeId() == null ? null : arvore.maeValida(pedido.categoriaMaeId(), pedido.natureza());
        String chave = TextoComparavel.chave(pedido.nome());
        arvore.exigirNomeLivre(pedido.natureza(), mae, chave, null, "");
        Categoria criada = repositorio.save(new Categoria(usuarioId, pedido.natureza(),
                mae == null ? null : mae.getId(), pedido.nome(), chave, agora()));
        arvore.todas.add(criada);
        return arvore.resposta(criada);
    }

    @Transactional
    public CategoriaResponse atualizar(Long usuarioId, Long id, EditarCategoriaRequest pedido) {
        repositorio.travarCategoriasDoUsuario(usuarioId);
        Arvore arvore = new Arvore(repositorio.findAllByUsuarioId(usuarioId));
        Categoria categoria = arvore.exigir(id);
        if (categoria.arquivada()) {
            throw new RegraDeNegocioException("Reative a categoria antes de editar.", "CATEGORIA_ARQUIVADA");
        }
        Categoria mae = null;
        if (pedido.categoriaMaeId() != null) {
            if (pedido.categoriaMaeId().equals(id)) {
                throw new RegraDeNegocioException("Uma categoria não pode ficar dentro dela mesma.");
            }
            mae = arvore.maeValida(pedido.categoriaMaeId(), categoria.getNatureza());
            if (!arvore.subcategorias(id).isEmpty()) {
                throw new RegraDeNegocioException("Uma categoria com subcategorias não pode ficar dentro de outra.");
            }
        }
        String chave = TextoComparavel.chave(pedido.nome());
        arvore.exigirNomeLivre(categoria.getNatureza(), mae, chave, id, "");
        categoria.alterar(pedido.nome(), chave, mae == null ? null : mae.getId(), agora());
        return arvore.resposta(categoria);
    }

    @Transactional
    public CategoriaResponse arquivar(Long usuarioId, Long id) {
        repositorio.travarCategoriasDoUsuario(usuarioId);
        Arvore arvore = new Arvore(repositorio.findAllByUsuarioId(usuarioId));
        Categoria categoria = arvore.exigir(id);
        if (!categoria.arquivada()) {
            Instant agora = agora();
            categoria.arquivar(agora, false);
            arvore.subcategorias(id).stream()
                    .filter(subcategoria -> !subcategoria.arquivada())
                    .forEach(subcategoria -> subcategoria.arquivar(agora, true));
        }
        return arvore.resposta(categoria);
    }

    @Transactional
    public CategoriaResponse reativar(Long usuarioId, Long id) {
        repositorio.travarCategoriasDoUsuario(usuarioId);
        Arvore arvore = new Arvore(repositorio.findAllByUsuarioId(usuarioId));
        Categoria categoria = arvore.exigir(id);
        if (categoria.arquivada()) {
            Categoria mae = categoria.principal() ? null : arvore.exigir(categoria.getCategoriaMaeId());
            if (mae != null && mae.arquivada()) {
                throw new RegraDeNegocioException("Reative antes a categoria " + mae.getNome() + ".", "MAE_ARQUIVADA");
            }
            arvore.exigirNomeLivre(categoria.getNatureza(), mae, categoria.getNomeChave(), id,
                    " Renomeie a categoria ativa e tente de novo.");
            Instant agora = agora();
            categoria.reativar(agora);
            arvore.subcategorias(id).stream()
                    .filter(Categoria::isArquivadaComAMae)
                    .forEach(subcategoria -> subcategoria.reativar(agora));
        }
        return arvore.resposta(categoria);
    }

    @Transactional
    public void excluir(Long usuarioId, Long id) {
        repositorio.travarCategoriasDoUsuario(usuarioId);
        Arvore arvore = new Arvore(repositorio.findAllByUsuarioId(usuarioId));
        Categoria categoria = arvore.exigir(id);
        List<Categoria> subcategorias = arvore.subcategorias(id);
        List<Long> ids = Stream.concat(Stream.of(categoria), subcategorias.stream()).map(Categoria::getId).toList();
        List<String> emUso = usos.orderedStream()
                .map(uso -> uso.uso(usuarioId, ids))
                .flatMap(Optional::stream)
                .toList();
        if (!emUso.isEmpty()) {
            String quem = subcategorias.isEmpty() ? "tem " : "ela e as subcategorias têm ";
            throw new RegraDeNegocioException("Esta categoria não pode ser excluída porque " + quem + juntar(emUso)
                    + ". Arquive a categoria para tirá-la dos seletores.", "CATEGORIA_EM_USO");
        }
        repositorio.deleteAll(subcategorias);
        repositorio.delete(categoria);
    }

    /** Microssegundos, a precisão do TIMESTAMPTZ: a resposta mostra o mesmo instante que fica gravado. */
    private Instant agora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    /** "a", "a e b", "a, b e c". */
    private static String juntar(List<String> partes) {
        if (partes.size() == 1) {
            return partes.getFirst();
        }
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " e " + partes.getLast();
    }

    /** As categorias de um usuário, com as regras que dependem da árvore. */
    private static final class Arvore {

        private final List<Categoria> todas;
        private final Comparator<Categoria> ordem;

        Arvore(List<Categoria> categorias) {
            this.todas = new ArrayList<>(categorias);
            Comparator<String> alfabetica = TextoComparavel.ordemAlfabetica();
            this.ordem = Comparator.comparing(Categoria::getNome, alfabetica).thenComparing(Categoria::getId);
        }

        Optional<Categoria> encontrar(Long id) {
            return todas.stream().filter(categoria -> categoria.getId().equals(id)).findFirst();
        }

        Categoria exigir(Long id) {
            return encontrar(id).orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada."));
        }

        List<Categoria> subcategorias(Long maeId) {
            return todas.stream().filter(categoria -> maeId.equals(categoria.getCategoriaMaeId())).sorted(ordem).toList();
        }

        /** A categoria principal escolhida num corpo de requisição (ADR 0007: inexistente e de outro usuário dão 422). */
        Categoria maeValida(Long maeId, Natureza natureza) {
            Categoria mae = encontrar(maeId)
                    .orElseThrow(() -> new RegraDeNegocioException("Categoria não encontrada."));
            if (mae.arquivada()) {
                throw new RegraDeNegocioException("Escolha uma categoria principal ativa.");
            }
            if (!mae.principal()) {
                throw new RegraDeNegocioException("Uma subcategoria não pode ter subcategorias.");
            }
            if (mae.getNatureza() != natureza) {
                throw new RegraDeNegocioException("A natureza precisa ser a mesma de " + mae.getNome() + ".");
            }
            return mae;
        }

        /** Nome único entre as irmãs ativas: mesma natureza e mesma principal (ou entre as principais). */
        void exigirNomeLivre(Natureza natureza, Categoria mae, String chave, Long exceto, String complemento) {
            Long maeId = mae == null ? null : mae.getId();
            boolean emUso = todas.stream().anyMatch(outra -> !outra.arquivada()
                    && outra.getNatureza() == natureza
                    && Objects.equals(outra.getCategoriaMaeId(), maeId)
                    && outra.getNomeChave().equals(chave)
                    && !outra.getId().equals(exceto));
            if (emUso) {
                String onde = mae == null ? "entre as categorias principais" : "em " + mae.getNome();
                throw new RegraDeNegocioException("Já existe uma categoria ativa com este nome " + onde + "." + complemento,
                        "NOME_EM_USO");
            }
        }

        CategoriaResponse resposta(Categoria categoria) {
            List<CategoriaResponse> subcategorias = !categoria.principal() ? List.of()
                    : subcategorias(categoria.getId()).stream()
                            .filter(subcategoria -> subcategoria.arquivada() == categoria.arquivada())
                            .map(this::resposta)
                            .toList();
            String nomeDaMae = categoria.principal() ? null : exigir(categoria.getCategoriaMaeId()).getNome();
            return new CategoriaResponse(categoria.getId(), categoria.getNome(), categoria.getNatureza(),
                    categoria.getCategoriaMaeId(), nomeDaMae, categoria.getArquivadaEm(), subcategorias);
        }

        CategoriasResponse.Grupo grupo(Natureza natureza) {
            List<Categoria> daNatureza = todas.stream().filter(categoria -> categoria.getNatureza() == natureza)
                    .sorted(ordem).toList();
            List<CategoriaResponse> ativas = daNatureza.stream()
                    .filter(categoria -> categoria.principal() && !categoria.arquivada())
                    .map(this::resposta)
                    .toList();
            // Arquivadas: principais arquivadas (com as subcategorias dentro) e subcategorias
            // arquivadas cuja principal está ativa. As de uma principal arquivada vão dentro dela.
            List<CategoriaResponse> arquivadas = daNatureza.stream()
                    .filter(Categoria::arquivada)
                    .filter(categoria -> categoria.principal() || !exigir(categoria.getCategoriaMaeId()).arquivada())
                    .map(this::resposta)
                    .toList();
            return new CategoriasResponse.Grupo(ativas, arquivadas);
        }
    }
}
