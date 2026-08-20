package com.gefinx.backend.compartilhado.config;

import com.gefinx.backend.compartilhado.seguranca.FiltroAutenticacaoJwt;
import com.gefinx.backend.compartilhado.seguranca.FiltroLimiteDeRequisicoes;
import com.gefinx.backend.compartilhado.seguranca.PontoDeEntradaNaoAutenticado;
import com.gefinx.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(PropriedadesLimiteDeRequisicoes.class)
public class SecurityConfig {

    private final FiltroAutenticacaoJwt filtroAutenticacaoJwt;
    private final FiltroLimiteDeRequisicoes filtroLimiteDeRequisicoes;
    private final CorsConfigurationSource origensPermitidas;
    private final PontoDeEntradaNaoAutenticado pontoDeEntradaNaoAutenticado;

    public SecurityConfig(
        FiltroAutenticacaoJwt filtroAutenticacaoJwt,
        FiltroLimiteDeRequisicoes filtroLimiteDeRequisicoes,
        CorsConfigurationSource origensPermitidas,
        PontoDeEntradaNaoAutenticado pontoDeEntradaNaoAutenticado
    ) {
        this.filtroAutenticacaoJwt = filtroAutenticacaoJwt;
        this.filtroLimiteDeRequisicoes = filtroLimiteDeRequisicoes;
        this.origensPermitidas = origensPermitidas;
        this.pontoDeEntradaNaoAutenticado = pontoDeEntradaNaoAutenticado;
    }

    @Bean
    public SecurityFilterChain cadeiaDeFiltros(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(origensPermitidas))
            .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(tratamento -> tratamento.authenticationEntryPoint(pontoDeEntradaNaoAutenticado))
            .authorizeHttpRequests(autorizacao -> autorizacao
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(filtroAutenticacaoJwt, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(filtroLimiteDeRequisicoes, FiltroAutenticacaoJwt.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder();
    }
}
