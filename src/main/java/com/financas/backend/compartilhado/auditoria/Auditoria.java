package com.financas.backend.compartilhado.auditoria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Trilha de auditoria: quem tentou entrar, quando e com que resultado.
 *
 * <p>Usa um logger de nome próprio, separado dos loggers por classe, porque a trilha
 * responde a uma pergunta diferente da do log de aplicação e sobrevive a decisões
 * diferentes: abaixar o nível de {@code com.financas.backend} para calar a depuração
 * não pode, junto, apagar o registro de quem entrou. O nome fixo permite endereçá-la
 * sozinha na configuração e isolá-la num arquivo próprio quando for preciso.
 *
 * <p>A senha nunca entra na trilha, em circunstância alguma. O e-mail entra, por ser o
 * único identificador da tentativa — inclusive das que não correspondem a conta nenhuma,
 * que são justamente as que interessam ao investigar força bruta.
 *
 * <p>A trilha distingue "e-mail inexistente" de "senha incorreta", coisa que a resposta
 * HTTP deliberadamente não faz (Etapa 5). Não há contradição: a resposta vai para quem
 * tentou entrar, e a trilha, para quem opera o sistema. Confundir os dois destinos foi o
 * que a Etapa 5 corrigiu — apagar a distinção aqui não protegeria ninguém e cegaria o
 * único leitor legítimo dela.
 */
public final class Auditoria {

    /** Nome do logger, endereçável em {@code logging.level}. */
    public static final String NOME = "AUDITORIA";

    public static final Logger LOG = LoggerFactory.getLogger(NOME);

    /** Teto de um valor na trilha, alinhado ao maior campo de texto aceito na borda. */
    private static final int LIMITE_DE_CARACTERES = 120;

    private Auditoria() {
    }

    /**
     * Prepara um valor vindo do cliente para entrar na trilha.
     *
     * <p>Quebras de linha viram {@code _} porque cada evento ocupa exatamente uma linha:
     * um valor contendo {@code \n} forjaria uma segunda linha e inventaria um evento que
     * nunca aconteceu. A validação da borda já recusaria esse valor antes de chegar aqui,
     * mas uma trilha que depende de quem a chama para não ser falsificada não é trilha.
     */
    public static String seguro(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "-";
        }

        String semQuebras = valor.replaceAll("[\r\n]", "_");
        return semQuebras.length() <= LIMITE_DE_CARACTERES
            ? semQuebras
            : semQuebras.substring(0, LIMITE_DE_CARACTERES) + "...";
    }
}
