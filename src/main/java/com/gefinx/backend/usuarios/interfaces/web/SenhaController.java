package com.gefinx.backend.usuarios.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.usuarios.aplicacao.TrocarSenhaService;
import com.gefinx.backend.usuarios.aplicacao.TrocarSenhaService.SenhaTrocada;
import com.gefinx.backend.usuarios.infraestrutura.JwtService;
import com.gefinx.backend.usuarios.interfaces.web.dto.RequisicaoTrocaDeSenha;
import com.gefinx.backend.usuarios.interfaces.web.dto.RespostaAutenticacao;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A senha de quem está na sessão. Fora de {@code /api/auth/**} pelo mesmo motivo do
 * {@link SessaoController}: ali tudo é público, e o id sai do token, nunca da requisição.
 */
@RestController
@RequestMapping("/api/conta")
public class SenhaController {

    private final TrocarSenhaService trocarSenhaService;
    private final JwtService jwtService;

    public SenhaController(TrocarSenhaService trocarSenhaService, JwtService jwtService) {
        this.trocarSenhaService = trocarSenhaService;
        this.jwtService = jwtService;
    }

    /**
     * Responde com um token novo, como o login: a troca encerra todas as sessões, inclusive a
     * deste pedido, e sem ele a tela cairia no login logo depois de trocar a senha.
     */
    @PutMapping("/senha")
    public RespostaAutenticacao trocarSenha(@Valid @RequestBody RequisicaoTrocaDeSenha requisicao) {
        SenhaTrocada trocada = trocarSenhaService.trocar(
            UsuarioAutenticado.obterId(), requisicao.senhaAtual(), requisicao.novaSenha()
        );
        String token = jwtService.gerarToken(trocada.usuario(), trocada.sessoesValidasApos().plusMillis(1));
        return new RespostaAutenticacao(token, trocada.usuario().getUsuario());
    }
}
