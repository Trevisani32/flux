package com.flux.recorrencias.interno;

import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static com.flux.suporte.ApiDeTeste.conta;
import static com.flux.suporte.ApiDeTeste.lancamento;
import static org.assertj.core.api.Assertions.assertThat;

class ExclusaoComRecorrenciasIT extends RecorrenciasBase {

    @Test
    void excluirAContaOuOCartaoLevaAsRecorrencias() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long pagamento = api.idCriado(api.post(usuario, "/api/contas", conta("Pagamento", "CORRENTE", "0", "2026-10-01", "null")));
        long conta = api.idCriado(api.post(usuario, "/api/contas", conta("Conta do clube", "CORRENTE", "0", "2026-10-01", "null")));
        long cartao = novoCartao(usuario, "Cartão extra", 3, 10, pagamento);
        long categoria = idDaCategoria(usuario, "Lazer");
        long daConta = api.idCriado(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Clube", "10", false, categoria, conta,
                null, "MENSAL", 26, "2026-11-25", null)));
        long doCartao = api.idCriado(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Extra", "10", false, categoria, null,
                cartao, "MENSAL", 26, "2026-11-25", null)));
        assertThat(pular(usuario, daConta, "2026-11-01")).hasStatus(200);
        assertThat(contar("recorrencias", usuario)).isEqualTo(2);
        assertThat(contar("ocorrencias_tratadas", usuario)).isEqualTo(1);
        assertThat(api.delete(usuario, "/api/cartoes/{id}", cartao)).hasStatus(204);
        assertThat(api.delete(usuario, "/api/contas/{id}", conta)).hasStatus(204);
        assertThat(contar("recorrencias", usuario)).isZero();
        assertThat(contar("ocorrencias_tratadas", usuario)).isZero();
        assertThat(api.get(usuario, "/api/recorrencias/{id}", daConta)).hasStatus(404);
        assertThat(api.get(usuario, "/api/recorrencias/{id}", doCartao)).hasStatus(404);
    }

    @Test
    void aCategoriaComRecorrenciaSoSeArquiva() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        long conta = api.idCriado(api.post(usuario, "/api/contas", conta("Banco Horizonte", "CORRENTE", "0", "2026-10-01", "null")));
        long categoria = idDaCategoria(usuario, "Contas da casa");
        api.idCriado(api.post(usuario, "/api/lancamentos", lancamento("DESPESA", "50.00", "2026-11-10", "Despesa Fictícia",
                conta, categoria)));
        long internet = api.idCriado(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Internet Fictícia", "50", false,
                categoria, conta, null, "MENSAL", 26, "2026-11-25", null)));
        long luz = api.idCriado(api.post(usuario, "/api/recorrencias", recorrencia("DESPESA", "Conta de luz Fictícia", "50", false,
                categoria, conta, null, "MENSAL", 30, "2026-11-25", null)));
        var excluir = api.delete(usuario, "/api/categorias/{id}", categoria);
        assertThat(excluir).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("Esta categoria não pode ser excluída porque tem 1 lançamento e 2 recorrências. "
                        + "Arquive a categoria para tirá-la dos seletores.");
        assertThat(json(excluir).get("uso").asString()).isEqualTo("1 lançamento e 2 recorrências");
        assertThat(api.acao(usuario, "/api/categorias/{id}/arquivar", categoria)).hasStatus(200);
        for (long pausada : new long[] {internet, luz}) {
            assertThat(json(api.get(usuario, "/api/recorrencias/{id}", pausada)).get("situacao").asString()).isEqualTo("PAUSADA");
            assertThat(json(api.get(usuario, "/api/recorrencias/{id}", pausada)).get("motivoDaPausa").asString())
                    .isEqualTo("a categoria Contas da casa está arquivada.");
        }
        assertThat(confirmar(usuario, internet, "2026-11-01", "2026-11-26", "50.00")).hasStatus(422).bodyJson().extractingPath("detail")
                .isEqualTo("A recorrência está pausada: a categoria Contas da casa está arquivada.");
    }

    @Test
    void excluirOUsuarioApagaTudo() {
        fixarHojeEm20261125();
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        montarOExemplo(usuario);
        assertThat(mvc.delete().uri("/api/usuarios/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario.tokenAcesso())
                .contentType(MediaType.APPLICATION_JSON).content("{\"senhaAtual\":\"" + usuario.senha() + "\"}")).hasStatus(204);
        assertThat(contar("recorrencias", usuario)).isZero();
        assertThat(contar("ocorrencias_tratadas", usuario)).isZero();
    }
}
