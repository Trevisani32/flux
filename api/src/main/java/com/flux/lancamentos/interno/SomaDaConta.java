package com.flux.lancamentos.interno;

import java.math.BigDecimal;

/** Soma dos lançamentos de uma conta (projeção da consulta do saldo). */
public record SomaDaConta(Long contaId, BigDecimal soma) {
}
