/**
 * Recorrências (Fatia 07a, ADR 0022): o cadastro, as ocorrências calculadas, a confirmação, o "Já lancei" e o pular.
 * Lê contas, cartões e categorias pelas consultas deles, grava lançamentos e compras pela LancamentosDasRecorrencias,
 * implementa o PrevistosNaLista de lançamentos e o UsoDaCategoria de categorias e, desde a Fatia 07b, expõe a
 * RecorrenciasConsulta, que a projeção usa (ADR 0023).
 */
@org.springframework.modulith.ApplicationModule
package com.flux.recorrencias;
