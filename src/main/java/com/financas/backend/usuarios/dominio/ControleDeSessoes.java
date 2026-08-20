package com.financas.backend.usuarios.dominio;

import java.time.Instant;

/**
 * Decide se uma sessão continua valendo e permite encerrar todas as de um usuário.
 *
 * <p>Sem isto, um token entregue não pode mais ser recolhido: a assinatura e o prazo são a
 * única coisa que o filtro confere, e o "Sair" da tela apaga apenas a cópia local. Quem
 * tivesse o token entraria até ele expirar, 24h depois — vale para token roubado, máquina
 * compartilhada ou qualquer suspeita de comprometimento.
 */
public interface ControleDeSessoes {

    /**
     * @param emitidoEm instante gravado no token (`iat`)
     */
    boolean sessaoValida(Long usuarioId, Instant emitidoEm);

    /**
     * Invalida toda sessão existente do usuário, inclusive a de quem está pedindo.
     */
    void encerrarTodas(Long usuarioId);
}
