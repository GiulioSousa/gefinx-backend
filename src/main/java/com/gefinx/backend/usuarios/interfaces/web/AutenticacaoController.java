package com.gefinx.backend.usuarios.interfaces.web;

import com.gefinx.backend.usuarios.aplicacao.AutenticacaoService;
import com.gefinx.backend.usuarios.dominio.Usuario;
import com.gefinx.backend.usuarios.infraestrutura.JwtService;
import com.gefinx.backend.usuarios.interfaces.web.dto.RequisicaoLogin;
import com.gefinx.backend.usuarios.interfaces.web.dto.RespostaAutenticacao;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Só login. O cadastro pela API deixou de existir: sem verificação de e-mail, a rota
 * aberta deixava qualquer um que alcançasse o endereço criar conta e usar o sistema, e
 * verificar endereço exigiria um canal de envio que o projeto não tem. As contas passam
 * a nascer no banco, por quem opera — ver backend/db/criar-usuario.sql.
 */
@RestController
@RequestMapping("/api/auth")
public class AutenticacaoController {

    private final AutenticacaoService autenticacaoService;
    private final JwtService jwtService;

    public AutenticacaoController(AutenticacaoService autenticacaoService, JwtService jwtService) {
        this.autenticacaoService = autenticacaoService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<RespostaAutenticacao> login(@Valid @RequestBody RequisicaoLogin requisicao) {
        Usuario usuario = autenticacaoService.autenticar(requisicao.usuario(), requisicao.senha());
        String token = jwtService.gerarToken(usuario);
        return ResponseEntity.ok(new RespostaAutenticacao(token, usuario.getUsuario()));
    }
}
