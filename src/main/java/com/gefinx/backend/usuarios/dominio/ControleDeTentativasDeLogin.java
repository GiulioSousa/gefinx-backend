package com.gefinx.backend.usuarios.dominio;

/**
 * Limita quantas tentativas de login uma mesma conta aceita, independentemente da
 * origem delas.
 *
 * <p>Complementa o limite por endereço, que não alcança este caso: como cada IP tem
 * seu próprio balde, um atacante distribuído multiplica as tentativas contra uma
 * única conta apenas trocando de origem. Aqui a chave é o alvo, não a procedência.
 *
 * <p>Duas regras que o adaptador precisa respeitar, ambas por motivo de segurança:
 * a chave deve derivar do e-mail <b>normalizado</b>, senão variar a caixa rende um
 * balde novo a cada tentativa; e a contagem deve valer também para e-mails que não
 * existem, senão a resposta de bloqueio revelaria quais contas existem.
 */
public interface ControleDeTentativasDeLogin {

    /**
     * Contabiliza uma tentativa para o e-mail e informa se ela ainda é permitida.
     */
    ResultadoDaTentativa registrar(String email);

    /**
     * Devolve a cota cheia ao e-mail, chamado após autenticação bem-sucedida.
     */
    void liberar(String email);

    record ResultadoDaTentativa(boolean bloqueado, long segundosParaLiberar) {
    }
}
