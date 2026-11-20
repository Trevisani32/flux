package com.flux.contas.interno;

import java.util.List;
import java.util.Optional;

import com.flux.contas.ContaResumo;
import com.flux.contas.ContasConsulta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaDeContas implements ContasConsulta {

    private final ContaRepository repositorio;

    public ConsultaDeContas(ContaRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Sem transação aberta, a trava sairia junto com a consulta: MANDATORY recusa isso. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void travar(Long usuarioId) {
        repositorio.travarContasDoUsuario(usuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContaResumo> buscar(Long usuarioId, Long contaId) {
        return repositorio.findByIdAndUsuarioId(contaId, usuarioId).map(ConsultaDeContas::resumo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContaResumo> listar(Long usuarioId) {
        return repositorio.findAllByUsuarioId(usuarioId).stream().map(ConsultaDeContas::resumo).toList();
    }

    private static ContaResumo resumo(Conta conta) {
        return new ContaResumo(conta.getId(), conta.getNome(), conta.getSaldoInicial(), conta.getDataSaldoInicial(),
                conta.arquivada(), conta.getTipo().name());
    }
}
