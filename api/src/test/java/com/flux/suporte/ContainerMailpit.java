package com.flux.suporte;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Mailpit dos testes: SMTP na 1025 e API HTTP na 8025 (portas mapeadas pelo Testcontainers). */
public class ContainerMailpit extends GenericContainer<ContainerMailpit> {

    public static final String IMAGEM = "axllent/mailpit:v1.33.0";

    public ContainerMailpit() {
        super(DockerImageName.parse(IMAGEM));
        withExposedPorts(1025, 8025);
        waitingFor(Wait.forHttp("/readyz").forPort(8025));
    }

    public int portaSmtp() {
        return getMappedPort(1025);
    }

    public String urlDaApi() {
        return "http://" + getHost() + ":" + getMappedPort(8025);
    }
}
