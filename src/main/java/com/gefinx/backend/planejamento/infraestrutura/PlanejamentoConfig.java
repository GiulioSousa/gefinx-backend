package com.gefinx.backend.planejamento.infraestrutura;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(PropriedadesPlanejamento.class)
public class PlanejamentoConfig {

    /** Um relógio injetado, e não {@code LocalDate.now()}, para que o teste escolha o dia. */
    @Bean
    public Clock relogioDoPlanejamento(PropriedadesPlanejamento propriedades) {
        return Clock.system(propriedades.fusoHorario());
    }
}
