package com.flux;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

class ModularidadeTest {

    @Test
    void verificaModulos() {
        ApplicationModules modules = ApplicationModules.of(FluxApplication.class);
        modules.verify();
        new Documenter(modules).writeDocumentation();
    }
}
