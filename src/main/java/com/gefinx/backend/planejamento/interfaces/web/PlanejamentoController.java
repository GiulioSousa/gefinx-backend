package com.gefinx.backend.planejamento.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.planejamento.aplicacao.PlanejamentoService;
import com.gefinx.backend.planejamento.interfaces.web.dto.RequisicaoCategoriasDoCustoDiario;
import com.gefinx.backend.planejamento.interfaces.web.dto.RequisicaoContasDeFora;
import com.gefinx.backend.planejamento.interfaces.web.dto.RespostaCategoriaDoPlanejamento;
import com.gefinx.backend.planejamento.interfaces.web.dto.RespostaContaDoPlanejamento;
import com.gefinx.backend.planejamento.interfaces.web.dto.RespostaPlanejamento;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/planejamento")
public class PlanejamentoController {

    private final PlanejamentoService planejamentoService;

    public PlanejamentoController(PlanejamentoService planejamentoService) {
        this.planejamentoService = planejamentoService;
    }

    @GetMapping
    public RespostaPlanejamento plano() {
        return RespostaPlanejamento.apartirDoPlano(planejamentoService.montar(UsuarioAutenticado.obterId()));
    }

    /** Todas as contas, cada uma dizendo se entra — é a lista que a tela mostra para marcar. */
    @GetMapping("/contas")
    public List<RespostaContaDoPlanejamento> contas() {
        return listarContas(UsuarioAutenticado.obterId());
    }

    @PutMapping("/contas")
    public List<RespostaContaDoPlanejamento> definirContasDeFora(@Valid @RequestBody RequisicaoContasDeFora requisicao) {
        Long usuarioId = UsuarioAutenticado.obterId();
        planejamentoService.definirContasDeFora(usuarioId, requisicao.contasDeFora());
        return listarContas(usuarioId);
    }

    /** As categorias de despesa, cada uma dizendo se entra no custo diário — a lista que a tela mostra para marcar. */
    @GetMapping("/categorias")
    public List<RespostaCategoriaDoPlanejamento> categorias() {
        return listarCategorias(UsuarioAutenticado.obterId());
    }

    @PutMapping("/categorias")
    public List<RespostaCategoriaDoPlanejamento> definirCategoriasDoCustoDiario(
        @Valid @RequestBody RequisicaoCategoriasDoCustoDiario requisicao
    ) {
        Long usuarioId = UsuarioAutenticado.obterId();
        planejamentoService.definirCategoriasDoCustoDiario(usuarioId, requisicao.categoriasDoCustoDiario());
        return listarCategorias(usuarioId);
    }

    private List<RespostaCategoriaDoPlanejamento> listarCategorias(Long usuarioId) {
        Set<Long> doCusto = planejamentoService.listarCategoriasDoCustoDiario(usuarioId);
        return planejamentoService.listarCategoriasDeDespesa(usuarioId).stream()
            .map(categoria -> new RespostaCategoriaDoPlanejamento(
                categoria.id(), categoria.nome(), doCusto.contains(categoria.id())))
            .toList();
    }

    private List<RespostaContaDoPlanejamento> listarContas(Long usuarioId) {
        Set<Long> deFora = planejamentoService.listarContasDeFora(usuarioId);
        return planejamentoService.listarContas(usuarioId).stream()
            .map(conta -> new RespostaContaDoPlanejamento(conta.id(), conta.nome(), !deFora.contains(conta.id())))
            .toList();
    }
}
