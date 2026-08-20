package com.financas.backend.usuarios.infraestrutura;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioSpringDataRepository extends JpaRepository<UsuarioJpaEntity, Long> {

    Optional<UsuarioJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);
}
