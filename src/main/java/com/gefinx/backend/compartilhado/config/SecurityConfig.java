package com.gefinx.backend.compartilhado.config;

import com.gefinx.backend.compartilhado.seguranca.FiltroAutenticacaoJwt;
import com.gefinx.backend.compartilhado.seguranca.FiltroLimiteDeRequisicoes;
import com.gefinx.backend.compartilhado.seguranca.PontoDeEntradaNaoAutenticado;
import com.gefinx.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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

    /**
     * O health check, na porta de monitoramento, sem exigir autenticação.
     *
     * <p>Precisa vir antes da cadeia principal, cujo {@code anyRequest().authenticated()}
     * também alcança a porta de monitoramento — e alcançava: a primeira versão desta etapa
     * respondia `401` em {@code /actuator/health}, o que anula o endpoint. Quem supervisiona
     * o processo pergunta se ele está vivo justamente quando não há sessão alguma; um health
     * check que exige token não responde a ninguém.
     *
     * <p>Liberar é seguro porque o endpoint não está exposto: ele vive numa porta própria
     * ligada a {@code 127.0.0.1} (ver o bloco {@code management} do application.yml), fora do
     * proxy e fora do firewall, e o corpo não traz detalhe algum além de UP ou DOWN.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain cadeiaDeMonitoramento(HttpSecurity http) throws Exception {
        http
            .securityMatcher(EndpointRequest.toAnyEndpoint())
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(autorizacao -> autorizacao.anyRequest().permitAll());

        return http.build();
    }

    @Bean
    @Order(2)
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
