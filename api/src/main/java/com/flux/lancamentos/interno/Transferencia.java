package com.flux.lancamentos.interno;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * O vínculo entre os dois lançamentos de uma transferência (ADR 0013). Data, valor, conta e
 * descrição ficam nos lançamentos.
 */
@Entity
@Table(name = "transferencias")
public class Transferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    protected Transferencia() {
    }

    public Transferencia(Long usuarioId, Instant agora) {
        this.usuarioId = usuarioId;
        this.criadaEm = agora;
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }
}
