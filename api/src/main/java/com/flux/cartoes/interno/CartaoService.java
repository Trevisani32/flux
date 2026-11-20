package com.flux.cartoes.interno;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.flux.cartoes.CalendarioDoCartao;
import com.flux.cartoes.CartaoResumo;
import com.flux.cartoes.MovimentoDosCartoes;
import com.flux.cartoes.TrocaDeDias;
import com.flux.cartoes.UsoDoCartao;
import com.flux.cartoes.interno.dto.CartaoResponse;
import com.flux.cartoes.interno.dto.CartoesResponse;
import com.flux.cartoes.interno.dto.DiasRequest;
import com.flux.cartoes.interno.dto.PreviaDosDiasResponse;
import com.flux.cartoes.interno.dto.SalvarCartaoRequest;
import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O cadastro de cartões (decisões 1 a 7). Criar e editar pegam a trava de contas e depois a de cartões,
 * porque gravam um uso da conta de pagamento (decisão 49); arquivar, reativar e excluir, só a de cartões.
 */
@Service
public class CartaoService {

    static final int LIMITE_DE_CARTOES = 20;
    private static final String CONTA_QUE_PAGA = "Escolha uma conta corrente, poupança ou carteira.";

    private final CartaoRepository repositorio;
    private final ContasConsulta contas;
    private final ObjectProvider<MovimentoDosCartoes> movimento;
    private final ObjectProvider<UsoDoCartao> usos;
    private final ObjectProvider<TrocaDeDias> trocaDeDias;
    private final Clock clock;

    public CartaoService(CartaoRepository repositorio, ContasConsulta contas, ObjectProvider<MovimentoDosCartoes> movimento,
                         ObjectProvider<UsoDoCartao> usos, ObjectProvider<TrocaDeDias> trocaDeDias, Clock clock) {
        this.repositorio = repositorio;
        this.contas = contas;
        this.movimento = movimento;
        this.usos = usos;
        this.trocaDeDias = trocaDeDias;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CartoesResponse listar(Long usuarioId) {
        List<Cartao> cartoes = repositorio.findAllByUsuarioId(usuarioId);
        Map<Long, ContaResumo> contasDoUsuario = contasDoUsuario(usuarioId);
        Map<Long, MovimentoDosCartoes.Movimento> movimentos = movimentos(usuarioId, cartoes);
        Comparator<String> alfabetica = TextoComparavel.ordemAlfabetica();
        List<CartaoResponse> todos = cartoes.stream()
                .map(cartao -> resposta(cartao, contasDoUsuario, movimentos))
                .sorted(Comparator.comparing(CartaoResponse::nome, alfabetica).thenComparing(CartaoResponse::id))
                .toList();
        return new CartoesResponse(todos.stream().filter(cartao -> cartao.arquivadoEm() == null).toList(),
                todos.stream().filter(cartao -> cartao.arquivadoEm() != null).toList());
    }

    @Transactional(readOnly = true)
    public CartaoResponse buscar(Long usuarioId, Long id) {
        return resposta(usuarioId, encontrar(usuarioId, id));
    }

    @Transactional
    public CartaoResponse criar(Long usuarioId, SalvarCartaoRequest pedido) {
        exigirDias(pedido.diaFechamento(), pedido.diaVencimento());
        contas.travar(usuarioId);
        repositorio.travarCartoesDoUsuario(usuarioId);
        if (repositorio.countByUsuarioId(usuarioId) >= LIMITE_DE_CARTOES) {
            throw new RegraDeNegocioException("Você chegou ao limite de " + LIMITE_DE_CARTOES + " cartões.", "LIMITE_DE_CARTOES");
        }
        String chave = TextoComparavel.chave(pedido.nome());
        if (repositorio.existsByUsuarioIdAndNomeChaveAndArquivadoEmIsNull(usuarioId, chave)) {
            throw new RegraDeNegocioException("Já existe um cartão ativo com este nome.", "NOME_EM_USO");
        }
        exigirContaQuePaga(usuarioId, pedido.contaPagamentoId(), null);
        Cartao cartao = new Cartao(usuarioId, agora());
        aplicar(cartao, pedido, chave);
        repositorio.save(cartao);
        return resposta(usuarioId, cartao);
    }

    /** Decisão 7: com os dias novos, a aberta e as fechadas ficam; as faturas seguintes mudam pela {@link TrocaDeDias}. */
    @Transactional
    public CartaoResponse atualizar(Long usuarioId, Long id, SalvarCartaoRequest pedido) {
        exigirDias(pedido.diaFechamento(), pedido.diaVencimento());
        contas.travar(usuarioId);
        repositorio.travarCartoesDoUsuario(usuarioId);
        Cartao cartao = encontrar(usuarioId, id);
        if (cartao.arquivado()) {
            throw new RegraDeNegocioException("Reative o cartão antes de editar.", "CARTAO_ARQUIVADO");
        }
        String chave = TextoComparavel.chave(pedido.nome());
        if (repositorio.existsByUsuarioIdAndNomeChaveAndArquivadoEmIsNullAndIdNot(usuarioId, chave, id)) {
            throw new RegraDeNegocioException("Já existe um cartão ativo com este nome.", "NOME_EM_USO");
        }
        exigirContaQuePaga(usuarioId, pedido.contaPagamentoId(), cartao.getContaPagamentoId());
        if (cartao.getDiaFechamento() != pedido.diaFechamento() || cartao.getDiaVencimento() != pedido.diaVencimento()) {
            CartaoResumo antes = ConsultaDeCartoes.resumo(cartao);
            trocaDeDias.ifAvailable(troca -> troca.aplicar(usuarioId, antes, pedido.diaFechamento(), pedido.diaVencimento(), hoje()));
        }
        aplicar(cartao, pedido, chave);
        return resposta(usuarioId, cartao);
    }

    /** O aviso do diálogo de edição (decisão 7), sem gravar nada. */
    @Transactional(readOnly = true)
    public PreviaDosDiasResponse previaDosDias(Long usuarioId, Long id, DiasRequest pedido) {
        exigirDias(pedido.diaFechamento(), pedido.diaVencimento());
        Cartao cartao = encontrar(usuarioId, id);
        TrocaDeDias troca = trocaDeDias.getIfAvailable();
        if (troca == null || (cartao.getDiaFechamento() == pedido.diaFechamento() && cartao.getDiaVencimento() == pedido.diaVencimento())) {
            return new PreviaDosDiasResponse(null, false);
        }
        // Sem lançamento e sem ajuste de fechamento, a prévia vem vazia: os dias novos valem já (a exceção da decisão 7).
        return troca.previa(usuarioId, ConsultaDeCartoes.resumo(cartao), pedido.diaFechamento(), pedido.diaVencimento(), hoje())
                .map(previa -> new PreviaDosDiasResponse(new PreviaDosDiasResponse.Troca(previa.abertaMes().toString(),
                        previa.abertaFechamento(), previa.primeiraMes().toString(), previa.primeiraInicio(), previa.primeiraFechamento(),
                        previa.primeiraVencimento()), false))
                .orElse(new PreviaDosDiasResponse(null, true));
    }

    /** Decisão 5: arquivar sempre pode; as compras e as faturas dele ficam só leitura. */
    @Transactional
    public CartaoResponse arquivar(Long usuarioId, Long id) {
        repositorio.travarCartoesDoUsuario(usuarioId);
        Cartao cartao = encontrar(usuarioId, id);
        if (!cartao.arquivado()) {
            cartao.arquivar(agora());
        }
        return resposta(usuarioId, cartao);
    }

    /** Decisão 5: reativar é recusado com outro cartão ativo de mesmo nome. */
    @Transactional
    public CartaoResponse reativar(Long usuarioId, Long id) {
        repositorio.travarCartoesDoUsuario(usuarioId);
        Cartao cartao = encontrar(usuarioId, id);
        if (cartao.arquivado()) {
            if (repositorio.existsByUsuarioIdAndNomeChaveAndArquivadoEmIsNull(usuarioId, cartao.getNomeChave())) {
                throw new RegraDeNegocioException("Já existe um cartão ativo chamado " + cartao.getNome()
                        + ". Renomeie um deles para reativar.", "NOME_EM_USO");
            }
            cartao.reativar(agora());
        }
        return resposta(usuarioId, cartao);
    }

    /** Decisão 5: só sem compras e sem estornos; as faturas vazias saem junto (CASCADE). */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        repositorio.travarCartoesDoUsuario(usuarioId);
        Cartao cartao = encontrar(usuarioId, id);
        List<String> emUso = usos.orderedStream().map(uso -> uso.uso(usuarioId, id)).flatMap(Optional::stream).toList();
        if (!emUso.isEmpty()) {
            String uso = String.join(" e ", emUso);
            throw new RegraDeNegocioException("Este cartão não pode ser excluído porque tem " + uso
                    + ". Arquive o cartão para tirá-lo dos seletores.", "CARTAO_EM_USO").com("uso", uso);
        }
        repositorio.delete(cartao);
    }

    /** Microssegundos, a precisão do TIMESTAMPTZ. */
    private Instant agora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private LocalDate hoje() {
        return LocalDate.now(clock);
    }

    private Cartao encontrar(Long usuarioId, Long id) {
        return repositorio.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cartão não encontrado."));
    }

    /** Decisão 3: o banco também recusa (ck_cartoes_dias). */
    private static void exigirDias(int diaFechamento, int diaVencimento) {
        if (!CalendarioDoCartao.diasValidos(diaFechamento, diaVencimento)) {
            throw new CampoInvalidoException("diaVencimento",
                    "Com o fechamento no dia 28 ou depois, o vencimento precisa ser num dia antes dele, no mês seguinte.");
        }
    }

    /**
     * Decisão 4: uma conta do usuário, ativa, corrente, poupança ou carteira. Na edição, a conta que o cartão
     * já tem pode ficar, mesmo arquivada (como a categoria arquivada no lançamento).
     */
    private void exigirContaQuePaga(Long usuarioId, Long contaId, Long contaAtual) {
        ContaResumo conta = contas.buscar(usuarioId, contaId)
                .orElseThrow(() -> new RegraDeNegocioException("Conta não encontrada.", "CONTA_INVALIDA"));
        if (contaId.equals(contaAtual)) {
            return;
        }
        if (conta.arquivada()) {
            throw new RegraDeNegocioException(conta.nome() + " está arquivada. Escolha uma conta ativa.", "CONTA_INVALIDA");
        }
        if ("INVESTIMENTO".equals(conta.tipo())) {
            throw new RegraDeNegocioException(CONTA_QUE_PAGA, "CONTA_INVALIDA");
        }
    }

    private void aplicar(Cartao cartao, SalvarCartaoRequest pedido, String chave) {
        cartao.alterar(pedido.nome(), chave, pedido.limite(), pedido.diaFechamento(), pedido.diaVencimento(),
                pedido.contaPagamentoId(), pedido.cor(), agora());
    }

    private CartaoResponse resposta(Long usuarioId, Cartao cartao) {
        return resposta(cartao, contasDoUsuario(usuarioId), movimentos(usuarioId, List.of(cartao)));
    }

    private Map<Long, ContaResumo> contasDoUsuario(Long usuarioId) {
        return contas.listar(usuarioId).stream().collect(Collectors.toMap(ContaResumo::id, Function.identity()));
    }

    /** Sem quem implemente (sem lançamentos), a fatura aberta sai das datas padrão, zerada. */
    private Map<Long, MovimentoDosCartoes.Movimento> movimentos(Long usuarioId, List<Cartao> cartoes) {
        List<CartaoResumo> resumos = cartoes.stream().map(ConsultaDeCartoes::resumo).toList();
        MovimentoDosCartoes implementacao = movimento.getIfAvailable();
        if (implementacao != null) {
            return implementacao.movimento(usuarioId, resumos, hoje());
        }
        return resumos.stream().collect(Collectors.toMap(CartaoResumo::id, cartao -> {
            CalendarioDoCartao calendario = cartao.calendario();
            YearMonth aberta = calendario.faturaDe(hoje(), Map.of());
            return new MovimentoDosCartoes.Movimento(semLancamento(calendario, aberta), semLancamento(calendario, aberta.plusMonths(1)),
                    BigDecimal.ZERO.setScale(2), 0);
        }));
    }

    private static MovimentoDosCartoes.FaturaResumida semLancamento(CalendarioDoCartao calendario, YearMonth mes) {
        CalendarioDoCartao.Datas datas = calendario.datas(mes, Map.of());
        return new MovimentoDosCartoes.FaturaResumida(mes, calendario.inicio(mes, Map.of()), datas.fechamento(), datas.vencimento(),
                BigDecimal.ZERO.setScale(2), 0);
    }

    private static CartaoResponse resposta(Cartao cartao, Map<Long, ContaResumo> contas, Map<Long, MovimentoDosCartoes.Movimento> movimentos) {
        ContaResumo conta = contas.get(cartao.getContaPagamentoId());
        MovimentoDosCartoes.Movimento movimento = movimentos.get(cartao.getId());
        return new CartaoResponse(cartao.getId(), cartao.getNome(), cartao.getLimite(), cartao.getDiaFechamento(),
                cartao.getDiaVencimento(), cartao.getContaPagamentoId(), conta.nome(), conta.arquivada(), cartao.getCor(),
                cartao.getArquivadoEm(), fatura(movimento.aberta()), fatura(movimento.proxima()), movimento.emAberto(),
                movimento.lancamentosEmAberto());
    }

    private static CartaoResponse.Fatura fatura(MovimentoDosCartoes.FaturaResumida fatura) {
        return new CartaoResponse.Fatura(fatura.mes().toString(), fatura.inicio(), fatura.fechamento(), fatura.vencimento(),
                fatura.total(), fatura.lancamentos());
    }
}
