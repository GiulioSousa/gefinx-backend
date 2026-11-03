package com.gefinx.backend.planejamento.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.planejamento.aplicacao.EtiquetaService;
import com.gefinx.backend.planejamento.interfaces.web.dto.RequisicaoEtiqueta;
import com.gefinx.backend.planejamento.interfaces.web.dto.RespostaEtiqueta;
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

/** As etiquetas das despesas planejadas. Excluir uma deixa sem etiqueta as despesas que ela marcava. */
@RestController
@RequestMapping("/api/planejamento/etiquetas")
public class EtiquetaController {

    private final EtiquetaService etiquetaService;

    public EtiquetaController(EtiquetaService etiquetaService) {
        this.etiquetaService = etiquetaService;
    }

    @GetMapping
    public List<RespostaEtiqueta> listar() {
        return etiquetaService.listar(UsuarioAutenticado.obterId()).stream()
            .map(RespostaEtiqueta::apartirDoUso)
            .toList();
    }

    @PostMapping
    public ResponseEntity<RespostaEtiqueta> criar(@Valid @RequestBody RequisicaoEtiqueta requisicao) {
        RespostaEtiqueta etiqueta = RespostaEtiqueta.apartirDoUso(
            etiquetaService.criar(UsuarioAutenticado.obterId(), requisicao.nome(), requisicao.cor())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(etiqueta);
    }

    @PutMapping("/{id}")
    public RespostaEtiqueta atualizar(@PathVariable Long id, @Valid @RequestBody RequisicaoEtiqueta requisicao) {
        return RespostaEtiqueta.apartirDoUso(
            etiquetaService.atualizar(UsuarioAutenticado.obterId(), id, requisicao.nome(), requisicao.cor())
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        etiquetaService.excluir(UsuarioAutenticado.obterId(), id);
        return ResponseEntity.noContent().build();
    }
}
