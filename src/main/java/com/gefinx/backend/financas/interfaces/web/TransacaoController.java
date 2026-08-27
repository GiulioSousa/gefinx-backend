package com.gefinx.backend.financas.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.financas.aplicacao.CategoriaService;
import com.gefinx.backend.financas.aplicacao.ContaService;
import com.gefinx.backend.financas.aplicacao.TransacaoService;
import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.Conta;
import com.gefinx.backend.financas.dominio.Transacao;
import com.gefinx.backend.financas.interfaces.web.dto.RequisicaoTransacao;
import com.gefinx.backend.financas.interfaces.web.dto.RespostaTransacao;
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
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transacoes")
public class TransacaoController {

    private final TransacaoService transacaoService;
    private final CategoriaService categoriaService;
    private final ContaService contaService;

    public TransacaoController(
        TransacaoService transacaoService,
        CategoriaService categoriaService,
        ContaService contaService
    ) {
        this.transacaoService = transacaoService;
        this.categoriaService = categoriaService;
        this.contaService = contaService;
    }

    @GetMapping
    public List<RespostaTransacao> listar() {
        Long usuarioId = UsuarioAutenticado.obterId();
        Map<Long, String> nomesPorCategoria = categoriaService.listar(usuarioId).stream()
            .collect(Collectors.toMap(Categoria::getId, Categoria::getNome));
        Map<Long, String> nomesPorConta = contaService.listar(usuarioId).stream()
            .collect(Collectors.toMap(Conta::getId, Conta::getNome));

        // Map.get(null) devolve null sem estourar, então o id ausente — categoria numa
        // transferência, destino num lançamento comum — já cai naturalmente em nome nulo.
        return transacaoService.listar(usuarioId).stream()
            .map(transacao -> RespostaTransacao.apartirDoDominio(
                transacao,
                nomesPorCategoria.get(transacao.getCategoriaId()),
                nomesPorConta.get(transacao.getContaId()),
                nomesPorConta.get(transacao.getContaDestinoId())
            ))
            .toList();
    }

    @PostMapping
    public ResponseEntity<RespostaTransacao> criar(@Valid @RequestBody RequisicaoTransacao requisicao) {
        Long usuarioId = UsuarioAutenticado.obterId();
        Transacao transacao = transacaoService.criar(
            usuarioId, requisicao.descricao(), requisicao.valor(), requisicao.tipo(),
            requisicao.categoriaId(), requisicao.contaId(), requisicao.contaDestinoId(),
            requisicao.dataTransacao()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(paraResposta(usuarioId, transacao));
    }

    @PutMapping("/{id}")
    public RespostaTransacao atualizar(@PathVariable Long id, @Valid @RequestBody RequisicaoTransacao requisicao) {
        Long usuarioId = UsuarioAutenticado.obterId();
        Transacao transacao = transacaoService.atualizar(
            usuarioId, id, requisicao.descricao(), requisicao.valor(), requisicao.tipo(),
            requisicao.categoriaId(), requisicao.contaId(), requisicao.contaDestinoId(),
            requisicao.dataTransacao()
        );
        return paraResposta(usuarioId, transacao);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        transacaoService.excluir(UsuarioAutenticado.obterId(), id);
        return ResponseEntity.noContent().build();
    }

    private RespostaTransacao paraResposta(Long usuarioId, Transacao transacao) {
        String nomeCategoria = transacao.getCategoriaId() == null
            ? null
            : categoriaService.buscarPorId(usuarioId, transacao.getCategoriaId()).getNome();
        String nomeContaDestino = transacao.getContaDestinoId() == null
            ? null
            : contaService.buscarPorId(usuarioId, transacao.getContaDestinoId()).getNome();
        String nomeConta = contaService.buscarPorId(usuarioId, transacao.getContaId()).getNome();

        return RespostaTransacao.apartirDoDominio(transacao, nomeCategoria, nomeConta, nomeContaDestino);
    }
}
