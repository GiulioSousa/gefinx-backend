package com.gefinx.backend.usuarios.dominio;

import java.util.Locale;

/**
 * Forma canônica de um nome de usuário dentro do sistema.
 *
 * <p>A unicidade compara a string gravada, então {@code Fulano} e {@code fulano} seriam
 * duas contas distintas. Reduzir tudo a uma forma só na entrada é o que impede isso — e
 * é também o que faz alguém digitar o próprio nome com a caixa "errada" e ainda assim
 * entrar.
 *
 * <p>Existe como ponto único de propósito: a mesma regra decide qual conta o login
 * procura e qual balde a trava por tentativas usa. Se essas definições divergirem, a
 * trava volta a ser contornável apenas alternando a caixa das letras.
 *
 * <p>A tabela guarda a mesma invariante numa CHECK (ver a V10). Isso não é redundância:
 * a conta agora nasce por um INSERT feito à mão, sem a borda da aplicação no caminho, e
 * um nome gravado fora da forma canônica seria uma conta que o login nunca encontraria.
 */
public final class NormalizadorDeUsuario {

    private NormalizadorDeUsuario() {
    }

    /**
     * O {@link Locale#ROOT} não é preciosismo: {@code toLowerCase()} sem locale usa o da
     * máquina, e em turco o minúsculo de {@code I} é {@code ı}. A mesma entrada
     * normalizaria diferente conforme onde a aplicação roda, e duas instâncias
     * discordariam sobre qual conta é qual.
     */
    public static String normalizar(String usuario) {
        return usuario == null ? null : usuario.trim().toLowerCase(Locale.ROOT);
    }
}
