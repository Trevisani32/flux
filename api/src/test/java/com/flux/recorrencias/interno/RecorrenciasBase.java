package com.flux.recorrencias.interno;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.flux.acesso.interno.TokenAcessoService;
import com.flux.suporte.CartoesBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;

import static com.flux.suporte.ApiDeTeste.conta;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class RecorrenciasBase extends CartoesBase {

    @Autowired
    private TokenAcessoService tokensDeAcesso;

    protected LocalDate fixarHojeEm20261125() {
        return fixarHojeEm(LocalDate.of(2026, 11, 25), 12, 0);
    }

    protected LocalDate fixarHojeEm(LocalDate data, int hora, int minuto) {
        Instant instante = data.atTime(hora, minuto).atZone(ZoneId.of("America/Sao_Paulo")).toInstant();
        relogioDeTeste.avancar(Duration.between(relogioDeTeste.instant(), instante));
        return LocalDate.now(relogioDeTeste);
    }

    protected UsuarioDeTeste renovarToken(UsuarioDeTeste usuario) {
        return new UsuarioDeTeste(usuario.id(), usuario.email(), usuario.senha(), tokensDeAcesso.emitir(usuario.id()).valor());
    }

    protected String recorrencia(String tipo, String descricao, String valor, boolean valorVaria, Long categoriaId, Long contaId,
                                 Long cartaoId, String frequencia, Integer diaDoMes, String comecaEm, String terminaEm) {
        return "{\"tipo\":\"" + tipo + "\",\"descricao\":\"" + descricao + "\",\"valor\":" + valor
                + ",\"valorVaria\":" + valorVaria + ",\"categoriaId\":" + categoriaId + ",\"contaId\":" + contaId
                + ",\"cartaoId\":" + cartaoId + ",\"frequencia\":\"" + frequencia + "\",\"diaDoMes\":"
                + (diaDoMes == null ? "null" : diaDoMes) + ",\"comecaEm\":\"" + comecaEm + "\",\"terminaEm\":"
                + (terminaEm == null ? "null" : "\"" + terminaEm + "\"") + "}";
    }

    protected JsonNode json(MvcTestResult resposta) {
        return api.json(resposta);
    }

    protected JsonNode aConfirmar(UsuarioDeTeste usuario) {
        return json(api.get(usuario, "/api/recorrencias/a-confirmar"));
    }

    protected JsonNode lista(UsuarioDeTeste usuario, String mes, String parametros) {
        return json(api.get(usuario, "/api/lancamentos?mes=" + mes + parametros));
    }

    protected MvcTestResult confirmar(UsuarioDeTeste usuario, long recorrenciaId, String periodo, String data, String valor) {
        return confirmar(usuario, recorrenciaId, periodo, data, valor, null);
    }

    protected MvcTestResult confirmar(UsuarioDeTeste usuario, long recorrenciaId, String periodo, String data, String valor,
                                      String observacao) {
        return api.post(usuario, "/api/recorrencias/{id}/ocorrencias/{periodo}/confirmar", confirmarCorpo(data, valor, observacao),
                recorrenciaId, periodo);
    }

    protected String confirmarCorpo(String data, String valor, String observacao) {
        return "{\"data\":" + textoOuNulo(data) + ",\"valor\":" + valor + ",\"observacao\":" + textoOuNulo(observacao) + "}";
    }

    protected MvcTestResult pular(UsuarioDeTeste usuario, long recorrenciaId, String periodo) {
        return api.acao(usuario, "/api/recorrencias/{id}/ocorrencias/{periodo}/pular", recorrenciaId, periodo);
    }

    protected MvcTestResult ligar(UsuarioDeTeste usuario, long recorrenciaId, String periodo, Long lancamentoId, Long compraId) {
        return api.post(usuario, "/api/recorrencias/{id}/ocorrencias/{periodo}/ligar", "{\"lancamentoId\":" + lancamentoId
                + ",\"compraId\":" + compraId + "}", recorrenciaId, periodo);
    }

    protected String previa(String frequencia, Integer diaDoMes, String comecaEm, String terminaEm, Long cartaoId,
                            Long recorrenciaId) {
        return "{\"frequencia\":" + textoOuNulo(frequencia) + ",\"diaDoMes\":" + (diaDoMes == null ? "null" : diaDoMes)
                + ",\"comecaEm\":" + textoOuNulo(comecaEm) + ",\"terminaEm\":" + textoOuNulo(terminaEm) + ",\"cartaoId\":"
                + (cartaoId == null ? "null" : cartaoId) + ",\"recorrenciaId\":" + (recorrenciaId == null ? "null" : recorrenciaId)
                + "}";
    }

    protected JsonNode historico(UsuarioDeTeste usuario, long recorrenciaId) {
        return json(api.get(usuario, "/api/recorrencias/{id}/historico", recorrenciaId));
    }

    protected JsonNode candidatos(UsuarioDeTeste usuario, long recorrenciaId, String periodo) {
        return json(api.get(usuario, "/api/recorrencias/{id}/ocorrencias/{periodo}/candidatos", recorrenciaId, periodo));
    }

    protected long importado(UsuarioDeTeste usuario, long contaId, String data, String valor, String descricao) {
        long importacao = jdbc.queryForObject("insert into importacoes (usuario_id, conta_id, formato, nome_arquivo, hash_arquivo, "
                + "data_inicio, data_fim, lidas, importadas, duplicadas, anteriores, fora, desmarcadas) values (?, ?, 'OFX', 'rec.ofx', "
                + "md5(random()::text) || md5(random()::text), ?::date, ?::date, 1, 1, 0, 0, 0, 0) returning id", Long.class,
                usuario.id(), contaId, data, data);
        return jdbc.queryForObject("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem, "
                + "importacao_id, chave_importacao, descricao_banco) values (?, ?, ?::date, ?, lower(?), ?::numeric, 'DESPESA', "
                + "'IMPORTADO', ?, 'REC:' || md5(random()::text), ?) returning id", Long.class, usuario.id(), contaId, data, descricao,
                descricao, valor, importacao, descricao);
    }

    protected Exemplo montarOExemplo(UsuarioDeTeste usuario) {
        long bancoHorizonte = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "1240.35",
                "2026-10-01", "null")));
        long bancoAurora = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Aurora", "CORRENTE", "0", "2026-10-01", "null")));
        long bancoAntigo = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Antigo", "CORRENTE", "0", "2026-10-01", "null")));
        long cartaoHorizonte = api.idCriado(api.post(usuario, "/api/cartoes", cartao("Cartão Horizonte", "5000", 3, 10,
                bancoHorizonte, "\"OURO\"")));
        long cartaoLua = api.idCriado(api.post(usuario, "/api/cartoes", cartao("Cartão Lua", "5000", 25, 5, bancoHorizonte,
                "\"ROXO\"")));
        long contasDaCasa = idDaCategoria(usuario, "Contas da casa");
        long moradia = idDaCategoria(usuario, "Moradia");
        long saude = idDaCategoria(usuario, "Saúde");
        long assinaturas = idDaCategoria(usuario, "Assinaturas");
        long aluguel = idDaCategoria(usuario, "Aluguel");
        long condominio = idDaCategoria(usuario, "Condomínio");
        long salario = idDaCategoria(usuario, "Salário");
        long educacao = idDaCategoria(usuario, "Educação");
        long transporte = idDaCategoria(usuario, "Transporte");
        long lazer = idDaCategoria(usuario, "Lazer");
        long internet = criar(usuario, "DESPESA", "Internet Fictícia", "119.90", false, contasDaCasa, bancoHorizonte, null,
                "MENSAL", 26, "2026-11-25", null);
        long diarista = criar(usuario, "DESPESA", "Diarista Fictícia", "180.00", false, moradia, bancoHorizonte, null,
                "SEMANAL", null, "2026-11-27", null);
        long academia = criar(usuario, "DESPESA", "Academia Fictícia", "99.90", false, saude, bancoHorizonte, null,
                "MENSAL", 28, "2026-10-01", null);
        long netflix = criar(usuario, "DESPESA", "Netflix Fictícia", "55.90", false, assinaturas, null, cartaoLua,
                "MENSAL", 28, "2026-11-25", null);
        long luz = criar(usuario, "DESPESA", "Conta de luz Fictícia", "189.90", true, contasDaCasa, bancoHorizonte, null,
                "MENSAL", 30, "2026-11-25", null);
        long aluguelRec = criar(usuario, "DESPESA", "Aluguel Fictício", "1850.00", false, aluguel, bancoHorizonte, null,
                "MENSAL", 1, "2026-11-25", null);
        long condominioRec = criar(usuario, "DESPESA", "Condomínio Fictício", "420.00", false, condominio, bancoHorizonte,
                null, "MENSAL", 1, "2026-11-25", null);
        long spotify = criar(usuario, "DESPESA", "Spotify Fictício", "21.90", false, assinaturas, null, cartaoHorizonte,
                "MENSAL", 2, "2026-11-25", null);
        long salarioRec = criar(usuario, "RECEITA", "Salário Fictício", "8200.00", false, salario, bancoHorizonte, null,
                "MENSAL", 5, "2026-11-25", null);
        long plano = criar(usuario, "DESPESA", "Plano de saúde Fictício", "640.00", false, saude, bancoAurora, null,
                "MENSAL", 10, "2026-11-25", null);
        long curso = criar(usuario, "DESPESA", "Curso de inglês Fictício", "350.00", false, educacao, bancoHorizonte, null,
                "MENSAL", 15, "2026-11-15", "2027-06-15");
        long agua = criar(usuario, "DESPESA", "Conta de água Fictícia", "96.40", true, contasDaCasa, bancoHorizonte, null,
                "MENSAL", 20, "2026-11-01", null);
        long ipva = criar(usuario, "DESPESA", "IPVA Fictício", "1240.00", false, transporte, bancoHorizonte, null,
                "ANUAL", null, "2026-01-15", null);
        long clube = criar(usuario, "DESPESA", "Mensalidade do clube Fictícia", "150.00", false, lazer, bancoAntigo, null,
                "MENSAL", 10, "2026-11-25", null);
        long streaming = criar(usuario, "DESPESA", "Streaming antigo Fictício", "29.90", false, assinaturas, null, cartaoLua,
                "MENSAL", 31, "2026-03-01", "2026-08-31");
        jdbc.update("update recorrencias set ocorrencias_desde = '2026-10-01', criada_em = '2026-10-01T15:00:00Z' where id = ?", academia);
        jdbc.update("update recorrencias set ocorrencias_desde = '2026-11-01' where id in (?, ?)", curso, agua);
        assertThat(pular(usuario, academia, "2026-10-01")).hasStatus(200);
        assertThat(confirmar(usuario, curso, "2026-11-01", "2026-11-15", "350.00")).hasStatus(200);
        assertThat(api.acao(usuario, "/api/contas/{id}/arquivar", bancoAntigo)).hasStatus(200);
        return new Exemplo(bancoHorizonte, bancoAurora, bancoAntigo, cartaoHorizonte, cartaoLua, internet, diarista, academia,
                netflix, luz, aluguelRec, condominioRec, spotify, salarioRec, plano, curso, agua, ipva, clube, streaming);
    }

    private long criar(UsuarioDeTeste usuario, String tipo, String descricao, String valor, boolean varia, long categoria, Long conta,
                       Long cartao, String frequencia, Integer dia, String inicio, String fim) {
        return api.idCriado(api.post(usuario, "/api/recorrencias", recorrencia(tipo, descricao, valor, varia, categoria, conta,
                cartao, frequencia, dia, inicio, fim)));
    }

    private static String textoOuNulo(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }

    protected record Exemplo(long bancoHorizonte, long bancoAurora, long bancoAntigo, long cartaoHorizonte, long cartaoLua,
                             long internet, long diarista, long academia, long netflix, long luz, long aluguel, long condominio,
                             long spotify, long salario, long plano, long curso, long agua, long ipva, long clube, long streaming) {
    }
}
