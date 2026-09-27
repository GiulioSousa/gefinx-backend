package com.gefinx.backend.planejamento.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.planejamento.aplicacao.DespesaPlanejadaService;
import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.interfaces.web.dto.RequisicaoDespesaPlanejada;
import com.gefinx.backend.planejamento.interfaces.web.dto.RespostaDespesaPlanejada;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/despesas-planejadas")
public class DespesaPlanejadaController {

    private final DespesaPlanejadaService despesaPlanejadaService;

    public DespesaPlanejadaController(DespesaPlanejadaService despesaPlanejadaService) {
        this.despesaPlanejadaService = despesaPlanejadaService;
    }

    @GetMapping
    public List<RespostaDespesaPlanejada> listar() {
        return despesaPlanejadaService.listar(UsuarioAutenticado.obterId()).stream()
            .map(RespostaDespesaPlanejada::apartirDoDominio)
            .toList();
    }

    @PostMapping
    public ResponseEntity<RespostaDespesaPlanejada> criar(@Valid @RequestBody RequisicaoDespesaPlanejada requisicao) {
        DespesaPlanejada despesa = despesaPlanejadaService.criar(
            UsuarioAutenticado.obterId(), requisicao.descricao(), requisicao.valor(), requisicao.prazo()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(RespostaDespesaPlanejada.apartirDoDominio(despesa));
    }

    @PutMapping("/{id}")
    public RespostaDespesaPlanejada atualizar(@PathVariable Long id, @Valid @RequestBody RequisicaoDespesaPlanejada requisicao) {
        return RespostaDespesaPlanejada.apartirDoDominio(despesaPlanejadaService.atualizar(
            UsuarioAutenticado.obterId(), id, requisicao.descricao(), requisicao.valor(), requisicao.prazo()
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        despesaPlanejadaService.excluir(UsuarioAutenticado.obterId(), id);
        return ResponseEntity.noContent().build();
    }
}
