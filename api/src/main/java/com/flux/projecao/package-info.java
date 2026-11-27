/**
 * Projeção de saldo (Fatia 07b, ADR 0023): só leitura, nada gravado. Lê as contas e o saldo pelas consultas de contas, os
 * cartões pela de cartões, os lançamentos futuros e as faturas pela LancamentosDaProjecao e as ocorrências pela
 * RecorrenciasConsulta; ninguém depende dele.
 */
package com.flux.projecao;
