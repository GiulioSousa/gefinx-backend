package com.gefinx.backend.usuarios.interfaces.web;

import com.gefinx.backend.usuarios.aplicacao.AutenticacaoService;
import com.gefinx.backend.usuarios.aplicacao.RegistroUseCase;
import com.gefinx.backend.usuarios.dominio.Usuario;
import com.gefinx.backend.usuarios.infraestrutura.JwtService;
import com.gefinx.backend.usuarios.interfaces.web.dto.RequisicaoLogin;
import com.gefinx.backend.usuarios.interfaces.web.dto.RequisicaoRegistro;
import com.gefinx.backend.usuarios.interfaces.web.dto.RespostaAutenticacao;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final RegistroUseCase registroUseCase;
    private final AutenticacaoService autenticacaoService;
    private final JwtService jwtService;

    public AutenticacaoController(RegistroUseCase registroUseCase, AutenticacaoService autenticacaoService, JwtService jwtService) {
        this.registroUseCase = registroUseCase;
        this.autenticacaoService = autenticacaoService;
        this.jwtService = jwtService;
    }

    @PostMapping("/registrar")
    public ResponseEntity<RespostaAutenticacao> registrar(@Valid @RequestBody RequisicaoRegistro requisicao) {
        Usuario usuario = registroUseCase.executar(requisicao.nome(), requisicao.email(), requisicao.senha());
        String token = jwtService.gerarToken(usuario);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new RespostaAutenticacao(token, usuario.getNome(), usuario.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<RespostaAutenticacao> login(@Valid @RequestBody RequisicaoLogin requisicao) {
        Usuario usuario = autenticacaoService.autenticar(requisicao.email(), requisicao.senha());
        String token = jwtService.gerarToken(usuario);
        return ResponseEntity.ok(new RespostaAutenticacao(token, usuario.getNome(), usuario.getEmail()));
    }
}
