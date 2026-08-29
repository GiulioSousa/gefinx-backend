package com.gefinx.backend.compartilhado.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * As origens autorizadas a chamar esta API de outro domínio.
 *
 * <p>Saiu do código na Etapa 22, quando a aplicação passou a ter um destino fora desta
 * máquina: até então a única origem permitida era o servidor de desenvolvimento do Vite,
 * escrita literalmente na configuração do CORS. Um endereço de `localhost` compilado dentro
 * do artefato não tem como estar certo em produção.
 *
 * <p>Publicada atrás de um proxy que serve o frontend e a API sob o **mesmo domínio**, esta
 * lista deixa de ser exercida: requisição de mesma origem não passa por CORS. Ela continua
 * existindo para o caso de o frontend viver em domínio próprio, e para que o
 * desenvolvimento siga funcionando com as duas portas separadas.
 *
 * @param origens origens permitidas, como o navegador as envia — esquema, host e porta,
 *                sem barra final. Vazia recusa qualquer requisição entre origens, que é o
 *                padrão correto para quem serve tudo sob o mesmo domínio.
 */
@ConfigurationProperties(prefix = "gefinx.cors")
public record PropriedadesCors(List<String> origens) {

    public PropriedadesCors {
        origens = origens == null ? List.of() : List.copyOf(origens);
    }
}
