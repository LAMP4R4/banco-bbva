package com.lamp.bbva.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lamp.bbva.entity.SolicitudCreditoEntity;
import com.lamp.bbva.entity.usuarioEntity;

@Repository
public interface solicitudCreditoRepository extends JpaRepository<SolicitudCreditoEntity, Long> {
    List<SolicitudCreditoEntity> findByUsuarioOrderByFechaDesc(usuarioEntity usuario);

    List<SolicitudCreditoEntity> findAllByOrderByFechaDesc();
}
