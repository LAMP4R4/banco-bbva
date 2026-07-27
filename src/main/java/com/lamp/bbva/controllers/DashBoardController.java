package com.lamp.bbva.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.lamp.bbva.entity.cuentaEntity;
import com.lamp.bbva.entity.movimientosEntity;
import com.lamp.bbva.entity.usuarioEntity;
import com.lamp.bbva.repository.movimientoCuentaRepository;
import com.lamp.bbva.repository.usuarioRepository;

@Controller
public class DashBoardController {

    private final usuarioRepository usuarioRepository;
    private final movimientoCuentaRepository movimientoCuentaRepository;

    public DashBoardController(usuarioRepository usuario, movimientoCuentaRepository movimientoCuenta) {
        this.usuarioRepository = usuario;
        this.movimientoCuentaRepository = movimientoCuenta;
    }

    @GetMapping("/")
    public String index() {
        return "redirect:/dashboard";

    }

    @GetMapping("/dashboard")
    public String mostrarDashboard(Model modelo, Authentication auth) {

        // obtener el usuario autenticado
        if (auth == null) {
            return "redirect:/login";

        }

        String userName = auth.getName();
        usuarioEntity usuario = usuarioRepository.findByUserName(userName)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if ("EJECUTIVO".equals(usuario.getRol())) {
            return "redirect:/admin/panel-ejecutivo";

        }

        // cargar datos del cliente
        String clabe = "No asignada";
        Double saldo = 0.0;
        List<movimientosEntity> ultimosMovimientos = new ArrayList<>();

        if (usuario.getCuentas() != null && !usuario.getCuentas().isEmpty()) {
            cuentaEntity cuentaPrincipal = usuario.getCuentas().get(0);
            clabe = cuentaPrincipal.getClabe();
            saldo = cuentaPrincipal.getSaldo();
            ultimosMovimientos = movimientoCuentaRepository
                    .findByCuentaOrigenOrCuentaDestinoOrderByFechaDescIdDesc(clabe, clabe);

        }

        // inyectar los datos al modelo de thymeleaf
        modelo.addAttribute("nombreCliente", usuario.getNombre());
        modelo.addAttribute("saldoTotal", saldo);
        modelo.addAttribute("cuentaClabe", clabe);
        modelo.addAttribute("movimientos", ultimosMovimientos);

        return "dashboard";
    }

    @GetMapping("/perfil")
    public String mostrarPerfil(Model modelo, Authentication auth) {

        if (auth == null) {
            return "redirect:/login";
        }

        String userName = auth.getName();
        usuarioEntity usuario = usuarioRepository.findByUserName(userName)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<cuentaEntity> cuentas = usuario.getCuentas() != null ? usuario.getCuentas() : new ArrayList<>();

        modelo.addAttribute("usuario", usuario);
        modelo.addAttribute("cuentas", cuentas);

        return "perfil";
    }

}
