package com.gefinx.backend.usuarios.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.usuarios.aplicacao.EncerrarSessoesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fica fora de {@code /api/auth/**} de propósito.
 *
 * <p>Aquele prefixo é {@code permitAll} no {@code SecurityConfig}, para que login e cadastro
 * sejam alcançáveis sem token. Um endpoint de encerrar sessões ali nasceria público — e como
 * o id viria de algum lugar, qualquer um derrubaria as sessões de qualquer pessoa. Em
 * {@code /api/sessoes} o {@code anyRequest().authenticated()} já o protege, e o id sai do
 * {@code SecurityContext}, nunca da requisição.
 */
@RestController
@RequestMapping("/api/sessoes")
public class SessaoController {

    private final EncerrarSessoesService encerrarSessoesService;

    public SessaoController(EncerrarSessoesService encerrarSessoesService) {
        this.encerrarSessoesService = encerrarSessoesService;
    }

    @DeleteMapping
    public ResponseEntity<Void> encerrarTodas() {
        encerrarSessoesService.executar(UsuarioAutenticado.obterId());
        return ResponseEntity.noContent().build();
    }
}
