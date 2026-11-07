package com.flux.acesso.interno;

import java.time.Duration;
import java.util.UUID;

import com.flux.suporte.IntegracaoBase;
import com.flux.suporte.UsuariosDeTeste.UsuarioDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteDaTrocaDeEmailIT extends IntegracaoBase {

    @Autowired private TokenAcessoService tokenAcessoService;

    @Test
    void limitaTresTrocasPorContaAntesDaSenhaEVoltaDepoisDeUmaHora() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            assertThat(trocar(usuario, emailUnico(), usuario.senha())).hasStatus(202);
        }
        String barrado = emailUnico();
        MvcTestResult bloqueado = trocar(usuario, barrado, usuario.senha());
        assertThat(bloqueado).hasStatus(429).containsHeader("Retry-After")
                .bodyJson().extractingPath("title").isEqualTo("Muitas tentativas");
        assertThat(Integer.parseInt(bloqueado.getResponse().getHeader("Retry-After"))).isBetween(1, 3600);
        assertThat(bloqueado).bodyJson().extractingPath("detail").isEqualTo("Muitas tentativas. Tente de novo em alguns minutos.");
        assertThat(caixaDeEntrada.mensagensPara(barrado)).isEmpty();
        assertThat(trocar(usuario, emailUnico(), "errada")).hasStatus(429);

        relogioDeTeste.avancar(Duration.ofHours(1));
        String tokenAcessoAtual = tokenAcessoService.emitir(usuario.id()).valor();
        assertThat(trocar(tokenAcessoAtual, emailUnico(), usuario.senha())).hasStatus(202);
    }

    @Test
    void senhaErradaEEmailIgualNaoGastamLimiteDaTroca() {
        UsuarioDeTeste usuario = usuariosDeTeste.criar();
        assertThat(trocar(usuario, emailUnico(), "errada")).hasStatus(422);
        assertThat(trocar(usuario, emailUnico(), "errada")).hasStatus(422);
        assertThat(trocar(usuario, usuario.email(), usuario.senha())).hasStatus(422);
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            assertThat(trocar(usuario, emailUnico(), usuario.senha())).hasStatus(202);
        }
        assertThat(trocar(usuario, emailUnico(), usuario.senha())).hasStatus(429);
    }

    @Test
    void limitaTresPedidosParaOMesmoEnderecoSemGastarContaBarrada() {
        String destino = emailUnico();
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            UsuarioDeTeste usuario = usuariosDeTeste.criar();
            assertThat(trocar(usuario, destino, usuario.senha())).hasStatus(202);
        }
        UsuarioDeTeste quarto = usuariosDeTeste.criar();
        assertThat(trocar(quarto, destino, quarto.senha())).hasStatus(429);
        assertThat(caixaDeEntrada.mensagensPara(destino, "Confirme seu novo e-mail no Flux")).hasSize(3);
        for (int tentativa = 0; tentativa < 3; tentativa++) {
            assertThat(trocar(quarto, emailUnico(), quarto.senha())).hasStatus(202);
        }
    }

    private MvcTestResult trocar(UsuarioDeTeste usuario, String email, String senha) {
        return trocar(usuario.tokenAcesso(), email, senha);
    }

    private MvcTestResult trocar(String tokenAcesso, String email, String senha) {
        return mvc.post().uri("/api/usuarios/me/email").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAcesso)
                .contentType(MediaType.APPLICATION_JSON).content("{\"novoEmail\":\"" + email + "\",\"senhaAtual\":\"" + senha + "\"}").exchange();
    }

    private static String emailUnico() { return "novo-" + UUID.randomUUID() + "@teste.local"; }
}
