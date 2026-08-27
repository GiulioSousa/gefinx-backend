package com.gefinx.backend.financas.interfaces.web;

import com.gefinx.backend.compartilhado.seguranca.UsuarioAutenticado;
import com.gefinx.backend.financas.aplicacao.CalcularSaldoService;
import com.gefinx.backend.financas.aplicacao.ContaService;
import com.gefinx.backend.financas.dominio.Conta;
import com.gefinx.backend.financas.dominio.SaldoDaConta;
import com.gefinx.backend.financas.interfaces.web.dto.RequisicaoConta;
import com.gefinx.backend.financas.interfaces.web.dto.RespostaConta;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/contas")
public class ContaController {

    private final ContaService contaService;
    private final CalcularSaldoService calcularSaldoService;

    public ContaController(ContaService contaService, CalcularSaldoService calcularSaldoService) {
        this.contaService = contaService;
        this.calcularSaldoService = calcularSaldoService;
    }

    /**
     * O saldo de todas as contas sai de uma consulta agrupada só, e não de uma por conta:
     * a lista é o que a tela de contas e o seletor carregam a cada visita.
     */
    @GetMapping
    public List<RespostaConta> listar() {
        Long usuarioId = UsuarioAutenticado.obterId();
        Map<Long, BigDecimal> saldos = contaService.resumirSaldos(usuarioId).stream()
            .collect(Collectors.toMap(SaldoDaConta::contaId, SaldoDaConta::saldo));

        return contaService.listar(usuarioId).stream()
            // Desde a Etapa 20 o agrupamento parte de `contas`, então toda conta vem no
            // resultado, inclusive a que nunca recebeu lançamento. O valor padrão fica
            // como rede: uma conta sem saldo apurado vale zero, nunca nulo.
            .map(conta -> RespostaConta.apartirDoDominio(conta, saldos.getOrDefault(conta.getId(), BigDecimal.ZERO)))
            .toList();
    }

    @PostMapping
    public ResponseEntity<RespostaConta> criar(@Valid @RequestBody RequisicaoConta requisicao) {
        Long usuarioId = UsuarioAutenticado.obterId();
        Conta conta = contaService.criar(usuarioId, requisicao.nome(), requisicao.saldoInicial());
        return ResponseEntity.status(HttpStatus.CREATED).body(comSaldo(usuarioId, conta));
    }

    @PutMapping("/{id}")
    public RespostaConta atualizar(@PathVariable Long id, @Valid @RequestBody RequisicaoConta requisicao) {
        Long usuarioId = UsuarioAutenticado.obterId();
        return comSaldo(usuarioId, contaService.atualizar(usuarioId, id, requisicao.nome()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        contaService.excluir(UsuarioAutenticado.obterId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lê o saldo em vez de deduzi-lo do que a requisição trazia. Na criação com saldo
     * inicial os dois coincidem por construção — mas afirmar um número que não foi
     * consultado é como a Etapa 6 ecoava um valor que o banco não tinha gravado.
     */
    private RespostaConta comSaldo(Long usuarioId, Conta conta) {
        return RespostaConta.apartirDoDominio(
            conta, calcularSaldoService.calcularPorConta(usuarioId, conta.getId()).saldo()
        );
    }
}
