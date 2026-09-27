package com.gefinx.backend.planejamento.infraestrutura;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * @param fusoHorario o fuso em que "hoje" é decidido. As datas das transações são as do
 *                    calendário de quem lança, e o dia do planejamento tem de ser o mesmo —
 *                    não o do servidor, que num VPS costuma estar em UTC.
 */
@ConfigurationProperties(prefix = "gefinx.planejamento")
public record PropriedadesPlanejamento(ZoneId fusoHorario) {

    public PropriedadesPlanejamento {
        fusoHorario = fusoHorario == null ? ZoneId.of("America/Sao_Paulo") : fusoHorario;
    }
}
