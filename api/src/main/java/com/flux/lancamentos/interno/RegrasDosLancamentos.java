package com.flux.lancamentos.interno;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.flux.categorias.CategoriaResumo;
import com.flux.categorias.CategoriasConsulta;
import com.flux.compartilhado.erro.CampoInvalidoException;
import com.flux.compartilhado.erro.RegraDeNegocioException;
import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import org.springframework.stereotype.Component;

/** Regras comuns a lançamentos, transferências e importados: travas, conta, data, categoria e limites. */
@Component
public class RegrasDosLancamentos {

    static final int LIMITE_POR_MES = 1_000;
    static final int LIMITE_TOTAL = 100_000;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATA_CURTA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MES_E_ANO = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR);

    private final ContasConsulta contas;
    private final CategoriasConsulta categorias;
    private final LancamentoRepository repositorio;
    private final Clock clock;

    public RegrasDosLancamentos(ContasConsulta contas, CategoriasConsulta categorias, LancamentoRepository repositorio,
                                Clock clock) {
        this.contas = contas;
        this.categorias = categorias;
        this.repositorio = repositorio;
        this.clock = clock;
    }

    LocalDate hoje() {
        return LocalDate.now(clock);
    }

    /** Microssegundos, a precisão do TIMESTAMPTZ. */
    Instant agora() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Lançamento: as duas travas do usuário, primeiro a de contas (ADR 0012). Depois delas,
     * ninguém exclui a conta ou a categoria, arquiva a conta nem muda a data do saldo inicial
     * até esta transação terminar.
     */
    void travarContasECategorias(Long usuarioId) {
        contas.travar(usuarioId);
        categorias.travar(usuarioId);
    }

    /** Transferência: sem categoria, só a trava de contas. */
    void travarContas(Long usuarioId) {
        contas.travar(usuarioId);
    }

    /** Data futura vai até 12 meses depois de hoje (decisão 21). */
    void exigirDataNoLimite(LocalDate data) {
        LocalDate limite = hoje().plusMonths(12);
        if (data.isAfter(limite)) {
            throw new CampoInvalidoException("data", "A data pode ir até " + limite.format(DATA_CURTA) + ".");
        }
    }

    /** Conta escolhida no corpo: do usuário (ADR 0007) e ativa (decisão 23). */
    ContaResumo contaParaLancar(Long usuarioId, Long contaId) {
        ContaResumo conta = contas.buscar(usuarioId, contaId)
                .orElseThrow(() -> new RegraDeNegocioException("Conta não encontrada."));
        if (conta.arquivada()) {
            throw new RegraDeNegocioException(conta.nome() + " está arquivada. Reative a conta para lançar nela.",
                    "CONTA_ARQUIVADA");
        }
        return conta;
    }

    /** Conta de um item que já existe: arquivada, o item fica só leitura (decisões 23 e 28). */
    void exigirContaAtiva(Long usuarioId, Long contaId, String item) {
        exigirContaAtivaPara(usuarioId, contaId, "editar ou excluir " + item);
    }

    /** "Poupança está arquivada. Reative a conta para restaurar este lançamento." Devolve a conta. */
    ContaResumo exigirContaAtivaPara(Long usuarioId, Long contaId, String acao) {
        ContaResumo conta = contas.buscar(usuarioId, contaId)
                .orElseThrow(() -> new IllegalStateException("Lançamento sem conta: " + contaId));
        if (conta.arquivada()) {
            throw new RegraDeNegocioException(conta.nome() + " está arquivada. Reative a conta para " + acao + ".",
                    "CONTA_ARQUIVADA");
        }
        return conta;
    }

    /**
     * A data não pode ser anterior à data do saldo inicial (ADR 0003). Com duas contas (a
     * transferência), vale a data mais recente, e a mensagem cita a conta dela.
     */
    void exigirDataDesdeOSaldoInicial(LocalDate data, List<ContaResumo> contasDoLancamento) {
        ContaResumo conta = contasDoLancamento.stream().max(Comparator.comparing(ContaResumo::dataSaldoInicial)).orElseThrow();
        if (data.isBefore(conta.dataSaldoInicial())) {
            throw new CampoInvalidoException("data", "A data precisa ser " + conta.dataSaldoInicial().format(DATA_CURTA)
                    + " ou depois, a data do saldo inicial de " + conta.nome() + ".");
        }
    }

    /**
     * Categoria do usuário (ADR 0007), da natureza do tipo e ativa. A categoria que o
     * lançamento já tem continua valendo mesmo arquivada (decisão 24).
     */
    void exigirCategoria(Long usuarioId, Long categoriaId, TipoLancamento tipo, Long categoriaAtual) {
        CategoriaResumo categoria = categorias.buscar(usuarioId, categoriaId)
                .orElseThrow(() -> new RegraDeNegocioException("Categoria não encontrada.", "CATEGORIA_INVALIDA"));
        if (!categoria.natureza().equals(tipo.name())) {
            String natureza = tipo == TipoLancamento.RECEITA ? "receita" : "despesa";
            throw new RegraDeNegocioException("Escolha uma categoria de " + natureza + ".", "CATEGORIA_INVALIDA");
        }
        if (categoria.arquivada() && !categoriaId.equals(categoriaAtual)) {
            throw new RegraDeNegocioException("Escolha uma categoria ativa.", "CATEGORIA_INVALIDA");
        }
    }

    /** Até 100.000 lançamentos por usuário, contando os dois lados de cada transferência (decisão 34). */
    void exigirLimiteTotal(Long usuarioId, int novos) {
        if (repositorio.countByUsuarioId(usuarioId) + novos > LIMITE_TOTAL) {
            throw new RegraDeNegocioException("Você chegou ao limite de " + String.format(PT_BR, "%,d", LIMITE_TOTAL)
                    + " lançamentos.", "LIMITE_DE_LANCAMENTOS");
        }
    }

    /**
     * Importação (decisão 24): as linhas novas não podem passar do limite total nem do limite de
     * nenhum mês. É tudo ou nada; os ignorados contam, porque são linhas guardadas.
     */
    void exigirLimitesDaImportacao(Long usuarioId, List<LocalDate> datas) {
        if (repositorio.countByUsuarioId(usuarioId) + datas.size() > LIMITE_TOTAL) {
            throw new RegraDeNegocioException("Esta importação passaria do limite de " + String.format(PT_BR, "%,d", LIMITE_TOTAL)
                    + " lançamentos.", "LIMITE_DE_LANCAMENTOS");
        }
        Map<YearMonth, Long> porMes = new TreeMap<>(datas.stream().collect(Collectors.groupingBy(YearMonth::from, Collectors.counting())));
        for (Map.Entry<YearMonth, Long> mes : porMes.entrySet()) {
            YearMonth doMes = mes.getKey();
            if (repositorio.countByUsuarioIdAndDataBetween(usuarioId, doMes.atDay(1), doMes.atEndOfMonth()) + mes.getValue()
                    > LIMITE_POR_MES) {
                throw new RegraDeNegocioException("Esta importação passaria do limite de " + String.format(PT_BR, "%,d", LIMITE_POR_MES)
                        + " lançamentos em " + doMes.atDay(1).format(MES_E_ANO)
                        + ". Desmarque linhas desse mês ou importe um período menor.", "LIMITE_DE_LANCAMENTOS");
            }
        }
    }

    /** Até 1.000 lançamentos com data no mesmo mês (decisão 34): a lista do mês vem inteira. */
    void exigirLimiteDoMes(Long usuarioId, LocalDate data, int novos) {
        YearMonth mes = YearMonth.from(data);
        if (repositorio.countByUsuarioIdAndDataBetween(usuarioId, mes.atDay(1), mes.atEndOfMonth()) + novos > LIMITE_POR_MES) {
            throw new RegraDeNegocioException("Você chegou ao limite de " + String.format(PT_BR, "%,d", LIMITE_POR_MES)
                    + " lançamentos em " + data.format(MES_E_ANO) + ".", "LIMITE_DE_LANCAMENTOS");
        }
    }
}
