package com.lamp.bbva.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "solicitudes_credito")
public class SolicitudCreditoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private usuarioEntity usuario;

    @Column(name = "monto_solicitado", nullable = false)
    private Double montoSolicitado;

    // se usa LONGTEXT en Mysql para almacenar la cadena Base64 completa

    @Column(name = "firma_base64", nullable = false, columnDefinition = "LONGTEXT")
    private String firmaBase64;

    @Column(nullable = false)
    private String estado = "PENDIENTE";

    @Column(nullable = false)
    private LocalDateTime fecha;

    // Saldo que aun falta pagar del credito; se fija al monto solicitado cuando
    // se aprueba y se reduce con cada abono hasta llegar a 0 (credito PAGADO)
    @Column(name = "monto_pendiente")
    private Double montoPendiente;

}
