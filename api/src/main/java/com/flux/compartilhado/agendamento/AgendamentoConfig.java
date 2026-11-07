package com.flux.compartilhado.agendamento;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga os @Scheduled. Os testes desligam (app.agendamentos.habilitados=false) e chamam os jobs direto. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnBooleanProperty(name = "app.agendamentos.habilitados", matchIfMissing = true)
public class AgendamentoConfig {
}
