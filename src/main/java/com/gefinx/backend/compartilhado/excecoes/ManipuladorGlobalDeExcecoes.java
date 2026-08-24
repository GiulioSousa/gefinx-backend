package com.gefinx.backend.compartilhado.excecoes;

import com.gefinx.backend.financas.dominio.excecoes.CategoriaDuplicadaException;
import com.gefinx.backend.financas.dominio.excecoes.ContaDuplicadaException;
import com.gefinx.backend.financas.dominio.excecoes.ContaEmUsoException;
import com.gefinx.backend.financas.dominio.excecoes.CategoriaEmUsoException;
import com.gefinx.backend.financas.dominio.excecoes.TipoDaCategoriaEmUsoException;
import com.gefinx.backend.financas.dominio.excecoes.TipoIncompativelComCategoriaException;
import com.gefinx.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import com.gefinx.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import com.gefinx.backend.usuarios.dominio.excecoes.EmailJaCadastradoException;
import com.gefinx.backend.usuarios.dominio.excecoes.TentativasExcedidasException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class ManipuladorGlobalDeExcecoes {

    private static final Logger log = LoggerFactory.getLogger(ManipuladorGlobalDeExcecoes.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> tratarRecursoNaoEncontrado(RecursoNaoEncontradoException excecao) {
        return construirResposta(HttpStatus.NOT_FOUND, excecao.getMessage());
    }

    @ExceptionHandler(TipoIncompativelComCategoriaException.class)
    public ResponseEntity<ErroResposta> tratarTipoIncompativel(TipoIncompativelComCategoriaException excecao) {
        return construirResposta(HttpStatus.BAD_REQUEST, excecao.getMessage());
    }

    @ExceptionHandler({CategoriaEmUsoException.class, CategoriaDuplicadaException.class, ContaEmUsoException.class, ContaDuplicadaException.class, EmailJaCadastradoException.class, TipoDaCategoriaEmUsoException.class})
    public ResponseEntity<ErroResposta> tratarConflito(RuntimeException excecao) {
        return construirResposta(HttpStatus.CONFLICT, excecao.getMessage());
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResposta> tratarCredenciaisInvalidas(CredenciaisInvalidasException excecao) {
        return construirResposta(HttpStatus.UNAUTHORIZED, excecao.getMessage());
    }

    @ExceptionHandler(TentativasExcedidasException.class)
    public ResponseEntity<ErroResposta> tratarTentativasExcedidas(TentativasExcedidasException excecao) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(excecao.getSegundosParaLiberar()))
            .body(new ErroResposta(HttpStatus.TOO_MANY_REQUESTS.value(), excecao.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> tratarValidacao(MethodArgumentNotValidException excecao) {
        Map<String, String> erros = new LinkedHashMap<>();
        excecao.getBindingResult().getFieldErrors()
            .forEach(erro -> erros.put(erro.getField(), erro.getDefaultMessage()));

        ErroResposta corpo = new ErroResposta(HttpStatus.BAD_REQUEST.value(), "Dados inválidos", erros);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo);
    }

    /**
     * Rota que não existe é erro de quem chamou, não falha do servidor.
     *
     * <p>Sem este manipulador, qualquer caminho fora do mapa caía no tratamento genérico
     * e voltava como `500` — inclusive a própria rota certa escrita com barra no fim, que
     * o Spring 6 deixou de casar por padrão. Um cliente que recebe `500` ao errar o
     * caminho conclui que a API está quebrada.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResposta> tratarRotaInexistente(HttpServletRequest requisicao) {
        log.debug("Rota inexistente: {} {}", requisicao.getMethod(), requisicao.getRequestURI());
        return construirResposta(HttpStatus.NOT_FOUND, "Recurso não encontrado");
    }

    /**
     * Corpo que o conversor não consegue ler: JSON truncado, campo com tipo incompatível,
     * bytes que não formam UTF-8 válido.
     *
     * <p>A mensagem devolvida é fixa de propósito. A da exceção traz posição no fluxo e
     * nomes de classes do domínio — detalhe que ajuda quem depura e desenha o sistema para
     * quem sonda. Ele fica no log, que é onde tem leitor legítimo.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResposta> tratarCorpoIlegivel(HttpMessageNotReadableException excecao) {
        log.debug("Corpo da requisição ilegível: {}", excecao.getMessage());
        return construirResposta(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido");
    }

    /**
     * Verbo que a rota não aceita. O cabeçalho {@code Allow} acompanha a resposta porque
     * a RFC 9110 o exige em `405`, e sem ele o cliente não descobre o que fazer.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResposta> tratarMetodoNaoSuportado(HttpRequestMethodNotSupportedException excecao) {
        var resposta = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);

        Set<HttpMethod> metodosAceitos = excecao.getSupportedHttpMethods();
        if (metodosAceitos != null && !metodosAceitos.isEmpty()) {
            resposta.allow(metodosAceitos.toArray(new HttpMethod[0]));
        }

        return resposta.body(new ErroResposta(
            HttpStatus.METHOD_NOT_ALLOWED.value(),
            "Método não permitido para este recurso"
        ));
    }

    /** Corpo enviado num formato que a API não recebe — tipicamente sem `Content-Type` de JSON. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroResposta> tratarTipoNaoSuportado(HttpMediaTypeNotSupportedException excecao) {
        return construirResposta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de conteúdo não suportado");
    }

    /**
     * Trecho do caminho que não converte para o tipo esperado, como {@code /transacoes/abc}
     * onde se espera um id numérico.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResposta> tratarParametroComTipoErrado(MethodArgumentTypeMismatchException excecao) {
        return construirResposta(HttpStatus.BAD_REQUEST, "Parâmetro inválido: " + excecao.getName());
    }

    /**
     * Último recurso: o que chega aqui não foi previsto por nenhum manipulador acima.
     *
     * <p>Registrar a exceção inteira é o ponto deste método. A resposta é, e continua sendo,
     * genérica — detalhe de exceção em corpo de resposta vira mapa do sistema para quem
     * sondar a API. Mas engolir a exceção sem registrá-la em lugar nenhum torna todo `500`
     * indistinguível de qualquer outro, e foi o que atrapalhou o diagnóstico duas vezes
     * durante a auditoria: sem rastro, um `500` não diz sequer em que camada nasceu.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> tratarErroInesperado(Exception excecao, HttpServletRequest requisicao) {
        log.error("Erro inesperado em {} {}", requisicao.getMethod(), requisicao.getRequestURI(), excecao);
        return construirResposta(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado");
    }

    private ResponseEntity<ErroResposta> construirResposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(new ErroResposta(status.value(), mensagem));
    }
}
