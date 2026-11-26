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
     * Direção combinada no ADR 0012, no ADR 0014, no ADR 0015 e no ADR 0017: lançamentos depende de contas,
     * categorias e cartões e implementa as interfaces deles (MovimentoDasContas, UsoDaConta, LimpezaDaConta,
     * UsoDaCategoria, MovimentoDosCartoes, UsoDoCartao, TrocaDeDias); cartões depende só de contas e implementa
     * UsoDaConta (a conta de pagamento, Fatia 05a); as regras (categorizacao) dependem de contas, categorias e lançamentos e
     * implementam UsoDaCategoria e LimpezaDaConta; a importação depende de lançamentos, de contas, de cartões e
     * das regras; ninguém depende da importação; contas, categorias e lançamentos não dependem das
     * regras, e contas e categorias não dependem dos cartões. Relatórios depende só de compartilhado, e ninguém depende de
     * relatórios (ADR 0020). Exportação depende só de compartilhado e de acesso (a conferência da senha), e ninguém depende
     * dela (ADR 0021). Recorrências depende de contas, cartões, categorias e lançamentos, implementa PrevistosNaLista e
     * UsoDaCategoria, e ninguém depende dela (ADR 0022).
     */
    @Test
    void dependenciasSeguemADirecaoCombinada() {
        assertThat(dependenciasDiretas("contas")).containsExactlyInAnyOrder("compartilhado");
        assertThat(dependenciasDiretas("categorias")).containsExactlyInAnyOrder("compartilhado", "acesso");
        assertThat(dependenciasDiretas("cartoes")).containsExactlyInAnyOrder("compartilhado", "contas");
        assertThat(dependenciasDiretas("lancamentos")).containsExactlyInAnyOrder("compartilhado", "contas", "categorias", "cartoes");
        assertThat(dependenciasDiretas("categorizacao")).containsExactlyInAnyOrder("compartilhado", "contas", "categorias", "cartoes",
                "lancamentos");
        assertThat(dependenciasDiretas("importacao"))
                .containsExactlyInAnyOrder("compartilhado", "contas", "lancamentos", "categorizacao", "cartoes");
        assertThat(dependenciasDiretas("relatorios")).containsExactlyInAnyOrder("compartilhado");
        assertThat(dependenciasDiretas("exportacao")).containsExactlyInAnyOrder("compartilhado", "acesso");
        assertThat(dependenciasDiretas("acesso"))
                .doesNotContain("contas", "categorias", "lancamentos", "importacao", "categorizacao", "cartoes");
        assertThat(dependenciasDiretas("recorrencias")).containsExactlyInAnyOrder("compartilhado", "contas", "categorias", "cartoes",
                "lancamentos");
        for (String modulo : Set.of("contas", "categorias", "cartoes", "lancamentos", "categorizacao", "importacao", "acesso", "relatorios",
                "recorrencias")) {
            assertThat(dependenciasDiretas(modulo)).doesNotContain("relatorios", "exportacao");
        }
        for (String modulo : Set.of("contas", "categorias", "cartoes", "lancamentos", "categorizacao", "importacao", "acesso", "relatorios",
                "exportacao")) {
            assertThat(dependenciasDiretas(modulo)).doesNotContain("recorrencias");
        }
    }

    private Set<String> dependenciasDiretas(String modulo) {
        return modules.getModuleByName(modulo).orElseThrow()
                .getDirectDependencies(modules)
                .uniqueModules()
                .map(dependencia -> dependencia.getIdentifier().toString())
                .collect(Collectors.toSet());
    }
}
