package com.lamp.bbva.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lamp.bbva.entity.GastosDTO;
import com.lamp.bbva.entity.ResumenMensualDTO;
import com.lamp.bbva.entity.movimientosEntity;
import com.lamp.bbva.entity.usuarioEntity;
import com.lamp.bbva.repository.movimientoCuentaRepository;
import com.lamp.bbva.repository.usuarioRepository;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/v1/finanzas")
public class FinanzasRestController {

    // estancia de los repositorios
    private final usuarioRepository usuarioRepository;
    private final movimientoCuentaRepository movimientoCuentaRepository;

    private static final Map<String, String> COLOR_MAP = new HashMap<>();
    static {
        COLOR_MAP.put("Alimentacion", "#FF6384");
        COLOR_MAP.put("Vivienda", "#36A2EB");
        COLOR_MAP.put("Transporte", "#FFCE56");
        COLOR_MAP.put("Otros", "#4BC0C0");
        COLOR_MAP.put("Servicios", "#9966FF");
        COLOR_MAP.put("Ocio", "#FF9F40");
        COLOR_MAP.put("Comida", "#C9CBCF");
        COLOR_MAP.put("Renta", "#2ECC71");
        COLOR_MAP.put("Nomina", "#E74C3C");

    }

    private static final List<String> PALETA_COLORES = Arrays
            .asList("#FF6384", "#36A2EB", "#FFCE56",
                    "#4BC0C0", "#9966FF", "#FF9F40", "#C9CBCF", "#2ECC71", "#E74C3C");

    // private final movimientoCuentaRepository movimientoCuentaRepository;
    // private final usuarioRepository usuarioRepository;

    public FinanzasRestController(usuarioRepository usuarioRepository,
            movimientoCuentaRepository movimientoCuentaRepository) {
        this.usuarioRepository = usuarioRepository;
        this.movimientoCuentaRepository = movimientoCuentaRepository;
    }

    @GetMapping("/gastos-mes")
    public List<GastosDTO> obtenerGastos(Authentication auth) {
        // datos de ejemplo, en un caso real se obtendrían de una base de datos

        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Usuario no identificado");
        }

        String username = auth.getName();
        Optional<usuarioEntity> usuarioOpt = usuarioRepository.findByUserName(username);
        if (usuarioOpt.isEmpty()) {
            throw new RuntimeException("Usuario no encontrado");
        }

        usuarioEntity usuario = usuarioOpt.get();
        if (usuario.getCuentas() == null || usuario.getCuentas().isEmpty()) {
            throw new RuntimeException("El ususario no tiene cuenta");
        }

        String clabe = usuario.getCuentas().get(0).getClabe();
        List<movimientosEntity> movimientos = movimientoCuentaRepository.findByCuentaOrigen(clabe);

        if (movimientos == null || movimientos.isEmpty()) {
            throw new RuntimeException("No se encontraron Movimientos");
        }

        // agrupar por descripcion y sumar montos

        Map<String, Double> gastosAgrupados = movimientos.stream()
                .collect(Collectors.groupingBy(movimientosEntity::getDescripcion,
                        Collectors.summingDouble(movimientosEntity::getMonto)));

        // mapear los DTO y asignar colores

        List<GastosDTO> resultado = new ArrayList<>();
        int colorIdx = 0;
        for (Map.Entry<String, Double> entry : gastosAgrupados.entrySet()) {

            String descripcion = entry.getKey();
            Double monto = entry.getValue();

            String color = COLOR_MAP.get(descripcion.trim());
            if (color == null) {
                color = PALETA_COLORES.get(
                        colorIdx % PALETA_COLORES.size());
                colorIdx++;
            }
            resultado.add(new GastosDTO(descripcion, monto, color));
        }
        return resultado;

    }

    @GetMapping("/resumen-mensual")
    public List<ResumenMensualDTO> obtenerResumenMensual(Authentication auth) {

        if (auth == null || !auth.isAuthenticated()) {
            throw new RuntimeException("Usuario no identificado");
        }

        String username = auth.getName();
        usuarioEntity usuario = usuarioRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (usuario.getCuentas() == null || usuario.getCuentas().isEmpty()) {
            throw new RuntimeException("El usuario no tiene cuenta");
        }

        String clabe = usuario.getCuentas().get(0).getClabe();
        List<movimientosEntity> movimientos = movimientoCuentaRepository
                .findByCuentaOrigenOrCuentaDestinoOrderByFechaDescIdDesc(clabe, clabe);

        // agrupar ingresos y gastos por mes
        Map<YearMonth, double[]> resumenPorMes = new TreeMap<>();
        for (movimientosEntity mov : movimientos) {
            YearMonth mes = YearMonth.from(mov.getFecha());
            double[] totales = resumenPorMes.computeIfAbsent(mes, m -> new double[2]);
            if (clabe.equals(mov.getCuentaDestino())) {
                totales[0] += mov.getMonto();
            }
            if (clabe.equals(mov.getCuentaOrigen())) {
                totales[1] += mov.getMonto();
            }
        }

        DateTimeFormatter formatoMes = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("es", "MX"));
        List<ResumenMensualDTO> resultado = new ArrayList<>();
        for (Map.Entry<YearMonth, double[]> entry : resumenPorMes.entrySet()) {
            String etiqueta = entry.getKey().format(formatoMes);
            resultado.add(new ResumenMensualDTO(etiqueta, entry.getValue()[0], entry.getValue()[1]));
        }

        return resultado;
    }

}
