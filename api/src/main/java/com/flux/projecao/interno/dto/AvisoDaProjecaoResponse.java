package com.flux.projecao.interno.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AvisoDaProjecaoResponse(List<ContaNegativa> contas) {

    public record ContaNegativa(Long contaId, String contaNome, BigDecimal hoje, LocalDate primeiroNegativo,
                                BigDecimal menor, LocalDate diaDoMenor) {
    }
}
