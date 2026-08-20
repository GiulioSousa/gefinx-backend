package com.financas.backend.usuarios.aplicacao;

import com.financas.backend.compartilhado.auditoria.Auditoria;
import com.financas.backend.usuarios.dominio.ControleDeSessoes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EncerrarSessoesService {

    private final ControleDeSessoes controleDeSessoes;

    public EncerrarSessoesService(ControleDeSessoes controleDeSessoes) {
        this.controleDeSessoes = controleDeSessoes;
    }

    /**
     * Encerra todas as sessões do usuário, inclusive a que fez o pedido — é o que se espera
     * de "sair de todos os dispositivos", e deixar a atual de fora daria a impressão falsa de
     * que ela é de algum modo especial.
     */
    @Transactional
    public void executar(Long usuarioId) {
        controleDeSessoes.encerrarTodas(usuarioId);
        Auditoria.LOG.info("sessoes encerradas usuario={}", usuarioId);
    }
}
