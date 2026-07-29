package com.lamp.bbva.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lamp.bbva.entity.SolicitudCreditoEntity;
import com.lamp.bbva.entity.usuarioEntity;

@Repository
public interface solicitudCreditoRepository extends JpaRepository<SolicitudCreditoEntity, Long> {
    List<SolicitudCreditoEntity> findByUsuarioOrderByFechaDesc(usuarioEntity usuario);

    List<SolicitudCreditoEntity> findAllByOrderByFechaDesc();

    // El credito mas antiguo del cliente que sigue con saldo por pagar; es el
    // unico que puede abonarse mientras exista (orden de pago FIFO)
    Optional<SolicitudCreditoEntity> findFirstByUsuarioAndEstadoOrderByFechaAsc(usuarioEntity usuario, String estado);
}
