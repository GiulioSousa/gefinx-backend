package com.gefinx.backend.planejamento.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.planejamento.aplicacao.DespesaPlanejadaService;
import com.gefinx.backend.planejamento.dominio.DespesaPlanejada;
import com.gefinx.backend.planejamento.interfaces.web.dto.RequisicaoDespesaPlanejada;
import com.gefinx.backend.planejamento.interfaces.web.dto.RequisicaoPagamento;
import com.gefinx.backend.planejamento.interfaces.web.dto.RespostaDesfazerPagamento;
import com.gefinx.backend.planejamento.interfaces.web.dto.RespostaDespesaPaga;
import com.gefinx.backend.planejamento.interfaces.web.dto.RespostaDespesaPlanejada;
import com.gefinx.backend.planejamento.interfaces.web.dto.RespostaPagamento;
import com.gefinx.backend.planejamento.interfaces.web.dto.RespostaPagina;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    /** Os mesmos limites de página e tamanho da listagem de transações. */
    @GetMapping("/pagas")
    public RespostaPagina<RespostaDespesaPaga> listarPagas(
        @RequestParam(defaultValue = "0") @Min(value = 0, message = "A página não pode ser negativa") int pagina,
        @RequestParam(defaultValue = "20")
        @Min(value = 1, message = "O tamanho deve ser no mínimo 1")
        @Max(value = 100, message = "O tamanho deve ser no máximo 100") int tamanho
    ) {
        return RespostaPagina.apartirDoDominio(
            despesaPlanejadaService.listarPagas(UsuarioAutenticado.obterId(), pagina, tamanho),
            RespostaDespesaPaga::apartirDoDominio
        );
    }

    @PostMapping
    public ResponseEntity<RespostaDespesaPlanejada> criar(@Valid @RequestBody RequisicaoDespesaPlanejada requisicao) {
        DespesaPlanejada despesa = despesaPlanejadaService.criar(
            UsuarioAutenticado.obterId(), requisicao.descricao(), requisicao.valor(), requisicao.prazo(), requisicao.recorrencia(),
            requisicao.parcela(), requisicao.totalDeParcelas(), requisicao.etiquetaIds()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(RespostaDespesaPlanejada.apartirDoDominio(despesa));
    }

    /** {@code serie=true} leva a mudança às outras pendentes da série; sem ele, só a esta ocorrência. */
    @PutMapping("/{id}")
    public RespostaDespesaPlanejada atualizar(
        @PathVariable Long id,
        @RequestParam(defaultValue = "false") boolean serie,
        @Valid @RequestBody RequisicaoDespesaPlanejada requisicao
    ) {
        return RespostaDespesaPlanejada.apartirDoDominio(despesaPlanejadaService.atualizar(
            UsuarioAutenticado.obterId(), id, requisicao.descricao(), requisicao.valor(), requisicao.prazo(),
            requisicao.recorrencia(), requisicao.parcela(), requisicao.totalDeParcelas(), requisicao.etiquetaIds(), serie
        ));
    }

    @PostMapping("/{id}/pagamento")
    public ResponseEntity<RespostaPagamento> pagar(@PathVariable Long id, @Valid @RequestBody RequisicaoPagamento requisicao) {
        Long transacaoId = despesaPlanejadaService.pagar(
            UsuarioAutenticado.obterId(), id, requisicao.descricao(), requisicao.valor(), requisicao.dataTransacao(),
            requisicao.categoriaId(), requisicao.contaId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(new RespostaPagamento(transacaoId));
    }

    /** {@code 200} com o corpo, e não {@code 204}: o desfecho da próxima ocorrência é parte da resposta. */
    @DeleteMapping("/{id}/pagamento")
    public RespostaDesfazerPagamento desfazerPagamento(@PathVariable Long id) {
        return new RespostaDesfazerPagamento(despesaPlanejadaService.desfazerPagamento(UsuarioAutenticado.obterId(), id));
    }

    /** {@code serie=true} exclui todas as pendentes da série; sem ele, só esta ocorrência. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean serie) {
        despesaPlanejadaService.excluir(UsuarioAutenticado.obterId(), id, serie);
        return ResponseEntity.noContent().build();
    }
}
