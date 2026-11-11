package com.flux.contas.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RecursoNaoEncontradoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.compartilhado.texto.TextoComparavel;
import com.flux.contas.LimpezaDaConta;
import com.flux.contas.MovimentoDasContas;
import com.flux.contas.UsoDaConta;
import com.flux.contas.interno.dto.ContaResponse;
import com.flux.contas.interno.dto.ContasResponse;
import com.flux.contas.interno.dto.SalvarContaRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContaService implements SmartInitializingSingleton {

    static final int LIMITE_DE_CONTAS = 50;
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ContaRepository repositorio;
    private final ObjectProvider<MovimentoDasContas> movimento;
    private final ObjectProvider<UsoDaConta> usos;
    private final ObjectProvider<LimpezaDaConta> limpezas;
    private final Clock clock;

    public ContaService(ContaRepository repositorio, ObjectProvider<MovimentoDasContas> movimento,
                        ObjectProvider<UsoDaConta> usos, ObjectProvider<LimpezaDaConta> limpezas, Clock clock) {
        this.repositorio = repositorio;
        this.movimento = movimento;
        this.usos = usos;
        this.limpezas = limpezas;
        this.clock = clock;
    }

    /**
     * Duas implementações de {@link MovimentoDasContas} somariam o saldo duas vezes: a
     * aplicação não sobe. Confere depois de criados todos os beans, para não criar um ciclo
     * com quem implementa (que pode depender de contas).
     */
    @Override
    public void afterSingletonsInstantiated() {
        long implementacoes = movimento.stream().count();
        if (implementacoes > 1) {
            throw new IllegalStateException(
                    "Só pode existir uma implementação de MovimentoDasContas; há " + implementacoes + ".");
        }
    }

    @Transactional(readOnly = true)
    public ContasResponse listar(Long usuarioId) {
        Map<Long, BigDecimal> somas = somasDeHoje(usuarioId);
        Comparator<String> alfabetica = TextoComparavel.ordemAlfabetica();
        List<ContaResponse> todas = repositorio.findAllByUsuarioId(usuarioId).stream()
                .map(conta -> resposta(conta, somas))
                .sorted(Comparator.comparing(ContaResponse::nome, alfabetica).thenComparing(ContaResponse::id))
                .toList();
        List<ContaResponse> ativas = todas.stream().filter(conta -> conta.arquivadaEm() == null).toList();
        List<ContaResponse> arquivadas = todas.stream().filter(conta -> conta.arquivadaEm() != null).toList();
        BigDecimal investimentos = somar(ativas.stream().filter(conta -> conta.tipo() == TipoConta.INVESTIMENTO).toList());
        BigDecimal disponivel = somar(ativas.stream().filter(conta -> conta.tipo() != TipoConta.INVESTIMENTO).toList());
        return new ContasResponse(ativas, arquivadas,
                new ContasResponse.Totais(disponivel.add(investimentos), disponivel, investimentos));
    }

    @Transactional(readOnly = true)
    public ContaResponse buscar(Long usuarioId, Long id) {
        return resposta(encontrar(usuarioId, id), somasDeHoje(usuarioId));
    }

    @Transactional
    public ContaResponse criar(Long usuarioId, SalvarContaRequest pedido) {
        exigirDataAteHoje(pedido.dataSaldoInicial());
        repositorio.travarContasDoUsuario(usuarioId);
        if (repositorio.countByUsuarioId(usuarioId) >= LIMITE_DE_CONTAS) {
            throw new RegraDeNegocioException("Você chegou ao limite de " + LIMITE_DE_CONTAS + " contas.", "LIMITE_DE_CONTAS");
        }
        String chave = TextoComparavel.chave(pedido.nome());
        if (repositorio.existsByUsuarioIdAndNomeChaveAndArquivadaEmIsNull(usuarioId, chave)) {
            throw new RegraDeNegocioException("Já existe uma conta ativa com este nome.", "NOME_EM_USO");
        }
        Conta conta = repositorio.save(new Conta(usuarioId, pedido.nome(), chave, pedido.tipo(),
                pedido.saldoInicial().setScale(2), pedido.dataSaldoInicial(), pedido.cor(), agora()));
        // Conta nova ainda não tem lançamentos: o saldo atual é o inicial.
        return resposta(conta, Map.of());
    }

    @Transactional
    public ContaResponse atualizar(Long usuarioId, Long id, SalvarContaRequest pedido) {
        exigirDataAteHoje(pedido.dataSaldoInicial());
        repositorio.travarContasDoUsuario(usuarioId);
        Conta conta = encontrar(usuarioId, id);
        if (conta.arquivada()) {
            throw new RegraDeNegocioException("Reative a conta antes de editar.", "CONTA_ARQUIVADA");
        }
        String chave = TextoComparavel.chave(pedido.nome());
        if (repositorio.existsByUsuarioIdAndNomeChaveAndArquivadaEmIsNullAndIdNot(usuarioId, chave, id)) {
            throw new RegraDeNegocioException("Já existe uma conta ativa com este nome.", "NOME_EM_USO");
        }
        Optional<LocalDate> primeiroLancamento = Optional.ofNullable(movimento.getIfAvailable())
                .flatMap(movimentoDasContas -> movimentoDasContas.dataDoPrimeiroLancamento(usuarioId, id));
        if (primeiroLancamento.isPresent() && pedido.dataSaldoInicial().isAfter(primeiroLancamento.get())) {
            throw new CampoInvalidoException("dataSaldoInicial", "A conta tem lançamentos desde "
                    + primeiroLancamento.get().format(DATA_CURTA) + ". A data do saldo inicial precisa ser essa ou anterior.");
        }
        conta.alterar(pedido.nome(), chave, pedido.tipo(), pedido.saldoInicial().setScale(2),
                pedido.dataSaldoInicial(), pedido.cor(), agora());
        return resposta(conta, somasDeHoje(usuarioId));
    }

    @Transactional
    public ContaResponse arquivar(Long usuarioId, Long id) {
        repositorio.travarContasDoUsuario(usuarioId);
        Conta conta = encontrar(usuarioId, id);
        if (!conta.arquivada()) {
            conta.arquivar(agora());
        }
        return resposta(conta, somasDeHoje(usuarioId));
    }

    @Transactional
    public ContaResponse reativar(Long usuarioId, Long id) {
        repositorio.travarContasDoUsuario(usuarioId);
        Conta conta = encontrar(usuarioId, id);
        if (conta.arquivada()) {
            if (repositorio.existsByUsuarioIdAndNomeChaveAndArquivadaEmIsNull(usuarioId, conta.getNomeChave())) {
                throw new RegraDeNegocioException(
                        "Já existe uma conta ativa com este nome. Renomeie a conta ativa e tente de novo.", "NOME_EM_USO");
            }
            conta.reativar(agora());
        }
        return resposta(conta, somasDeHoje(usuarioId));
    }

    @Transactional
    public void excluir(Long usuarioId, Long id) {
        repositorio.travarContasDoUsuario(usuarioId);
        Conta conta = encontrar(usuarioId, id);
        List<String> emUso = usos.orderedStream()
                .map(uso -> uso.uso(usuarioId, id))
                .flatMap(Optional::stream)
                .toList();
        if (!emUso.isEmpty()) {
            String uso = juntar(emUso);
            throw new RegraDeNegocioException("Esta conta não pode ser excluída porque tem " + uso
                    + ". Arquive a conta para tirá-la do saldo total e dos seletores.", "CONTA_EM_USO").com("uso", uso);
        }
        // Sem uso: o que não prende a conta sai antes dela (os importados ignorados, Fatia 04a).
        limpezas.orderedStream().forEach(limpeza -> limpeza.antesDeExcluir(usuarioId, id));
        repositorio.delete(conta);
    }

    /** Microssegundos, a precisão do TIMESTAMPTZ: a resposta mostra o mesmo instante que fica gravado. */
    private Instant agora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private Conta encontrar(Long usuarioId, Long id) {
        return repositorio.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada."));
    }

    private void exigirDataAteHoje(LocalDate data) {
        if (data.isAfter(LocalDate.now(clock))) {
            throw new CampoInvalidoException("dataSaldoInicial", "A data não pode ser depois de hoje.");
        }
    }

    private Map<Long, BigDecimal> somasDeHoje(Long usuarioId) {
        MovimentoDasContas movimentoDasContas = movimento.getIfAvailable();
        return movimentoDasContas == null ? Map.of() : movimentoDasContas.somaPorContaAte(usuarioId, LocalDate.now(clock));
    }

    private static ContaResponse resposta(Conta conta, Map<Long, BigDecimal> somas) {
        BigDecimal saldoAtual = conta.getSaldoInicial().add(somas.getOrDefault(conta.getId(), BigDecimal.ZERO))
                .setScale(2, RoundingMode.UNNECESSARY);
        return new ContaResponse(conta.getId(), conta.getNome(), conta.getTipo(), conta.getSaldoInicial(),
                conta.getDataSaldoInicial(), conta.getCor(), saldoAtual, conta.getArquivadaEm());
    }

    private static BigDecimal somar(List<ContaResponse> contas) {
        return contas.stream().map(ContaResponse::saldoAtual).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    /** "a", "a e b", "a, b e c". */
    private static String juntar(List<String> partes) {
        if (partes.size() == 1) {
            return partes.getFirst();
        }
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " e " + partes.getLast();
    }
}
