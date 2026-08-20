package com.gefinx.backend.usuarios.dominio;

import java.util.Locale;

/**
 * Forma canônica de um e-mail dentro do sistema.
 *
 * <p>O domínio é insensível a caixa por especificação, e todo provedor real trata a
 * parte local do mesmo modo — mas isso diz respeito à <b>entrega</b>. A unicidade aqui
 * compara a string que chegou, então {@code TESTE@exemplo.com} e {@code teste@exemplo.com}
 * criariam duas contas para a mesma pessoa. Reduzir tudo a uma forma só na entrada é o
 * que impede isso.
 *
 * <p>Existe como ponto único de propósito: a mesma regra decide qual conta já está
 * cadastrada, qual conta o login procura e qual balde a trava por tentativas usa. Se
 * essas definições divergirem, a trava volta a ser contornável apenas alternando a
 * caixa das letras.
 */
public final class NormalizadorDeEmail {

    private NormalizadorDeEmail() {
    }

    /**
     * O {@link Locale#ROOT} não é preciosismo: {@code toLowerCase()} sem locale usa o da
     * máquina, e em turco o minúsculo de {@code I} é {@code ı}. A mesma entrada
     * normalizaria diferente conforme onde a aplicação roda, e duas instâncias
     * discordariam sobre qual conta é qual.
     */
    public static String normalizar(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
