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
     * Direção combinada na Fatia 03a: contas e categorias não dependem de lançamentos; a
     * 03b implementa as interfaces deles (MovimentoDasContas, UsoDaConta, UsoDaCategoria).
     */
    @Test
    void contasECategoriasSoDependemDoQueFoiCombinado() {
        assertThat(dependenciasDiretas("contas")).containsExactlyInAnyOrder("compartilhado");
        assertThat(dependenciasDiretas("categorias")).containsExactlyInAnyOrder("compartilhado", "acesso");
        assertThat(dependenciasDiretas("acesso")).doesNotContain("contas", "categorias");
    }

    private Set<String> dependenciasDiretas(String modulo) {
        return modules.getModuleByName(modulo).orElseThrow()
                .getDirectDependencies(modules)
                .uniqueModules()
                .map(dependencia -> dependencia.getIdentifier().toString())
                .collect(Collectors.toSet());
    }
}
