package com.flux.relatorios.interno;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As consultas do relatório (ADR 0001: o que atravessa módulos fica em relatorios, só de leitura; ADR 0020). Toda consulta
 * filtra por {@code usuario_id} (ADR 0007) e recebe as datas prontas, do Clock: nenhuma usa a data do banco. Os importados
 * ignorados ficam de fora, como na lista e no saldo (Fatia 04a). Receitas e despesas são as da lista de Lançamentos do mês
 * sem filtro: as de conta e as de fatura (a parcela e o estorno têm a data do vencimento da fatura), de contas e cartões
 * ativos ou arquivados; transferências e pagamentos de fatura têm outro tipo e ficam de fora.
 */
@Component
public class ConsultasDoRelatorio {

    /** Receitas e despesas de um mês, sem sinal; {@code despesasNoCartao} conta as de fatura. */
    public record MovimentoDoMes(YearMonth mes, BigDecimal receitas, BigDecimal despesas, long quantidadeDeReceitas,
                                 long quantidadeDeDespesas, long despesasNoCartao) {
    }

    /** O que saiu numa categoria num mês, sem sinal; {@code categoriaId} nulo é "Sem categoria". */
    public record GastoNoMes(Long categoriaId, YearMonth mes, BigDecimal valor) {
    }

    /** Uma categoria de despesa, ativa ou arquivada: principal ({@code maeId} nulo) ou subcategoria. */
    public record CategoriaDeDespesa(Long id, String nome, Long maeId, boolean arquivada) {
    }

    /**
     * O saldo total no {@code dia} (o último do mês ou hoje, o que vier antes), com as contas que entram nele. {@code saldo}
     * e {@code investimentos} são nulos quando o dia é anterior ao primeiro saldo inicial do usuário.
     */
    public record SaldoNoDia(YearMonth mes, LocalDate dia, BigDecimal saldo, BigDecimal investimentos, int contas,
                             int contasDeInvestimento) {
    }

    // O primeiro dado: o saldo inicial mais antigo ou o lançamento mais antigo (inclusive parcela). LEAST ignora o nulo.
    private static final String PRIMEIRA_DATA = """
            SELECT least(
                (SELECT min(c.data_saldo_inicial) FROM contas c WHERE c.usuario_id = :usuarioId),
                (SELECT min(l.data) FROM lancamentos l WHERE l.usuario_id = :usuarioId AND l.ignorado_em IS NULL)
            ) AS primeira
            """;

    private static final String MOVIMENTO_POR_MES = """
            SELECT to_char(l.data, 'YYYY-MM') AS mes,
                   COALESCE(sum(l.valor) FILTER (WHERE l.tipo = 'RECEITA'), 0) AS receitas,
                   COALESCE(-sum(l.valor) FILTER (WHERE l.tipo = 'DESPESA'), 0) AS despesas,
                   count(*) FILTER (WHERE l.tipo = 'RECEITA') AS quantidade_de_receitas,
                   count(*) FILTER (WHERE l.tipo = 'DESPESA') AS quantidade_de_despesas,
                   count(*) FILTER (WHERE l.tipo = 'DESPESA' AND l.fatura_id IS NOT NULL) AS despesas_no_cartao
            FROM lancamentos l
            WHERE l.usuario_id = :usuarioId
              AND l.ignorado_em IS NULL
              AND l.tipo IN ('RECEITA', 'DESPESA')
              AND l.data BETWEEN :inicio AND :fim
            GROUP BY to_char(l.data, 'YYYY-MM')
            """;

    private static final String DESPESAS_POR_CATEGORIA = """
            SELECT l.categoria_id, to_char(l.data, 'YYYY-MM') AS mes, -sum(l.valor) AS valor
            FROM lancamentos l
            WHERE l.usuario_id = :usuarioId
              AND l.ignorado_em IS NULL
              AND l.tipo = 'DESPESA'
              AND l.data BETWEEN :inicio AND :fim
            GROUP BY l.categoria_id, to_char(l.data, 'YYYY-MM')
            """;

    private static final String CATEGORIAS_DE_DESPESA = """
            SELECT c.id, c.nome, c.categoria_mae_id, c.arquivada_em IS NOT NULL AS arquivada
            FROM categorias c
            WHERE c.usuario_id = :usuarioId AND c.natureza = 'DESPESA'
            """;

    // Um dia por mês, de :inicio a :fim (os primeiros dias dos meses): o último dia do mês ou hoje, o que vier antes. O saldo
    // de cada conta no dia = saldo inicial + o que ela moveu antes do primeiro mês (lido uma vez) + o acumulado do que moveu
    // em cada mês até o dia dele (window function): cada lançamento é lido uma vez por pedido. A conta entra a partir do dia
    // do saldo inicial e até a véspera do dia em que foi arquivada, no fuso de São Paulo: a arquivada em 15/08 entra em 31/07
    // e não em 31/08, e a arquivada hoje não entra hoje (o saldo de hoje é o do Início). O generate_series usa timestamp sem
    // fuso, e as datas nunca passam pelo fuso da sessão.
    private static final String SALDOS_NO_FIM_DOS_MESES = """
            WITH meses AS (
                SELECT CAST(g.mes AS date) AS primeiro_dia,
                       least(CAST(g.mes + interval '1 month' - interval '1 day' AS date), CAST(:hoje AS date)) AS dia
                FROM generate_series(CAST(:inicio AS timestamp), CAST(:fim AS timestamp), interval '1 month') AS g(mes)
            ),
            contas_do_usuario AS (
                SELECT c.id, c.tipo, c.saldo_inicial, c.data_saldo_inicial,
                       CAST(c.arquivada_em AT TIME ZONE 'America/Sao_Paulo' AS date) AS arquivada_no_dia
                FROM contas c
                WHERE c.usuario_id = :usuarioId
            ),
            antes AS (
                SELECT l.conta_id, sum(l.valor) AS soma
                FROM lancamentos l
                WHERE l.usuario_id = :usuarioId AND l.conta_id IS NOT NULL AND l.ignorado_em IS NULL
                  AND l.data < CAST(:inicio AS date)
                GROUP BY l.conta_id
            ),
            no_mes AS (
                SELECT l.conta_id, m.primeiro_dia, sum(l.valor) AS soma
                FROM meses m
                JOIN lancamentos l
                  ON l.usuario_id = :usuarioId AND l.conta_id IS NOT NULL AND l.ignorado_em IS NULL
                 AND l.data BETWEEN m.primeiro_dia AND m.dia
                GROUP BY l.conta_id, m.primeiro_dia
            ),
            saldos AS (
                SELECT m.primeiro_dia, c.id, c.tipo, c.data_saldo_inicial, c.arquivada_no_dia,
                       c.saldo_inicial + COALESCE(a.soma, 0)
                           + COALESCE(sum(n.soma) OVER (PARTITION BY c.id ORDER BY m.primeiro_dia), 0) AS saldo
                FROM meses m
                CROSS JOIN contas_do_usuario c
                LEFT JOIN antes a ON a.conta_id = c.id
                LEFT JOIN no_mes n ON n.conta_id = c.id AND n.primeiro_dia = m.primeiro_dia
            )
            SELECT to_char(m.primeiro_dia, 'YYYY-MM') AS mes,
                   m.dia,
                   CASE WHEN p.primeiro IS NULL OR m.dia < p.primeiro THEN NULL
                        ELSE COALESCE(sum(s.saldo), 0) END AS saldo,
                   CASE WHEN p.primeiro IS NULL OR m.dia < p.primeiro THEN NULL
                        ELSE COALESCE(sum(s.saldo) FILTER (WHERE s.tipo = 'INVESTIMENTO'), 0) END AS investimentos,
                   count(s.id) AS contas,
                   count(s.id) FILTER (WHERE s.tipo = 'INVESTIMENTO') AS contas_de_investimento
            FROM meses m
            CROSS JOIN (SELECT min(x.data_saldo_inicial) AS primeiro FROM contas_do_usuario x) p
            LEFT JOIN saldos s
                   ON s.primeiro_dia = m.primeiro_dia
                  AND s.data_saldo_inicial <= m.dia
                  AND (s.arquivada_no_dia IS NULL OR s.arquivada_no_dia > m.dia)
            GROUP BY m.primeiro_dia, m.dia, p.primeiro
            ORDER BY m.primeiro_dia
            """;

    private final JdbcClient jdbc;

    public ConsultasDoRelatorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** A data do primeiro dado; nula quando o usuário não tem conta nem lançamento. */
    public LocalDate primeiraData(Long usuarioId) {
        return jdbc.sql(PRIMEIRA_DATA).param("usuarioId", usuarioId)
                .query((rs, linha) -> rs.getObject("primeira", LocalDate.class)).list().getFirst();
    }

    /** Os meses de {@code inicio} a {@code fim} que têm receita ou despesa (os outros não vêm). */
    public List<MovimentoDoMes> movimentoPorMes(Long usuarioId, YearMonth inicio, YearMonth fim) {
        return jdbc.sql(MOVIMENTO_POR_MES).param("usuarioId", usuarioId).param("inicio", inicio.atDay(1))
                .param("fim", fim.atEndOfMonth())
                .query((rs, linha) -> new MovimentoDoMes(YearMonth.parse(rs.getString("mes")), dinheiro(rs, "receitas"),
                        dinheiro(rs, "despesas"), rs.getLong("quantidade_de_receitas"), rs.getLong("quantidade_de_despesas"),
                        rs.getLong("despesas_no_cartao")))
                .list();
    }

    /** As despesas de {@code inicio} a {@code fim} por categoria e mês (só as combinações que têm gasto). */
    public List<GastoNoMes> despesasPorCategoria(Long usuarioId, YearMonth inicio, YearMonth fim) {
        return jdbc.sql(DESPESAS_POR_CATEGORIA).param("usuarioId", usuarioId).param("inicio", inicio.atDay(1))
                .param("fim", fim.atEndOfMonth())
                .query((rs, linha) -> new GastoNoMes(rs.getObject("categoria_id", Long.class), YearMonth.parse(rs.getString("mes")),
                        dinheiro(rs, "valor")))
                .list();
    }

    public List<CategoriaDeDespesa> categoriasDeDespesa(Long usuarioId) {
        return jdbc.sql(CATEGORIAS_DE_DESPESA).param("usuarioId", usuarioId)
                .query((rs, linha) -> new CategoriaDeDespesa(rs.getLong("id"), rs.getString("nome"),
                        rs.getObject("categoria_mae_id", Long.class), rs.getBoolean("arquivada")))
                .list();
    }

    /** Um saldo por mês, de {@code inicio} a {@code fim}, em ordem; {@code hoje} limita o dia do mês atual. */
    public List<SaldoNoDia> saldosNoFimDosMeses(Long usuarioId, YearMonth inicio, YearMonth fim, LocalDate hoje) {
        return jdbc.sql(SALDOS_NO_FIM_DOS_MESES).param("usuarioId", usuarioId).param("inicio", inicio.atDay(1))
                .param("fim", fim.atDay(1)).param("hoje", hoje)
                .query((rs, linha) -> new SaldoNoDia(YearMonth.parse(rs.getString("mes")), rs.getObject("dia", LocalDate.class),
                        dinheiro(rs, "saldo"), dinheiro(rs, "investimentos"), rs.getInt("contas"),
                        rs.getInt("contas_de_investimento")))
                .list();
    }

    /** NUMERIC(19,2) ou o 0 do COALESCE, sempre com escala 2 (ADR 0002); nulo continua nulo. */
    private static BigDecimal dinheiro(ResultSet rs, String coluna) throws SQLException {
        BigDecimal valor = rs.getBigDecimal(coluna);
        return valor == null ? null : valor.setScale(2, RoundingMode.UNNECESSARY);
    }
}
