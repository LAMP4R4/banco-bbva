package com.lamp.bbva.controllers;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.lamp.bbva.entity.SolicitudCreditoEntity;
import com.lamp.bbva.entity.usuarioEntity;
import com.lamp.bbva.repository.solicitudCreditoRepository;
import com.lamp.bbva.repository.usuarioRepository;
import com.lamp.bbva.service.BancaService;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Controller

public class CreditoController {

    private final BancaService bancaService;
    private final solicitudCreditoRepository solicitudCreditoRepository;
    private final usuarioRepository usuarioRepository;

    public CreditoController(BancaService bancaService,
            com.lamp.bbva.repository.solicitudCreditoRepository solicitudCreditoRepository,
            com.lamp.bbva.repository.usuarioRepository usuarioRepository) {
        this.bancaService = bancaService;
        this.solicitudCreditoRepository = solicitudCreditoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // VISTA DE MENU CREDITOS
    @GetMapping("/credito")
    public String mostrarFormularioCredito(Model modelo, Authentication auth) {

        String username = auth.getName();
        usuarioEntity usuario = usuarioRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        List<SolicitudCreditoEntity> solicitudes = solicitudCreditoRepository.findByUsuarioOrderByFechaDesc(usuario);

        String estadoCuenta = "ACTIVA";
        if (usuario.getCuentas() != null && !usuario.getCuentas().isEmpty()) {
            estadoCuenta = usuario.getCuentas().get(0).getEstado();
        }

        modelo.addAttribute("solicitudes", solicitudes);
        modelo.addAttribute("cuentaEstado", estadoCuenta);
        return "credito";
    }

    // RUTA DE PROCESAR CREDITO

    @PostMapping("/procesar-credito")
    public String procesarCredito(
            @RequestParam Double monto,
            @RequestParam String firmaBase64,
            Authentication auth) {

        String username = auth.getName();

        if (monto == null || monto <= 0) {
            return "redirect:/credito?error=El monto debe ser mayor a 0";

        }
        if (firmaBase64 == null || firmaBase64.trim().isEmpty()) {
            return "redirect:/credito?error=La firma es OBLIGATORIA";

        }
        try {
            bancaService.guardarSolicitudCredito(username, monto, firmaBase64);
            return "redirect:/credito?exito= Credito firmado y en espera de validacion";

        } catch (Exception e) {
            return "redirect:/credito?error=" + e.getMessage();
        }
    }

}
