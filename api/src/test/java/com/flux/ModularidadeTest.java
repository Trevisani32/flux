package com.flux;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;

class ModularidadeTest {

    private final ApplicationModules modules = ApplicationModules.of(FluxApplication.class);

    @Test
    void verificaModulos() {
        modules.verify();
        new Documenter(modules).writeDocumentation();
    }

    /**
     * Direção combinada no ADR 0012 e no ADR 0014: lançamentos depende de contas e categorias e
     * implementa as interfaces deles (MovimentoDasContas, UsoDaConta, LimpezaDaConta,
     * UsoDaCategoria); a importação depende de lançamentos e de contas; ninguém depende da
     * importação, e contas e categorias não dependem de lançamentos.
     */
    @Test
    void dependenciasSeguemADirecaoCombinada() {
        assertThat(dependenciasDiretas("contas")).containsExactlyInAnyOrder("compartilhado");
        assertThat(dependenciasDiretas("categorias")).containsExactlyInAnyOrder("compartilhado", "acesso");
        assertThat(dependenciasDiretas("lancamentos")).containsExactlyInAnyOrder("compartilhado", "contas", "categorias");
        assertThat(dependenciasDiretas("importacao")).containsExactlyInAnyOrder("compartilhado", "contas", "lancamentos");
        assertThat(dependenciasDiretas("acesso")).doesNotContain("contas", "categorias", "lancamentos", "importacao");
    }

    private Set<String> dependenciasDiretas(String modulo) {
        return modules.getModuleByName(modulo).orElseThrow()
                .getDirectDependencies(modules)
                .uniqueModules()
                .map(dependencia -> dependencia.getIdentifier().toString())
                .collect(Collectors.toSet());
    }
}
