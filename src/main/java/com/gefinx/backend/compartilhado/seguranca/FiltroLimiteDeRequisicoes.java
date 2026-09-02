package com.gefinx.backend.compartilhado.seguranca;

import com.gefinx.backend.compartilhado.auditoria.Auditoria;
import com.gefinx.backend.compartilhado.excecoes.ErroResposta;
import com.gefinx.backend.compartilhado.seguranca.PropriedadesLimiteDeRequisicoes.Politica;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

/**
 * Protege o login contra força bruta, limitando a quantidade de tentativas por origem.
 *
 * <p>A lista tem uma rota só desde que o cadastro deixou de existir. Ela continua sendo
 * uma lista, e não uma rota fixa: o desenho — casar por padrão, derivar a chave do nome
 * do balde — é o que fecha o desvio descrito abaixo, e é ele que precisa valer para a
 * próxima rota pública que aparecer.
 *
 * <p>O reconhecimento da rota usa {@link PathPatternRequestMatcher}, e não o valor
 * cru de {@code getRequestURI()}. A distinção é essencial: {@code getRequestURI()}
 * devolve o caminho exatamente como o cliente o escreveu, enquanto o Spring MVC
 * roteia pelo caminho já decodificado e normalizado. Comparar a forma crua contra
 * uma constante permitia escapar do limite apenas percent-encoding uma letra —
 * {@code /api/auth/%6Cogin} não casava aqui, mas chegava ao controlador de login.
 * Casando pelo mesmo caminho que a cadeia de autorização usa, as duas visões não
 * podem mais divergir.
 *
 * <p>A origem vem de {@code getRemoteAddr()}, e não de cabeçalhos como
 * {@code X-Forwarded-For} — que o cliente poderia forjar para escapar do limite.
 * Ao publicar atrás de um proxy reverso, configure
 * {@code server.forward-headers-strategy}, para que o próprio contêiner resolva o
 * endereço real antes de chegar aqui.
 */
@Component
public class FiltroLimiteDeRequisicoes extends OncePerRequestFilter {

    private final List<RotaLimitada> rotasLimitadas;
    private final LimitadorDeRequisicoes limitador;
    private final ObjectMapper objectMapper;

    public FiltroLimiteDeRequisicoes(
        LimitadorDeRequisicoes limitador,
        PropriedadesLimiteDeRequisicoes propriedades,
        ObjectMapper objectMapper
    ) {
        this.limitador = limitador;
        this.objectMapper = objectMapper;
        this.rotasLimitadas = List.of(
            new RotaLimitada("login", rotaPost("/api/auth/login"), propriedades.login())
        );
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return rotaPara(request) == null;
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        RotaLimitada rota = rotaPara(request);

        var resultado = limitador.verificar(rota.chavePara(request), rota.politica());
        if (!resultado.permitido()) {
            // A origem só existe aqui: a camada de aplicação não enxerga a requisição, e
            // arrastá-la até lá para poder registrar o IP custaria mais do que a trilha vale.
            Auditoria.LOG.warn(
                "limite por origem excedido rota={} origem={} liberaEm={}s",
                rota.nome(), request.getRemoteAddr(), resultado.segundosParaLiberar()
            );
            responderLimiteExcedido(response, resultado.segundosParaLiberar());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RotaLimitada rotaPara(HttpServletRequest request) {
        return rotasLimitadas.stream()
            .filter(rota -> rota.corresponde(request))
            .findFirst()
            .orElse(null);
    }

    private static RequestMatcher rotaPost(String caminho) {
        return PathPatternRequestMatcher.pathPattern(HttpMethod.POST, caminho);
    }

    private void responderLimiteExcedido(HttpServletResponse response, long segundosParaLiberar) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(segundosParaLiberar));

        ErroResposta corpo = new ErroResposta(
            HttpStatus.TOO_MANY_REQUESTS.value(),
            "Muitas tentativas. Tente novamente em " + segundosParaLiberar + " segundos."
        );
        objectMapper.writeValue(response.getWriter(), corpo);
    }

    /**
     * Rota sujeita a limite, com o nome que identifica seu bucket.
     *
     * <p>A chave deriva do {@code nome}, nunca do caminho recebido: usar o caminho
     * cru daria um bucket novo a cada grafia diferente da mesma rota, restaurando
     * por outro caminho a mesma brecha que o casamento por padrão fecha.
     */
    private record RotaLimitada(String nome, RequestMatcher rota, Politica politica) {

        boolean corresponde(HttpServletRequest request) {
            return rota.matches(request);
        }

        String chavePara(HttpServletRequest request) {
            return nome + ":" + request.getRemoteAddr();
        }
    }
}
