package com.lamp.bbva.entity;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResumenMensualDTO {
    private String mes;
    private Double ingresos;
    private Double gastos;

}
