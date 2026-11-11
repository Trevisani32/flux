package com.flux.categorizacao.interno;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import com.flux.categorizacao.interno.dto.OrdemRequest;
import com.flux.categorizacao.interno.dto.PreviaDaRegraRequest;
import com.flux.categorizacao.interno.dto.PreviaDaRegraResponse;
import com.flux.categorizacao.interno.dto.RegraResponse;
import com.flux.categorizacao.interno.dto.SalvarRegraRequest;
import com.flux.categorizacao.interno.dto.SugestaoResponse;
import com.flux.categorizacao.interno.dto.SugestoesRequest;
import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import com.flux.lancamentos.LancamentosParaRegras;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O cadastro das regras (decisões 2 a 13 e 23 a 25), a prévia do diálogo (decisão 12) e o texto
 * sugerido para criar uma regra (decisões 20 a 22). Toda escrita pega a trava de contas e depois a de
 * categorias (ADR 0012) e só então confere a conta e a categoria.
 */
@Service
public class RegraService {

    static final int LIMITE = 200;
    static final int LIMITE_DE_SUGESTOES = 5;
    private static final int EXEMPLOS = 3;

    private final RegraRepository repositorio;
    private final ContasConsulta contas;
    private final CategoriasConsulta categorias;
    private final LancamentosParaRegras lancamentos;
    private final Clock clock;

    public RegraService(RegraRepository repositorio, ContasConsulta contas, CategoriasConsulta categorias,
                        LancamentosParaRegras lancamentos, Clock clock) {
        this.repositorio = repositorio;
        this.contas = contas;
        this.categorias = categorias;
        this.lancamentos = lancamentos;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RegraResponse> listar(Long usuarioId) {
        Nomes nomes = nomes(usuarioId);
        return repositorio.findAllByUsuarioIdOrderByPosicao(usuarioId).stream().map(nomes::resposta).toList();
    }

    /** A regra nova entra em 1º (decisão 10); as outras descem uma posição. */
    @Transactional
    public RegraResponse criar(Long usuarioId, SalvarRegraRequest pedido) {
        travar(usuarioId);
        List<Regra> regras = repositorio.findAllByUsuarioIdOrderByPosicao(usuarioId);
        if (regras.size() >= LIMITE) {
            throw new RegraDeNegocioException("Você chegou ao limite de " + LIMITE + " regras.", "LIMITE_DE_REGRAS");
        }
        Dados dados = validar(usuarioId, pedido, null, regras);
        regras.forEach(regra -> regra.mudarPosicao(regra.getPosicao() + 1));
        Regra regra = new Regra(usuarioId, 1, agora());
        aplicar(regra, dados);
        repositorio.save(regra);
        return nomes(usuarioId).resposta(regra);
    }

    @Transactional
    public RegraResponse atualizar(Long usuarioId, Long id, SalvarRegraRequest pedido) {
        travar(usuarioId);
        List<Regra> regras = repositorio.findAllByUsuarioIdOrderByPosicao(usuarioId);
        Regra regra = encontrar(regras, id);
        aplicar(regra, validar(usuarioId, pedido, regra, regras));
        return nomes(usuarioId).resposta(regra);
    }

    /** As de baixo sobem uma posição. Os lançamentos que a regra categorizou ficam como estão. */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        travar(usuarioId);
        List<Regra> regras = repositorio.findAllByUsuarioIdOrderByPosicao(usuarioId);
        Regra regra = encontrar(regras, id);
        repositorio.delete(regra);
        regras.stream().filter(outra -> outra.getPosicao() > regra.getPosicao())
                .forEach(outra -> outra.mudarPosicao(outra.getPosicao() - 1));
    }

    /**
     * A ordem nova (decisão 9): a lista precisa ter exatamente as regras do usuário, cada uma uma vez.
     * Se outra aba criou ou excluiu uma regra, a lista não bate e nada muda.
     */
    @Transactional
    public List<RegraResponse> reordenar(Long usuarioId, OrdemRequest pedido) {
        travar(usuarioId);
        List<Regra> regras = repositorio.findAllByUsuarioIdOrderByPosicao(usuarioId);
        Map<Long, Regra> porId = regras.stream().collect(Collectors.toMap(Regra::getId, Function.identity()));
        if (pedido.ids().size() != regras.size() || !porId.keySet().equals(new HashSet<>(pedido.ids()))) {
            throw new RegraDeNegocioException("A lista de regras mudou. Recarregue a página e tente de novo.", "ORDEM_DESATUALIZADA");
        }
        for (int indice = 0; indice < pedido.ids().size(); indice++) {
            porId.get(pedido.ids().get(indice)).mudarPosicao(indice + 1);
        }
        Nomes nomes = nomes(usuarioId);
        return regras.stream().sorted(Comparator.comparingInt(Regra::getPosicao)).map(nomes::resposta).toList();
    }

    /**
     * Quantos lançamentos a condição pega, sem olhar as outras regras (decisão 12): receitas e despesas
     * não excluídas de contas ativas, na natureza da categoria, se ela veio. A categoria e a conta podem
     * estar arquivadas (a regra que se edita pode ter uma), mas precisam ser do usuário: senão, 422.
     */
    @Transactional(readOnly = true)
    public PreviaDaRegraResponse previa(Long usuarioId, PreviaDaRegraRequest pedido) {
        String chave = chaveValida(TextoComparavel.limparEspacos(pedido.texto()));
        String natureza = pedido.categoriaId() == null ? null : categoria(usuarioId, pedido.categoriaId()).natureza();
        if (pedido.contaId() != null) {
            conta(usuarioId, pedido.contaId());
        }
        Set<Long> ativas = contas.listar(usuarioId).stream().filter(conta -> !conta.arquivada()).map(ContaResumo::id)
                .collect(Collectors.toSet());
        int casam = 0;
        int semCategoria = 0;
        List<PreviaDaRegraResponse.Exemplo> exemplos = new ArrayList<>();
        for (LancamentosParaRegras.Candidato candidato : lancamentos.receitasEDespesas(usuarioId)) {
            if (!ativas.contains(candidato.contaId()) || (pedido.contaId() != null && !pedido.contaId().equals(candidato.contaId()))
                    || (natureza != null && !natureza.equals(RegrasAtivas.natureza(candidato.valor())))
                    || !pedido.condicao().casa(Comparacao.chave(candidato.textoParaRegras()), chave)) {
                continue;
            }
            casam++;
            if (candidato.categoriaId() == null) {
                semCategoria++;
            }
            if (exemplos.size() < EXEMPLOS) {
                exemplos.add(new PreviaDaRegraResponse.Exemplo(candidato.data(), candidato.textoParaRegras(), candidato.valor()));
            }
        }
        return new PreviaDaRegraResponse(casam, semCategoria, exemplos);
    }

    /**
     * As regras prontas para "Criar regra" (decisões 20 a 22), no máximo 5: só para categoria ativa do
     * usuário, sem regra ativa que já case com a descrição do extrato e sem regra igual (mesmo "contém",
     * mesmo texto, todas as contas). Cada texto sai uma vez. Uma categoria ou conta que não é do usuário
     * dá 422 (ADR 0007); a categoria arquivada só fica sem sugestão.
     */
    @Transactional(readOnly = true)
    public List<SugestaoResponse> sugestoes(Long usuarioId, SugestoesRequest pedido) {
        Map<Long, CategoriaResumo> doUsuario = mapaDeCategorias(usuarioId);
        Map<Long, ContaResumo> contasDoUsuario = mapaDeContas(usuarioId);
        for (SugestoesRequest.Item item : pedido.itens()) {
            if (!doUsuario.containsKey(item.categoriaId())) {
                throw categoriaNaoEncontrada();
            }
            if (!contasDoUsuario.containsKey(item.contaId())) {
                throw contaNaoEncontrada();
            }
        }
        List<Regra> regras = repositorio.findAllByUsuarioIdOrderByPosicao(usuarioId);
        RegrasAtivas ativas = new RegrasAtivas(regras, doUsuario, contasDoUsuario);
        List<SugestaoResponse> sugestoes = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        for (SugestoesRequest.Item item : pedido.itens()) {
            if (sugestoes.size() == LIMITE_DE_SUGESTOES) {
                break;
            }
            CategoriaResumo categoria = doUsuario.get(item.categoriaId());
            String doExtrato = TextoComparavel.limparEspacos(item.descricaoBanco());
            if (categoria.arquivada() || ativas.primeira(item.contaId(), categoria.natureza(), Comparacao.chave(doExtrato)) != null) {
                continue;
            }
            String texto = TextoSugerido.de(doExtrato);
            String chave = Comparacao.chave(texto);
            boolean igual = regras.stream().anyMatch(regra -> regra.getNatureza().equals(categoria.natureza())
                    && regra.getCondicao() == Condicao.CONTEM && regra.getTextoChave().equals(chave) && regra.getContaId() == null);
            if (igual || !Comparacao.temLetrasOuNumerosSuficientes(chave) || !vistas.add(categoria.natureza() + ":" + chave)) {
                continue;
            }
            String descricao = item.descricao() == null || item.descricao().isBlank()
                    || Comparacao.chave(item.descricao()).equals(Comparacao.chave(doExtrato)) ? null
                    : TextoComparavel.limparEspacos(item.descricao());
            sugestoes.add(new SugestaoResponse(Condicao.CONTEM.name(), texto, categoria.id(), categoria.nome(), categoria.nomeDaMae(),
                    categoria.natureza(), descricao, doExtrato));
        }
        return sugestoes;
    }

    /** Contas e depois categorias (ADR 0012): ninguém exclui a categoria nem a conta da regra no meio. */
    private void travar(Long usuarioId) {
        contas.travar(usuarioId);
        categorias.travar(usuarioId);
    }

    /**
     * O texto com 2 letras ou números, a categoria e a conta do usuário e ativas (ao editar, a arquivada
     * que a regra já tem pode ficar, decisão 11) e nenhuma regra igual.
     */
    private Dados validar(Long usuarioId, SalvarRegraRequest pedido, Regra atual, List<Regra> regras) {
        String texto = TextoComparavel.limparEspacos(pedido.texto());
        String chave = chaveValida(texto);
        CategoriaResumo categoria = categoria(usuarioId, pedido.categoriaId());
        if (categoria.arquivada() && (atual == null || !categoria.id().equals(atual.getCategoriaId()))) {
            throw new RegraDeNegocioException("Escolha uma categoria ativa.", "CATEGORIA_INVALIDA");
        }
        if (pedido.contaId() != null) {
            ContaResumo conta = conta(usuarioId, pedido.contaId());
            if (conta.arquivada() && (atual == null || !conta.id().equals(atual.getContaId()))) {
                throw new RegraDeNegocioException("Escolha uma conta ativa.", "CONTA_INVALIDA");
            }
        }
        regras.stream()
                .filter(outra -> atual == null || !outra.getId().equals(atual.getId()))
                .filter(outra -> outra.getNatureza().equals(categoria.natureza()) && outra.getCondicao() == pedido.condicao()
                        && outra.getTextoChave().equals(chave) && Objects.equals(outra.getContaId(), pedido.contaId()))
                .findFirst()
                .ifPresent(outra -> {
                    throw new RegraDeNegocioException("Já existe uma regra igual: a " + outra.getPosicao() + "ª, "
                            + outra.getCondicao().rotulo() + " \"" + outra.getTexto() + "\".", "REGRA_REPETIDA");
                });
        String descricao = pedido.descricao() == null || pedido.descricao().isBlank() ? null
                : TextoComparavel.limparEspacos(pedido.descricao());
        return new Dados(pedido.condicao(), texto, pedido.contaId(), categoria.natureza(), categoria.id(), descricao);
    }

    /** A categoria do usuário, arquivada ou não; inexistente ou de outro usuário dá 422 (ADR 0007). */
    private CategoriaResumo categoria(Long usuarioId, Long categoriaId) {
        return categorias.buscar(usuarioId, categoriaId).orElseThrow(RegraService::categoriaNaoEncontrada);
    }

    /** A conta do usuário, arquivada ou não; inexistente ou de outro usuário dá 422 (ADR 0007). */
    private ContaResumo conta(Long usuarioId, Long contaId) {
        return contas.buscar(usuarioId, contaId).orElseThrow(RegraService::contaNaoEncontrada);
    }

    private static RegraDeNegocioException categoriaNaoEncontrada() {
        return new RegraDeNegocioException("Categoria não encontrada.", "CATEGORIA_INVALIDA");
    }

    private static RegraDeNegocioException contaNaoEncontrada() {
        return new RegraDeNegocioException("Conta não encontrada.", "CONTA_INVALIDA");
    }

    private static String chaveValida(String texto) {
        String chave = Comparacao.chave(texto);
        if (!Comparacao.temLetrasOuNumerosSuficientes(chave)) {
            throw new CampoInvalidoException("texto", "Use ao menos 2 letras ou números.");
        }
        return chave;
    }

    private void aplicar(Regra regra, Dados dados) {
        regra.alterar(dados.condicao(), dados.texto(), dados.contaId(), dados.natureza(), dados.categoriaId(), dados.descricao(),
                agora());
    }

    private static Regra encontrar(List<Regra> regras, Long id) {
        return regras.stream().filter(regra -> regra.getId().equals(id)).findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Regra não encontrada."));
    }

    private Nomes nomes(Long usuarioId) {
        return new Nomes(mapaDeCategorias(usuarioId), mapaDeContas(usuarioId));
    }

    private Map<Long, CategoriaResumo> mapaDeCategorias(Long usuarioId) {
        return categorias.listar(usuarioId).stream().collect(Collectors.toMap(CategoriaResumo::id, Function.identity()));
    }

    private Map<Long, ContaResumo> mapaDeContas(Long usuarioId) {
        return contas.listar(usuarioId).stream().collect(Collectors.toMap(ContaResumo::id, Function.identity()));
    }

    /** Microssegundos, a precisão do TIMESTAMPTZ. */
    private Instant agora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private record Dados(Condicao condicao, String texto, Long contaId, String natureza, Long categoriaId, String descricao) {
    }

    /** Os nomes de categorias e contas, lidos uma vez por pedido, para as respostas. */
    private record Nomes(Map<Long, CategoriaResumo> categorias, Map<Long, ContaResumo> contas) {

        RegraResponse resposta(Regra regra) {
            CategoriaResumo categoria = categorias.get(regra.getCategoriaId());
            ContaResumo conta = regra.getContaId() == null ? null : contas.get(regra.getContaId());
            return new RegraResponse(regra.getId(), regra.getPosicao(), regra.getCondicao().name(), regra.getTexto(), regra.getContaId(),
                    conta == null ? null : conta.nome(), conta != null && conta.arquivada(), regra.getNatureza(), regra.getCategoriaId(),
                    categoria.nome(), categoria.nomeDaMae(), categoria.arquivada(), regra.getDescricao(),
                    RegrasAtivas.pausada(regra, categorias, contas));
        }
    }
}
