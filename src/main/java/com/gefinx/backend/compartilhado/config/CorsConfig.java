package com.gefinx.backend.compartilhado.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Libera as origens declaradas em {@link PropriedadesCors}, e só elas.
 *
 * <p>Sem {@code allowCredentials}: a autenticação desta API viaja no cabeçalho
 * {@code Authorization}, que é um cabeçalho comum e não uma credencial no sentido do CORS.
 * Ligar a bandeira faria o navegador anexar cookies e cabeçalhos de autenticação HTTP às
 * requisições entre origens, e obrigaria o servidor a ecoar a origem de volta — permissão
 * que nada aqui usa. Se um dia a sessão passar a viver em cookie {@code httpOnly}, item que
 * está na fila, ela volta, e aí valendo alguma coisa.
 *
 * <p>A lista de origens vem de configuração desde a Etapa 22. Antes era
 * {@code http://localhost:5173} escrito aqui dentro, o que impedia qualquer publicação:
 * mudar de destino exigiria recompilar o artefato.
 */
@Configuration
@EnableConfigurationProperties(PropriedadesCors.class)
public class CorsConfig {

    private final PropriedadesCors propriedades;

    public CorsConfig(PropriedadesCors propriedades) {
        this.propriedades = propriedades;
    }

    @Bean
    public CorsConfigurationSource origensPermitidas() {
        CorsConfiguration configuracao = new CorsConfiguration();
        // setAllowedOrigins, e nunca setAllowedOriginPatterns: o segundo aceita curingas, e
        // um "*" aqui autorizaria qualquer site a chamar a API em nome de quem a visitasse.
        configuracao.setAllowedOrigins(propriedades.origens());
        configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource origem = new UrlBasedCorsConfigurationSource();
        origem.registerCorsConfiguration("/**", configuracao);
        return origem;
    }
}
