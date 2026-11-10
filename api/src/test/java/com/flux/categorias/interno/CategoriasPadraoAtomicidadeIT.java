package com.flux.categorias.interno;

import java.util.UUID;

import com.flux.acesso.UsuarioCriado;
import com.flux.acesso.interno.UsuarioService;
import com.flux.suporte.InfraestruturaDeTeste;
import com.flux.suporte.IntegracaoBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Categorias padrão e usuário são gravados juntos ou nada é gravado. Um segundo ouvinte,
 * só deste teste, falha para e-mails "falha-…" (esta classe sobe um contexto próprio).
 */
@Import({InfraestruturaDeTeste.class, CategoriasPadraoAtomicidadeIT.OuvinteQueFalha.class})
class CategoriasPadraoAtomicidadeIT extends IntegracaoBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UsuarioService usuarioService;

    @Test
    void falhaDepoisDasCategoriasDesfazOUsuarioCriadoPeloService() {
        String email = "falha-" + UUID.randomUUID() + "@teste.local";
        int categoriasAntes = totalDeCategorias();

        Falhador.categoriasNaHoraDaFalha = -1;
        assertThatThrownBy(() -> usuarioService.criar("Falha", email, "senha-de-teste-123"))
                .isInstanceOf(IllegalStateException.class).hasMessage("falha simulada");

        assertThat(Falhador.categoriasNaHoraDaFalha).isEqualTo(24);
        assertThat(jdbc.queryForObject("select count(*) from usuarios where email = ?", Integer.class, email)).isZero();
        assertThat(totalDeCategorias()).isEqualTo(categoriasAntes);
    }

    @Test
    void falhaDepoisDasCategoriasDesfazOCadastroPublicoSemMandarEmail() {
        String email = "falha-" + UUID.randomUUID() + "@teste.local";
        int categoriasAntes = totalDeCategorias();
        Falhador.categoriasNaHoraDaFalha = -1;

        assertThat(mvc.post().uri("/api/auth/cadastrar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"Ana\",\"email\":\"" + email + "\",\"senha\":\"senha-de-teste-123\",\"aceiteDosTermos\":true}"))
                .hasStatus(500);

        assertThat(Falhador.categoriasNaHoraDaFalha).isEqualTo(24);
        assertThat(jdbc.queryForObject("select count(*) from usuarios where email = ?", Integer.class, email)).isZero();
        assertThat(totalDeCategorias()).isEqualTo(categoriasAntes);
        caixaDeEntrada.aguardarEnvios();
        assertThat(caixaDeEntrada.mensagensPara(email, "Confirme seu e-mail no Flux")).isEmpty();
    }

    private int totalDeCategorias() {
        return jdbc.queryForObject("select count(*) from categorias", Integer.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OuvinteQueFalha {

        @Bean
        Falhador falhador(JdbcTemplate jdbc) {
            return new Falhador(jdbc);
        }
    }

    static class Falhador {

        private final JdbcTemplate jdbc;

        Falhador(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        /** Quantas categorias o usuário tinha quando este ouvinte falhou (prova que as padrão vieram antes). */
        static volatile int categoriasNaHoraDaFalha = -1;

        /** Roda na mesma transação, depois do CategoriasPadrao: enxerga o usuário e as categorias recém-inseridos. */
        @EventListener
        @Order(Ordered.LOWEST_PRECEDENCE)
        public void aoCriarUsuario(UsuarioCriado evento) {
            String email = jdbc.queryForObject("select email from usuarios where id = ?", String.class, evento.usuarioId());
            if (email.startsWith("falha-")) {
                categoriasNaHoraDaFalha = jdbc.queryForObject("select count(*) from categorias where usuario_id = ?",
                        Integer.class, evento.usuarioId());
                throw new IllegalStateException("falha simulada");
            }
        }
    }
}
