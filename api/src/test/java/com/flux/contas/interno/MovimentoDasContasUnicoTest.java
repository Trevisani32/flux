package com.flux.contas.interno;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import com.flux.contas.MovimentoDasContas;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Duas implementações de MovimentoDasContas impedem a subida; zero ou uma, não. */
class MovimentoDasContasUnicoTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(ContaRepository.class, () -> mock(ContaRepository.class))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(ContaService.class);

    @Test
    void duasImplementacoesImpedemASubida() {
        contexto.withBean("movimentoDosLancamentos", MovimentoDasContas.class, MovimentoDasContasUnicoTest::falso)
                .withBean("movimentoRepetido", MovimentoDasContas.class, MovimentoDasContasUnicoTest::falso)
                .run(subida -> assertThat(subida).hasFailed().getFailure()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("Só pode existir uma implementação de MovimentoDasContas; há 2."));
    }

    @Test
    void zeroOuUmaImplementacaoSobe() {
        contexto.run(subida -> assertThat(subida).hasNotFailed().hasSingleBean(ContaService.class));
        contexto.withBean("movimentoDosLancamentos", MovimentoDasContas.class, MovimentoDasContasUnicoTest::falso)
                .run(subida -> assertThat(subida).hasNotFailed().hasSingleBean(ContaService.class));
    }

    private static MovimentoDasContas falso() {
        return new MovimentoDasContas() {
            @Override
            public Map<Long, BigDecimal> somaPorContaAte(Long usuarioId, LocalDate data) {
                return Map.of();
            }

            @Override
            public Optional<LocalDate> dataDoPrimeiroLancamento(Long usuarioId, Long contaId) {
                return Optional.empty();
            }
        };
    }
}
