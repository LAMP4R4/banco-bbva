package com.lamp.bbva.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lamp.bbva.entity.cuentaEntity;
import com.lamp.bbva.entity.usuarioEntity;

import java.util.List;

@Repository

public interface cuentaRepository extends JpaRepository<cuentaEntity, Long> {
    Optional<cuentaEntity> findByClabe(String clabe);

    List<cuentaEntity> findByUsuario(usuarioEntity usuario);
}
