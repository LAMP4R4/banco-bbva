package com.lamp.bbva.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "movimientos")
public class movimientosEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cuenta_origen", nullable = false, length = 18)
    private String cuentaOrigen;

    @Column(name = "cuenta_destino", nullable = false, length = 18)
    private String cuentaDestino;

    @Column(nullable = false)
    private Double monto;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private String descripcion;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private String estadoMovimiento = "PENDIENTE";

}
