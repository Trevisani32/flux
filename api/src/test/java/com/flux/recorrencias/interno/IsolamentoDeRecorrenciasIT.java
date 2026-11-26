package com.flux.recorrencias.interno;

import java.util.List;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static org.assertj.core.api.Assertions.assertThat;

class IsolamentoDeRecorrenciasIT extends RecorrenciasBase {

    @Test
    void oUsuarioBNaoLeNemMexeNoQueEDeA() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuarioA = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuarioA);
        UsuarioDeTeste usuarioB = usuariosDeTeste.criar();
        int recorrenciasAntes = contar("recorrencias", usuarioA);
        List<String> tratadasAntes = jdbc.queryForList("select id || '|' || recorrencia_id || '|' || periodo || '|' || situacao "
                + "from ocorrencias_tratadas "
                + "where usuario_id = ? order by id", String.class, usuarioA.id());
        assertNaoEncontrada(api.get(usuarioB, "/api/recorrencias/{id}", exemplo.internet()));
        assertNaoEncontrada(api.put(usuarioB, "/api/recorrencias/{id}", recorrencia("DESPESA", "Internet Fictícia", "119.90", false,
                idDaCategoria(usuarioA, "Contas da casa"), exemplo.bancoHorizonte(), null, "MENSAL", 26, "2026-11-25", null),
                exemplo.internet()));
        assertNaoEncontrada(api.acao(usuarioB, "/api/recorrencias/{id}/encerrar", exemplo.internet()));
        assertNaoEncontrada(api.acao(usuarioB, "/api/recorrencias/{id}/retomar", exemplo.internet()));
        assertNaoEncontrada(api.delete(usuarioB, "/api/recorrencias/{id}", exemplo.internet()));
        assertNaoEncontrada(api.get(usuarioB, "/api/recorrencias/{id}/historico", exemplo.internet()));
        assertNaoEncontrada(api.get(usuarioB, "/api/recorrencias/{id}/ocorrencias/{periodo}/candidatos", exemplo.internet(), "2026-11-01"));
        assertNaoEncontrada(confirmar(usuarioB, exemplo.internet(), "2026-11-01", "2026-11-26", "119.90"));
        assertNaoEncontrada(ligar(usuarioB, exemplo.internet(), "2026-11-01", 1L, null));
        assertNaoEncontrada(pular(usuarioB, exemplo.internet(), "2026-11-01"));
        assertNaoEncontrada(api.acao(usuarioB, "/api/recorrencias/{id}/ocorrencias/{periodo}/voltar-a-prever", exemplo.internet(),
                "2026-11-01"));
        assertNaoEncontrada(api.post(usuarioB, "/api/recorrencias/previa",
                previa("MENSAL", 26, "2026-11-25", null, null, exemplo.internet())));
        assertThat(contar("recorrencias", usuarioA)).isEqualTo(recorrenciasAntes);
        assertThat(jdbc.queryForList("select id || '|' || recorrencia_id || '|' || periodo || '|' || situacao from ocorrencias_tratadas "
                + "where usuario_id = ? order by id", String.class, usuarioA.id())).containsExactlyElementsOf(tratadasAntes);
    }

    @Test
    void idsDeANoCorpoDeBDao422() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuarioA = usuariosDeTeste.criar();
        Exemplo exemplo = montarOExemplo(usuarioA);
        long lancamentoA = api.idCriado(api.post(usuarioA, "/api/lancamentos", lancamento("DESPESA", "1", "2026-11-26", "Lançamento A",
                exemplo.bancoHorizonte(), null)));
        long compraA = novaCompra(usuarioA, exemplo.cartaoLua(), "2026-11-28", "Compra A", "1", 1);
        UsuarioDeTeste usuarioB = usuariosDeTeste.criar();
        long contaB = api.idCriado(api.post(usuarioB, "/api/contas", conta("Conta B", "CORRENTE", "0", "2026-10-01", "null")));
        assertRegra(api.post(usuarioB, "/api/recorrencias", recorrencia("DESPESA", "Conta de A", "1", false, null, exemplo.bancoHorizonte(),
                null, "MENSAL", 26, "2026-11-25", null)), "Conta não encontrada.");
        assertRegra(api.post(usuarioB, "/api/recorrencias", recorrencia("DESPESA", "Cartão de A", "1", false, null, null,
                exemplo.cartaoLua(), "MENSAL", 26, "2026-11-25", null)), "Cartão não encontrado.");
        assertRegra(api.post(usuarioB, "/api/recorrencias", recorrencia("DESPESA", "Categoria de A", "1", false,
                idDaCategoria(usuarioA, "Contas da casa"), contaB, null, "MENSAL", 26, "2026-11-25", null)), "Categoria não encontrada.");
        assertRegra(api.post(usuarioB, "/api/recorrencias/previa", previa("MENSAL", 26, "2026-11-25", null, exemplo.cartaoLua(), null)),
                "Cartão não encontrado.");
        long cartaoB = novoCartao(usuarioB, "Cartão B", 3, 10, contaB);
        long contaBRec = api.idCriado(api.post(usuarioB, "/api/recorrencias", recorrencia("DESPESA", "Conta B rec", "1", false, null,
                contaB, null, "MENSAL", 26, "2026-11-25", null)));
        long cartaoBRec = api.idCriado(api.post(usuarioB, "/api/recorrencias", recorrencia("DESPESA", "Cartão B rec", "1", false, null,
                null, cartaoB, "MENSAL", 28, "2026-11-25", null)));
        assertRegra(ligar(usuarioB, contaBRec, "2026-11-01", lancamentoA, null), "Lançamento não encontrado.");
        assertRegra(ligar(usuarioB, cartaoBRec, "2026-11-01", null, compraA), "Compra não encontrada.");
    }

    @Test
    void listasDeBNaoTemNadaDeA() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuarioA = usuariosDeTeste.criar();
        montarOExemplo(usuarioA);
        UsuarioDeTeste usuarioB = usuariosDeTeste.criar();
        assertThat(json(api.get(usuarioB, "/api/recorrencias")).get("ativas")).isEmpty();
        assertThat(json(api.get(usuarioB, "/api/recorrencias")).get("pausadas")).isEmpty();
        assertThat(json(api.get(usuarioB, "/api/recorrencias")).get("encerradas")).isEmpty();
        assertThat(aConfirmar(usuarioB).get("recorrencias").asLong()).isZero();
        assertThat(lista(usuarioB, "2026-12", "").get("previstos").get("quantidade").asInt()).isZero();
    }

    private static void assertNaoEncontrada(org.springframework.test.web.servlet.assertj.MvcTestResult resposta) {
        assertThat(resposta).hasStatus(404).bodyJson().extractingPath("detail").isEqualTo("Recorrência não encontrada.");
    }

    private static void assertRegra(org.springframework.test.web.servlet.assertj.MvcTestResult resposta, String esperada) {
        assertThat(resposta).hasStatus(422).bodyJson().extractingPath("detail").isEqualTo(esperada);
    }
}
