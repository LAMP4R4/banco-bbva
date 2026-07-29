package com.lamp.bbva.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lamp.bbva.entity.cuentaEntity;
import com.lamp.bbva.entity.movimientosEntity;
import com.lamp.bbva.entity.usuarioEntity;
import com.lamp.bbva.repository.movimientoCuentaRepository;
import com.lamp.bbva.repository.usuarioRepository;
import com.lamp.bbva.service.BancaService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class DashBoardController {

    private final usuarioRepository usuarioRepository;
    private final movimientoCuentaRepository movimientoCuentaRepository;
    private final BancaService bancaService;

    public DashBoardController(usuarioRepository usuario, movimientoCuentaRepository movimientoCuenta,
            BancaService bancaService) {
        this.usuarioRepository = usuario;
        this.movimientoCuentaRepository = movimientoCuenta;
        this.bancaService = bancaService;
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

    // El cliente edita su propio username, telefono, correo y (opcionalmente)
    // contraseña. Como username/password son las credenciales de la sesión,
    // al terminar se invalida la sesión y se manda a login para que vuelva a
    // autenticarse.
    @PostMapping("/perfil/editar")
    public String editarPerfil(@RequestParam String username, @RequestParam(required = false) String telefono,
            @RequestParam(required = false) String correo, @RequestParam(required = false) String password,
            @RequestParam(required = false) String confirmarPassword, Authentication auth,
            HttpServletRequest request, RedirectAttributes redirectAttributes) {

        if (auth == null) {
            return "redirect:/login";
        }

        if (password != null && !password.isBlank() && !password.equals(confirmarPassword)) {
            redirectAttributes.addAttribute("error", "Las contraseñas no coinciden");
            return "redirect:/perfil";
        }

        try {
            bancaService.actualizarPerfilPropio(auth.getName(), username, telefono, correo, password);
            request.getSession().invalidate();
            redirectAttributes.addAttribute("exito", "Datos actualizados, vuelve a iniciar sesión");
            return "redirect:/login";
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            return "redirect:/perfil";
        }
    }

}
