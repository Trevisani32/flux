package com.flux.recorrencias.interno;

import java.time.LocalDate;
import java.util.List;

import com.flux.recorrencias.OcorrenciaPrevista;
import com.flux.recorrencias.RecorrenciasConsulta;
import com.flux.recorrencias.interno.dto.OcorrenciaResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaDeRecorrencias implements RecorrenciasConsulta {

    private final RecorrenciaService recorrencias;

    public ConsultaDeRecorrencias(RecorrenciaService recorrencias) {
        this.recorrencias = recorrencias;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OcorrenciaPrevista> previstas(Long usuarioId, LocalDate de, LocalDate ate) {
        return recorrencias.ocorrenciasVisiveis(usuarioId, de, ate).stream().map(ConsultaDeRecorrencias::prevista).toList();
    }

    private static OcorrenciaPrevista prevista(OcorrenciaResponse ocorrencia) {
        return new OcorrenciaPrevista(ocorrencia.recorrenciaId(), ocorrencia.periodo(), ocorrencia.data(), ocorrencia.descricao(),
                ocorrencia.tipo(), ocorrencia.valor(), ocorrencia.valorPrevisto(), ocorrencia.valorVaria(), ocorrencia.contaId(),
                ocorrencia.contaNome(), ocorrencia.cartaoId(), ocorrencia.cartaoNome(), ocorrencia.cartaoCor(), ocorrencia.categoriaId(),
                ocorrencia.categoriaNome(), ocorrencia.categoriaMaeNome(), ocorrencia.atrasada(), ocorrencia.visivelAte());
    }
}
