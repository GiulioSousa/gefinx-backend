package com.financas.backend.usuarios.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UsuarioSpringDataRepository extends JpaRepository<UsuarioJpaEntity, Long> {

    Optional<UsuarioJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Só a marca, não o usuário inteiro: esta consulta corre a cada requisição autenticada
     * que não estiver em cache, e carregar o registro completo para ler um campo seria
     * desperdício no caminho mais quente do sistema.
     */
    @Query("SELECT u.sessoesValidasApos FROM UsuarioJpaEntity u WHERE u.id = :id")
    Optional<LocalDateTime> buscarSessoesValidasApos(@Param("id") Long id);

    @Modifying
    @Query("UPDATE UsuarioJpaEntity u SET u.sessoesValidasApos = :instante WHERE u.id = :id")
    int atualizarSessoesValidasApos(@Param("id") Long id, @Param("instante") LocalDateTime instante);
}
