package com.lamp.bbva.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GastosDTO {
    private String descripcion;
    private Double monto;
    private String colorHexadecimal;

}
