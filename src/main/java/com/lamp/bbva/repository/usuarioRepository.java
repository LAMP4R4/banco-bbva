package com.lamp.bbva.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lamp.bbva.entity.usuarioEntity;

@Repository
public interface usuarioRepository extends JpaRepository<usuarioEntity, Long> {
    Optional<usuarioEntity> findByUserName(String username);

    Optional<usuarioEntity> findByNombre(String nombre);

}
