package com.gefinx.backend.financas.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.financas.aplicacao.CategoriaService;
import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.interfaces.web.dto.RequisicaoCategoria;
import com.gefinx.backend.financas.interfaces.web.dto.RespostaCategoria;
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
@RequestMapping("/api/categorias")
public class CategoriaController {

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public List<RespostaCategoria> listar() {
        return categoriaService.listar(UsuarioAutenticado.obterId()).stream()
            .map(RespostaCategoria::apartirDoDominio)
            .toList();
    }

    @PostMapping
    public ResponseEntity<RespostaCategoria> criar(@Valid @RequestBody RequisicaoCategoria requisicao) {
        Categoria categoria = categoriaService.criar(UsuarioAutenticado.obterId(), requisicao.nome(), requisicao.tipo());
        return ResponseEntity.status(HttpStatus.CREATED).body(RespostaCategoria.apartirDoDominio(categoria));
    }

    @PutMapping("/{id}")
    public RespostaCategoria atualizar(@PathVariable Long id, @Valid @RequestBody RequisicaoCategoria requisicao) {
        Categoria categoria = categoriaService.atualizar(UsuarioAutenticado.obterId(), id, requisicao.nome(), requisicao.tipo());
        return RespostaCategoria.apartirDoDominio(categoria);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        categoriaService.excluir(UsuarioAutenticado.obterId(), id);
        return ResponseEntity.noContent().build();
    }
}
