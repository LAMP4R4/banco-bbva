package com.lamp.bbva.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.lamp.bbva.entity.movimientosEntity;

@Repository

public interface movimientoCuentaRepository extends JpaRepository<movimientosEntity, Long> {
    List<movimientosEntity> findByCuentaOrigenOrCuentaDestinoOrderByFechaDescIdDesc(String cuentaOrigen,
            String cuentaDestino);

    List<movimientosEntity> findByCuentaOrigen(String cuentaOrigen);

    List<movimientosEntity> findAllByOrderByFechaDescIdDesc();

}
