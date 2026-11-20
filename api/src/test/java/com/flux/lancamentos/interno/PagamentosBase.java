package com.flux.lancamentos.interno;

import java.time.LocalDate;
import java.time.YearMonth;

import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Apoio das integrações do pagamento da fatura (Fatia 05b). As datas saem do relógio dos testes: o cartão "fecha 5,
 * vence 15" tem a fatura de cada mês fechando no dia 5 e vencendo no dia 15 do mesmo mês, e a compra do dia 1 de um mês
 * cai na fatura daquele mês. A de dois meses atrás está sempre fechada, em qualquer dia de hoje.
 */
abstract class PagamentosBase extends CartoesBase {

    protected LocalDate hoje() {
        return LocalDate.now(relogioDeTeste);
    }

    /** O mês da fatura {@code meses} meses antes do mês de hoje ("2026-09"). */
    protected String mesAtras(int meses) {
        return YearMonth.from(hoje()).minusMonths(meses).toString();
    }

    /** O dia 1 do mês {@code meses} meses antes do mês de hoje: a compra dele cai na fatura desse mês. */
    protected String diaUmAtras(int meses) {
        return YearMonth.from(hoje()).minusMonths(meses).atDay(1).toString();
    }

    /** O cartão "fecha 5, vence 15", com limite de R$ 1.000,00. */
    protected long cartaoQueFecha5(UsuarioDeTeste usuario, String nome, long contaId) {
        return api.idCriado(api.post(usuario, "/api/cartoes", cartao(nome, "1000", 5, 15, contaId, "null")));
    }

    /** Corpo de POST /api/cartoes/{id}/faturas/{mes}/pagamentos e PUT /api/pagamentos/{id}, sem observação. */
    protected static String pagamento(long contaId, String data, String valor) {
        return "{\"contaId\":" + contaId + ",\"data\":\"" + data + "\",\"valor\":" + valor + ",\"observacao\":null}";
    }

    protected MvcTestResult pagar(UsuarioDeTeste usuario, long cartaoId, String mes, long contaId, String data, String valor) {
        return api.post(usuario, "/api/cartoes/{id}/faturas/{mes}/pagamentos", pagamento(contaId, data, valor), cartaoId, mes);
    }

    /** Paga hoje e devolve o id do pagamento. */
    protected long pagarHoje(UsuarioDeTeste usuario, long cartaoId, String mes, long contaId, String valor) {
        return api.idCriado(pagar(usuario, cartaoId, mes, contaId, hoje().toString(), valor));
    }

    protected MvcTestResult fatura(UsuarioDeTeste usuario, long cartaoId, String mes) {
        return api.get(usuario, "/api/cartoes/{id}/faturas/{mes}", cartaoId, mes);
    }

    /**
     * Despesa importada sem categoria, com a descrição do extrato igual à descrição, numa importação só dela (por SQL, como
     * na conversão em transferência). {@code valor} com sinal. Devolve o id do lançamento.
     */
    protected long importado(UsuarioDeTeste usuario, long contaId, String data, String valor, String descricao) {
        Long importacao = jdbc.queryForObject("insert into importacoes (usuario_id, conta_id, formato, nome_arquivo, hash_arquivo, "
                + "data_inicio, data_fim, lidas, importadas, duplicadas, anteriores, fora, desmarcadas) values (?, ?, 'OFX', 'teste.ofx', "
                + "md5(random()::text) || md5(random()::text), ?::date, ?::date, 1, 1, 0, 0, 0, 0) returning id", Long.class,
                usuario.id(), contaId, data, data);
        return jdbc.queryForObject("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem, "
                + "importacao_id, chave_importacao, descricao_banco) values (?, ?, ?::date, ?, lower(?), ?::numeric, "
                + "case when ?::numeric > 0 then 'RECEITA' else 'DESPESA' end, 'IMPORTADO', ?, 'ID:' || ? || ':' || md5(random()::text), ?) "
                + "returning id", Long.class, usuario.id(), contaId, data, descricao, descricao, valor, valor, importacao, contaId, descricao);
    }

    /** "tipo valor data descrição categoria fatura-paga" de um lançamento. */
    protected String lancamentoNoBanco(long id) {
        return jdbc.queryForObject("select l.tipo || ' ' || l.valor || ' ' || l.data || ' ' || l.descricao || ' ' "
                + "|| coalesce(l.categoria_id::text, 'sem') || ' ' || coalesce(to_char(f.mes_referencia, 'YYYY-MM'), 'solto') "
                + "from lancamentos l left join faturas f on f.id = l.fatura_paga_id where l.id = ?", String.class, id);
    }
}
