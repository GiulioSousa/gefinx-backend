package com.financas.backend.compartilhado.excecoes;

import com.financas.backend.financas.dominio.excecoes.CategoriaDuplicadaException;
import com.financas.backend.financas.dominio.excecoes.CategoriaEmUsoException;
import com.financas.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import com.financas.backend.usuarios.dominio.excecoes.CredenciaisInvalidasException;
import com.financas.backend.usuarios.dominio.excecoes.EmailJaCadastradoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ManipuladorGlobalDeExcecoes {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> tratarRecursoNaoEncontrado(RecursoNaoEncontradoException excecao) {
        return construirResposta(HttpStatus.NOT_FOUND, excecao.getMessage());
    }

    @ExceptionHandler({CategoriaEmUsoException.class, CategoriaDuplicadaException.class, EmailJaCadastradoException.class})
    public ResponseEntity<ErroResposta> tratarConflito(RuntimeException excecao) {
        return construirResposta(HttpStatus.CONFLICT, excecao.getMessage());
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResposta> tratarCredenciaisInvalidas(CredenciaisInvalidasException excecao) {
        return construirResposta(HttpStatus.UNAUTHORIZED, excecao.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> tratarValidacao(MethodArgumentNotValidException excecao) {
        Map<String, String> erros = new LinkedHashMap<>();
        excecao.getBindingResult().getFieldErrors()
            .forEach(erro -> erros.put(erro.getField(), erro.getDefaultMessage()));

        ErroResposta corpo = new ErroResposta(HttpStatus.BAD_REQUEST.value(), "Dados inválidos", erros);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(corpo);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> tratarErroInesperado(Exception excecao) {
        return construirResposta(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro inesperado");
    }

    private ResponseEntity<ErroResposta> construirResposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(new ErroResposta(status.value(), mensagem));
    }
}
