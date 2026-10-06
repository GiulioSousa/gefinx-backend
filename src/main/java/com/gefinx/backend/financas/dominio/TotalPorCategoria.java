package com.gefinx.backend.financas.dominio;

import java.math.BigDecimal;

public record TotalPorCategoria(Long categoriaId, BigDecimal total) {
}
