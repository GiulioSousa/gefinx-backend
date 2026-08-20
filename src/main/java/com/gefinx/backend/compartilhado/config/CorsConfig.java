package com.gefinx.backend.compartilhado.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Libera o frontend de desenvolvimento, e só ele.
 *
 * <p>Sem {@code allowCredentials}: a autenticação desta API viaja no cabeçalho
 * {@code Authorization}, que é um cabeçalho comum e não uma credencial no sentido do CORS.
 * Ligar a bandeira faria o navegador anexar cookies e cabeçalhos de autenticação HTTP às
 * requisições entre origens, e obrigaria o servidor a ecoar a origem de volta — permissão
 * que nada aqui usa. Se um dia a sessão passar a viver em cookie {@code httpOnly}, item que
 * está na fila, ela volta, e aí valendo alguma coisa.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource origensPermitidas() {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(List.of("http://localhost:5173"));
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource origem = new UrlBasedCorsConfigurationSource();
        origem.registerCorsConfiguration("/**", configuracao);
        return origem;
    }
}
