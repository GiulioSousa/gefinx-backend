package com.gefinx.backend.financas.aplicacao;

import com.gefinx.backend.financas.dominio.Categoria;
import com.gefinx.backend.financas.dominio.Conta;
import com.gefinx.backend.financas.dominio.RepositorioCategoria;
import com.gefinx.backend.financas.dominio.RepositorioConta;
import com.gefinx.backend.financas.dominio.RepositorioTransacao;
import com.gefinx.backend.financas.dominio.SaldoDaConta;
import com.gefinx.backend.financas.dominio.TipoTransacao;
import com.gefinx.backend.financas.dominio.Transacao;
import com.gefinx.backend.financas.dominio.excecoes.ContaDuplicadaException;
import com.gefinx.backend.financas.dominio.excecoes.ContaEmUsoException;
import com.gefinx.backend.financas.dominio.excecoes.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ContaService {

    /**
     * Mesmo nome que a migration V7 deu à conta criada no backfill. Um usuário cadastrado
     * antes da V7 e outro cadastrado depois têm de encontrar a mesma coisa ao entrar.
     */
    public static final String NOME_PADRAO = "Conta principal";

    /** Categoria em que a transação de abertura cai, criada só quando alguém a usa. */
    static final String CATEGORIA_ABERTURA = "Saldo inicial";

    private final RepositorioConta repositorioConta;
    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioCategoria repositorioCategoria;

    public ContaService(
        RepositorioConta repositorioConta,
        RepositorioTransacao repositorioTransacao,
        RepositorioCategoria repositorioCategoria
    ) {
        this.repositorioConta = repositorioConta;
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioCategoria = repositorioCategoria;
    }

    public List<Conta> listar(Long usuarioId) {
        return repositorioConta.listarPorUsuario(usuarioId);
    }

    public Conta buscarPorId(Long usuarioId, Long id) {
        return buscarOuLancar(usuarioId, id);
    }

    public List<SaldoDaConta> resumirSaldos(Long usuarioId) {
        return repositorioTransacao.resumirSaldoPorConta(usuarioId);
    }

    /**
     * O saldo inicial vira uma transação de abertura, e não uma coluna na conta: assim o
     * saldo continua saindo de uma fonte só. A contrapartida, assumida ao decidir isso, é
     * que a abertura é uma transação como as outras — corrigir o valor informado no
     * cadastro é editá-la, e apagá-la zera a abertura.
     *
     * <p>Só aceita valor positivo ou zero. Uma abertura negativa precisaria de uma
     * despesa, e a única conta que nasce devendo é cartão de crédito — que tem fatura e
     * limite, e está fora deste desenho. Quem precisar registra a dívida como despesa
     * comum depois de criar a conta.
     */
    @Transactional
    public Conta criar(Long usuarioId, String nome, BigDecimal saldoInicial) {
        validarNaoDuplicada(usuarioId, nome);
        Conta conta = repositorioConta.salvar(Conta.nova(nome, usuarioId));

        if (saldoInicial != null && saldoInicial.signum() > 0) {
            registrarAbertura(usuarioId, conta, saldoInicial);
        }
        return conta;
    }

    @Transactional
    public Conta atualizar(Long usuarioId, Long id, String nome) {
        Conta contaExistente = buscarOuLancar(usuarioId, id);
        if (!contaExistente.getNome().equalsIgnoreCase(nome)) {
            validarNaoDuplicada(usuarioId, nome);
        }
        return repositorioConta.salvar(new Conta(id, nome, usuarioId));
    }

    /**
     * Recusa em vez de cascatear. Apagar a conta levaria junto lançamentos de anos, e a
     * decisão de descartá-los é do usuário, um a um — não efeito colateral de um clique.
     */
    @Transactional
    public void excluir(Long usuarioId, Long id) {
        Conta conta = buscarOuLancar(usuarioId, id);
        if (repositorioTransacao.existePorConta(conta.getId())) {
            throw new ContaEmUsoException();
        }
        repositorioConta.excluir(id);
    }

    /**
     * Criada no cadastro, pelo mesmo caminho das categorias padrão: sem ela o usuário
     * novo cairia num sistema em que nenhuma transação pode ser lançada, porque toda
     * transação exige conta.
     */
    public Conta criarContaPadrao(Long usuarioId) {
        return repositorioConta.salvar(Conta.nova(NOME_PADRAO, usuarioId));
    }

    private void registrarAbertura(Long usuarioId, Conta conta, BigDecimal saldoInicial) {
        Categoria categoria = repositorioCategoria
            .buscarPorNomeTipoUsuario(CATEGORIA_ABERTURA, TipoTransacao.RECEITA, usuarioId)
            .orElseGet(() -> repositorioCategoria.salvar(
                Categoria.nova(CATEGORIA_ABERTURA, TipoTransacao.RECEITA, usuarioId)
            ));

        repositorioTransacao.salvar(Transacao.nova(
            CATEGORIA_ABERTURA,
            saldoInicial,
            TipoTransacao.RECEITA,
            LocalDate.now(),
            categoria.getId(),
            conta.getId(),
            usuarioId
        ));
    }

    private Conta buscarOuLancar(Long usuarioId, Long id) {
        return repositorioConta.buscarPorIdEUsuario(id, usuarioId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Conta não encontrada"));
    }

    private void validarNaoDuplicada(Long usuarioId, String nome) {
        if (repositorioConta.existePorNomeEUsuario(nome, usuarioId)) {
            throw new ContaDuplicadaException(nome);
        }
    }
}
