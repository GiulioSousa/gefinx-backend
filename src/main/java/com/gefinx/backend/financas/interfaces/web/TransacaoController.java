package com.gefinx.backend.financas.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.financas.aplicacao.CategoriaService;
import com.gefinx.backend.financas.aplicacao.ContaService;
import com.gefinx.backend.financas.aplicacao.TransacaoComNomes;
import com.gefinx.backend.financas.aplicacao.TransacaoService;
import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.Conta;
import com.gefinx.backend.financas.dominio.FiltroDeTransacoes;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.interfaces.web.dto.RequisicaoTransacao;
import com.gefinx.backend.financas.interfaces.web.dto.RespostaPagina;
import com.gefinx.backend.financas.interfaces.web.dto.RespostaTransacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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

    /**
     * A listagem, paginada e com recortes opcionais.
     *
     * <p>O teto de {@code tamanho} não é decoração: sem ele, {@code ?tamanho=1000000}
     * devolveria o histórico inteiro numa requisição e desfaria em silêncio a única coisa
     * que esta rota passou a garantir. E o limite recusa em vez de aparar o valor para 100,
     * pelo mesmo motivo que o valor fora de escala é recusado desde a Etapa 6: corrigir
     * calado o número que o cliente mandou faz a resposta descrever um pedido que ninguém
     * fez.
     *
     * <p>Filtro por conta ou categoria que não pertence a quem pediu devolve página vazia,
     * e não {@code 404}. A consulta é fechada por {@code usuario_id} antes de qualquer
     * filtro, então não há o que vazar; e um recorte de listagem, ao contrário de
     * {@code /saldo?contaId=}, não afirma nada sobre a existência do recurso — só descreve
     * o que casa com ele.
     */
    @GetMapping
    public RespostaPagina<RespostaTransacao> listar(
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa") int pagina,
        @RequestParam(defaultValue = "20")
        @Min(value = 1, message = "O tamanho deve ser no mínimo 1")
        @Max(value = 100, message = "O tamanho deve ser no máximo 100") int tamanho,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
        @RequestParam(required = false) TipoTransacao tipo,
        @RequestParam(required = false) Long contaId,
        @RequestParam(required = false) Long categoriaId
    ) {
        Long usuarioId = UsuarioAutenticado.obterId();
        Map<Long, String> nomesPorCategoria = categoriaService.listar(usuarioId).stream()
            .collect(Collectors.toMap(Categoria::getId, Categoria::getNome));
        Map<Long, String> nomesPorConta = contaService.listar(usuarioId).stream()
            .collect(Collectors.toMap(Conta::getId, Conta::getNome));

        FiltroDeTransacoes filtro = new FiltroDeTransacoes(dataInicio, dataFim, tipo, contaId, categoriaId);

        // Map.get(null) devolve null sem estourar, então o id ausente — categoria numa
        // transferência, destino num lançamento comum — já cai naturalmente em nome nulo.
        return RespostaPagina.apartirDoDominio(
            transacaoService.listar(usuarioId, filtro, pagina, tamanho),
            transacao -> RespostaTransacao.apartirDoDominio(
                transacao,
                nomesPorCategoria.get(transacao.getCategoriaId()),
                nomesPorConta.get(transacao.getContaId()),
                nomesPorConta.get(transacao.getContaDestinoId())
            )
        );
    }

    @PostMapping
    public ResponseEntity<RespostaTransacao> criar(@Valid @RequestBody RequisicaoTransacao requisicao) {
        Long usuarioId = UsuarioAutenticado.obterId();
        TransacaoComNomes resultado = transacaoService.criar(
            usuarioId, requisicao.descricao(), requisicao.valor(), requisicao.tipo(),
            requisicao.categoriaId(), requisicao.contaId(), requisicao.contaDestinoId(),
            requisicao.dataTransacao()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(paraResposta(resultado));
    }

    @PutMapping("/{id}")
    public RespostaTransacao atualizar(@PathVariable Long id, @Valid @RequestBody RequisicaoTransacao requisicao) {
        Long usuarioId = UsuarioAutenticado.obterId();
        TransacaoComNomes resultado = transacaoService.atualizar(
            usuarioId, id, requisicao.descricao(), requisicao.valor(), requisicao.tipo(),
            requisicao.categoriaId(), requisicao.contaId(), requisicao.contaDestinoId(),
            requisicao.dataTransacao()
        );
        return paraResposta(resultado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        transacaoService.excluir(UsuarioAutenticado.obterId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Os nomes ja vem do caso de uso, que os carregou para validar as referencias. Antes
     * eram buscados de novo aqui — ate tres consultas por escrita para reler o que a
     * validacao acabara de ler.
     */
    private RespostaTransacao paraResposta(TransacaoComNomes resultado) {
        return RespostaTransacao.apartirDoDominio(
            resultado.transacao(),
            resultado.nomeCategoria(),
            resultado.nomeConta(),
            resultado.nomeContaDestino()
        );
    }
}
