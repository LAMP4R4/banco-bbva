package com.lamp.bbva.controllers;

import java.lang.ProcessBuilder.Redirect;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.lamp.bbva.repository.movimientoCuentaRepository;
import com.lamp.bbva.service.BancaService;

import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
@RequestMapping("/admin/movimientos")
public class MovimientoAdminController {
    @Autowired
    private BancaService bancaService;

    // mostramos la vista con la lista
    @GetMapping
    public String listaMomvimientos(Model modelo) {
        modelo.addAttribute("movimientos", bancaService.todosMovimientos());
        return "adminmovimientos";
    }

    // ACTUALIZAR MOVIMIENTO
    @PostMapping("/actualizar-estado")
    public String actualizarEstadi(@RequestParam Long id, @RequestParam String estado,
            RedirectAttributes redirectAttributes) {
        try {
            bancaService.actualizarMovimiento(id, estado);
            redirectAttributes.addAttribute("exito", "El estado del movimiento cambio a " + estado);

        } catch (Exception e) {

            redirectAttributes.addAttribute("error", e.getMessage());

        }
        return "redirect:/admin/movimientos";
    }

    @PostMapping("/eliminar")
    public String eliminarMovimiento(@RequestParam Long id, RedirectAttributes redirectAttributes) {
        try {
            bancaService.eliminarMovimiento(id);
            redirectAttributes.addAttribute("exito", "Movimiento Eliminado");
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", "El error es: " + e.getMessage());
        }

        return "redirect:/admin/movimientos";
    }

}
