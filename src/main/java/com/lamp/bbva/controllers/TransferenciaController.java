package com.lamp.bbva.controllers;

import java.net.Authenticator;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.lamp.bbva.entity.cuentaEntity;
import com.lamp.bbva.entity.usuarioEntity;
import com.lamp.bbva.repository.usuarioRepository;
import com.lamp.bbva.service.BancaService;

@Controller
public class TransferenciaController {
    private final BancaService bancaService;
    private final usuarioRepository usuarioRepository;

    public TransferenciaController(BancaService bancaService, usuarioRepository usuarioRepository) {
        this.bancaService = bancaService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping("/transferencia")
    public String mostrarFormTranferencia(Model modelo, Authentication auth) {
        String username = auth.getName();
        usuarioEntity usuario = usuarioRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        String clabe = "No asignada";
        Double saldo = 0.0;
        String estadoCuenta = "ACTIVA";

        if (usuario.getCuentas() != null && !usuario.getCuentas().isEmpty()) {
            cuentaEntity cuentaPrincipal = usuario.getCuentas().get(0);
            clabe = cuentaPrincipal.getClabe();
            saldo = cuentaPrincipal.getSaldo();
            estadoCuenta = cuentaPrincipal.getEstado();
        }
        modelo.addAttribute("cuentaClabe", clabe);
        modelo.addAttribute("saldoTotal", saldo);
        modelo.addAttribute("cuentaEstado", estadoCuenta);
        return "transferencia";
    }

    @PostMapping("/procesar-transferencia")
    public String procesar(@RequestParam String clabeDestino,
            @RequestParam Double monto,
            @RequestParam String descripcion,
            Authentication auth) {

        String usernameAutorizado = auth.getName();
        try {
            bancaService.transferirDesdeUsuario(usernameAutorizado,
                    clabeDestino, monto, descripcion);
            return "redirect:/dashboard?exito=Tranferencia realizada con exito";
        } catch (Exception e) {
            return "redirect:/transferencia?error=" + e.getMessage();

        }

    }

}