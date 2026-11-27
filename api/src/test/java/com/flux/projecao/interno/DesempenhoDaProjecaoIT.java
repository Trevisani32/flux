package com.flux.projecao.interno;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.flux.suporte.ApiDeTeste;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static org.assertj.core.api.Assertions.assertThat;

class DesempenhoDaProjecaoIT extends ProjecaoBase {

    private static final List<String> URIS = List.of("/api/projecao", "/api/projecao?ate=2027-11-25", "/api/projecao/aviso",
            "/api/recorrencias/a-confirmar");

    @Autowired
    private EntityManagerFactory fabrica;

    @Test
    void oNumeroDeConsultasEFixoEAProjecaoCabe() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        ExemploDaProjecao exemplo = montarAProjecao(usuario);
        Statistics estatisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        estatisticas.setStatisticsEnabled(true);
        try {
            assertThat(api.get(usuario, "/api/projecao")).hasStatus(200);
            assertThat(contar(estatisticas, usuario)).containsExactly(14L, 14L, 14L, 7L);
            segundaCarga(usuario, exemplo);
            assertThat(contar(estatisticas, usuario)).containsExactly(14L, 14L, 14L, 7L);
            estatisticas.clear();
            long inicio = System.nanoTime();
            var resposta = api.get(usuario, "/api/projecao?ate=2027-11-25");
            long milissegundos = (System.nanoTime() - inicio) / 1_000_000;
            assertThat(resposta).hasStatus(200);
            String corpo = ApiDeTeste.texto(resposta);
            var json = json(resposta);
            assertThat(json.get("escolhidas").size()).isEqualTo(15);
            assertThat(json.get("dias").size()).isEqualTo(364);
            assertThat(corpo.getBytes(StandardCharsets.UTF_8).length).isLessThan(4_000_000);
            assertThat(milissegundos).isLessThan(3_000);
            assertThat(json.get("dias").valueStream().flatMap(dia -> dia.get("itens").valueStream())
                    .allMatch(item -> item.get("ocorrencia").isNull())).isTrue();
            assertThat(json.get("pendencias").valueStream().filter(item -> "PREVISTO".equals(item.get("tipo").asString()))
                    .allMatch(item -> !item.get("ocorrencia").isNull())).isTrue();
        } finally {
            estatisticas.setStatisticsEnabled(false);
        }
    }

    private List<Long> contar(Statistics estatisticas, UsuarioDeTeste usuario) {
        return URIS.stream().map(uri -> {
            estatisticas.clear();
            assertThat(api.get(usuario, uri)).hasStatus(200);
            return estatisticas.getPrepareStatementCount();
        }).toList();
    }

    private void segundaCarga(UsuarioDeTeste usuario, ExemploDaProjecao exemplo) {
        assertThat(jdbc.update("insert into recorrencias (usuario_id, descricao, valor, tipo, conta_id, frequencia, comeca_em, "
                + "ocorrencias_desde) select ?, 'Semanal ' || g, 10.00 + g, 'DESPESA', ?, 'SEMANAL', date '2026-11-23' + (g % 7), "
                + "date '2026-11-25' from generate_series(1, 185) g", usuario.id(), exemplo.exemplo().bancoHorizonte())).isEqualTo(185);
        assertThat(jdbc.update("insert into lancamentos (usuario_id, conta_id, data, descricao, descricao_chave, valor, tipo, origem) "
                + "select ?, ?, date '2026-11-24', 'Semanal ' || g, 'semanal ' || g, -(10.00 + g), 'DESPESA', 'MANUAL' "
                + "from generate_series(1, 185) g", usuario.id(), exemplo.exemplo().bancoHorizonte())).isEqualTo(185);
        assertThat(jdbc.update("insert into ocorrencias_tratadas (usuario_id, recorrencia_id, periodo, data_prevista, situacao, "
                + "lancamento_id) select r.usuario_id, r.id, date '2026-11-23', r.comeca_em, 'EFETIVADA', l.id from recorrencias r "
                + "join lancamentos l on l.usuario_id = r.usuario_id and l.descricao = r.descricao where r.usuario_id = ? "
                + "and r.descricao like 'Semanal %'", usuario.id())).isEqualTo(185);
        long primeira = 0;
        for (int indice = 1; indice <= 10; indice++) {
            long conta = api.idCriado(api.post(usuario, "/api/contas", conta("Conta extra " + indice, "CORRENTE", "1000.00",
                    "2026-10-01", "null")));
            if (indice <= 3) {
                long cartao = novoCartao(usuario, "Cartão extra " + indice, 25, 5, conta);
                novaCompra(usuario, cartao, "2026-11-20", "Compra extra aberta " + indice, "100.00", 1);
                novaCompra(usuario, cartao, "2026-10-20", "Compra extra vencida " + indice, "50.00", 1);
            }
            if (indice == 5) {
                primeira = conta;
            }
        }
        assertThat(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "20.00", "2026-12-03", "Futuro extra", primeira,
                null))).hasStatus(201);
    }
}
