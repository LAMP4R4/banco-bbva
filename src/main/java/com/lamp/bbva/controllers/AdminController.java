package com.lamp.bbva.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import com.lamp.bbva.entity.SolicitudCreditoEntity;
import com.lamp.bbva.entity.movimientosEntity;
import com.lamp.bbva.entity.usuarioEntity;
import com.lamp.bbva.repository.movimientoCuentaRepository;
import com.lamp.bbva.repository.solicitudCreditoRepository;
import com.lamp.bbva.repository.usuarioRepository;
import com.lamp.bbva.service.BancaService;
import com.lamp.bbva.service.PdfService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private final BancaService bancaService;
    private final usuarioRepository usuarioRepository;
    private final solicitudCreditoRepository solicitudCreditoRepository;
    private final movimientoCuentaRepository movimientoCuentaRepository;
    private final PdfService pdfService;

    public AdminController(BancaService bancaService, usuarioRepository usuarioRepository,
            solicitudCreditoRepository solicitudCreditoRepository,
            movimientoCuentaRepository movimientoCuentaRepository, PdfService pdfService) {
        this.bancaService = bancaService;
        this.usuarioRepository = usuarioRepository;
        this.solicitudCreditoRepository = solicitudCreditoRepository;
        this.movimientoCuentaRepository = movimientoCuentaRepository;
        this.pdfService = pdfService;
    }

    @GetMapping("/dashboard")
    public String mostrarDashboard(Model modelo) {

        // cargar info de cllientes en una lista
        List<usuarioEntity> clientes = usuarioRepository.findAll().stream().filter(u -> "CLIENTE".equals(u.getRol()))
                .collect(Collectors.toList());

        List<SolicitudCreditoEntity> solicitudes = solicitudCreditoRepository.findAllByOrderByFechaDesc();

        modelo.addAttribute("clientes", clientes);
        modelo.addAttribute("solicitudes", solicitudes);

        return "admin";
    }

    // WEA PARA CLIENTES
    @PostMapping("/crear-cliente")
    public String crearCliente(@RequestParam String nombre, @RequestParam String username,
            @RequestParam String password,
            @RequestParam Double saldoInicial,
            RedirectAttributes redirectAttributes) {

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty() ||
                nombre == null || nombre.trim().isEmpty()) {
            redirectAttributes.addAttribute("error", "Debes llenar todos los campos");
            return "redirect:/admin/dashboard";

        }
        if (saldoInicial == null || saldoInicial <= 0) {

            redirectAttributes.addAttribute("error", "El monto de apertura debe ser mayor a 0");
            return "redirect:/admin/dashboard";

        }
        try {

            bancaService.crearClienteConCuenta(nombre, username, password, saldoInicial);
            redirectAttributes.addAttribute("exito", "Cliente creado exitosamente");
            return "redirect:/admin/dashboard";

        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            return "redirect:/admin/dashboard";

        }

    }

    // Acciones sobre la cartera de clientes (usadas tanto desde /admin/dashboard
    // como desde /admin/registro-admin, por eso reciben a donde regresar)
    @PostMapping("/clientes/{id}/editar")
    public String editarCliente(@PathVariable Long id, @RequestParam String nombre, @RequestParam String username,
            @RequestParam(defaultValue = "/admin/dashboard") String origen, RedirectAttributes redirectAttributes) {
        try {
            bancaService.actualizarDatosCliente(id, nombre, username);
            redirectAttributes.addAttribute("exito", "Cliente actualizado correctamente");
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:" + validarOrigen(origen);
    }

    @PostMapping("/clientes/{id}/deshabilitar")
    public String deshabilitarCliente(@PathVariable Long id,
            @RequestParam(defaultValue = "/admin/dashboard") String origen, RedirectAttributes redirectAttributes) {
        try {
            bancaService.cambiarEstadoCuenta(id, "DESHABILITADA");
            redirectAttributes.addAttribute("exito", "Cliente deshabilitado");
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:" + validarOrigen(origen);
    }

    @PostMapping("/clientes/{id}/habilitar")
    public String habilitarCliente(@PathVariable Long id,
            @RequestParam(defaultValue = "/admin/dashboard") String origen, RedirectAttributes redirectAttributes) {
        try {
            bancaService.cambiarEstadoCuenta(id, "ACTIVA");
            redirectAttributes.addAttribute("exito", "Cliente habilitado");
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:" + validarOrigen(origen);
    }

    @PostMapping("/clientes/{id}/retener-saldo")
    public String retenerSaldoCliente(@PathVariable Long id,
            @RequestParam(defaultValue = "/admin/dashboard") String origen, RedirectAttributes redirectAttributes) {
        try {
            bancaService.cambiarEstadoCuenta(id, "RETENIDA");
            redirectAttributes.addAttribute("exito", "Saldo retenido");
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:" + validarOrigen(origen);
    }

    @PostMapping("/clientes/{id}/liberar-saldo")
    public String liberarSaldoCliente(@PathVariable Long id,
            @RequestParam(defaultValue = "/admin/dashboard") String origen, RedirectAttributes redirectAttributes) {
        try {
            bancaService.cambiarEstadoCuenta(id, "ACTIVA");
            redirectAttributes.addAttribute("exito", "Saldo liberado");
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:" + validarOrigen(origen);
    }

    // Solo permite volver a las paginas que realmente muestran la cartera de
    // clientes, para no exponer un open-redirect a traves del campo "origen"
    private String validarOrigen(String origen) {
        return "/admin/registro-admin".equals(origen) ? origen : "/admin/dashboard";
    }

    @PostMapping("/solicitudes/{id}/aprobar")
    public String aprobarSolicitud(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bancaService.aprobarSolicitudCredito(id);
            redirectAttributes.addAttribute("exito", "Solicitud aprobada y crédito abonado");
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/solicitudes/{id}/rechazar")
    public String rechazarSolicitud(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bancaService.rechazarSolicitudCredito(id);
            redirectAttributes.addAttribute("exito", "Solicitud rechazada");
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    // Panel Ejecutivo: resumen general (clientes registrados y solicitudes de credito)
    @GetMapping("/panel-ejecutivo")
    public String mostrarPanelEjecutivo(Model modelo) {

        List<usuarioEntity> clientes = usuarioRepository.findAll().stream().filter(u -> "CLIENTE".equals(u.getRol()))
                .collect(Collectors.toList());

        List<SolicitudCreditoEntity> solicitudes = solicitudCreditoRepository.findAllByOrderByFechaDesc();

        modelo.addAttribute("clientes", clientes);
        modelo.addAttribute("solicitudes", solicitudes);

        return "panel-ejecutivo";
    }

    // Registrar Cliente: pagina dedicada para registrar clientes y ver la cartera
    @GetMapping("/registro-admin")
    public String mostrarRegistroAdmin(Model modelo) {

        List<usuarioEntity> clientes = usuarioRepository.findAll().stream().filter(u -> "CLIENTE".equals(u.getRol()))
                .collect(Collectors.toList());

        modelo.addAttribute("clientes", clientes);

        return "registro-admin";
    }

    // Reporte formal en PDF de la cartera de clientes junto con su historial
    // de movimientos, usado desde el Panel Ejecutivo.
    @GetMapping("/reporte-clientes")
    public ResponseEntity<byte[]> descargarReporteClientes() {
        List<usuarioEntity> clientes = usuarioRepository.findAll().stream().filter(u -> "CLIENTE".equals(u.getRol()))
                .collect(Collectors.toList());

        Map<Long, List<movimientosEntity>> historialPorCliente = new HashMap<>();
        for (usuarioEntity cliente : clientes) {
            if (cliente.getCuentas() != null && !cliente.getCuentas().isEmpty()) {
                String clabe = cliente.getCuentas().get(0).getClabe();
                historialPorCliente.put(cliente.getId(),
                        movimientoCuentaRepository.findByCuentaOrigenOrCuentaDestinoOrderByFechaDescIdDesc(clabe,
                                clabe));
            }
        }

        byte[] pdf = pdfService.generarReporteClientes(clientes, historialPorCliente);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename("reporte-clientes.pdf").build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @PostMapping("/registro-admin/crear-cliente")
    public String crearClienteDesdeRegistroAdmin(@RequestParam String nombre, @RequestParam String username,
            @RequestParam String password,
            @RequestParam Double saldoInicial,
            RedirectAttributes redirectAttributes) {

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty() ||
                nombre == null || nombre.trim().isEmpty()) {
            redirectAttributes.addAttribute("error", "Debes llenar todos los campos");
            return "redirect:/admin/registro-admin";

        }
        if (saldoInicial == null || saldoInicial <= 0) {

            redirectAttributes.addAttribute("error", "El monto de apertura debe ser mayor a 0");
            return "redirect:/admin/registro-admin";

        }
        try {

            bancaService.crearClienteConCuenta(nombre, username, password, saldoInicial);
            redirectAttributes.addAttribute("exito", "Cliente creado exitosamente");
            return "redirect:/admin/registro-admin";

        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
            return "redirect:/admin/registro-admin";

        }

    }

    // Panel Administrador: pagina dedicada para la auditoria de creditos
    @GetMapping("/credito-admin")
    public String mostrarCreditoAdmin(Model modelo) {

        List<SolicitudCreditoEntity> solicitudes = solicitudCreditoRepository.findAllByOrderByFechaDesc();

        modelo.addAttribute("solicitudes", solicitudes);

        return "credito-admin";
    }

    @PostMapping("/credito-admin/solicitudes/{id}/aprobar")
    public String aprobarSolicitudDesdeCreditoAdmin(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bancaService.aprobarSolicitudCredito(id);
            redirectAttributes.addAttribute("exito", "Solicitud aprobada y crédito abonado");
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin/credito-admin";
    }

    @PostMapping("/credito-admin/solicitudes/{id}/rechazar")
    public String rechazarSolicitudDesdeCreditoAdmin(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            bancaService.rechazarSolicitudCredito(id);
            redirectAttributes.addAttribute("exito", "Solicitud rechazada");
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin/credito-admin";
    }

    @PostMapping("/credito-admin/solicitudes/{id}/abonar")
    public String registrarAbonoCredito(@PathVariable Long id, @RequestParam Double montoAbono,
            RedirectAttributes redirectAttributes) {
        try {
            bancaService.registrarAbonoCredito(id, montoAbono);
            redirectAttributes.addAttribute("exito", "Abono registrado con éxito");
        } catch (Exception e) {
            redirectAttributes.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin/credito-admin";
    }

}
