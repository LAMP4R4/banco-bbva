package com.lamp.bbva.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.lamp.bbva.entity.cuentaEntity;
import com.lamp.bbva.entity.movimientosEntity;
import com.lamp.bbva.entity.usuarioEntity;
import com.lamp.bbva.repository.movimientoCuentaRepository;
import com.lamp.bbva.repository.usuarioRepository;
import com.lamp.bbva.service.BancaService;
import com.lamp.bbva.service.PdfService;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class DashBoardController {

    private final usuarioRepository usuarioRepository;
    private final movimientoCuentaRepository movimientoCuentaRepository;
    private final BancaService bancaService;
    private final PdfService pdfService;

    public DashBoardController(usuarioRepository usuario, movimientoCuentaRepository movimientoCuenta,
            BancaService bancaService, PdfService pdfService) {
        this.usuarioRepository = usuario;
        this.movimientoCuentaRepository = movimientoCuenta;
        this.bancaService = bancaService;
        this.pdfService = pdfService;
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
        String estadoCuenta = "ACTIVA";
        List<movimientosEntity> ultimosMovimientos = new ArrayList<>();

        if (usuario.getCuentas() != null && !usuario.getCuentas().isEmpty()) {
            cuentaEntity cuentaPrincipal = usuario.getCuentas().get(0);
            clabe = cuentaPrincipal.getClabe();
            saldo = cuentaPrincipal.getSaldo();
            estadoCuenta = cuentaPrincipal.getEstado();
            ultimosMovimientos = movimientoCuentaRepository
                    .findByCuentaOrigenOrCuentaDestinoOrderByFechaDescIdDesc(clabe, clabe);

        }

        // inyectar los datos al modelo de thymeleaf
        modelo.addAttribute("nombreCliente", usuario.getNombre());
        modelo.addAttribute("saldoTotal", saldo);
        modelo.addAttribute("cuentaClabe", clabe);
        modelo.addAttribute("cuentaEstado", estadoCuenta);
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

    // Comprobante formal de un movimiento. Un cliente solo puede ver el de
    // movimientos donde participa alguna de sus propias cuentas; un ejecutivo
    // puede ver el de cualquier movimiento.
    @GetMapping("/movimientos/{id}/comprobante")
    public ResponseEntity<byte[]> descargarComprobante(@PathVariable Long id, Authentication auth) {
        if (auth == null) {
            throw new RuntimeException("No autenticado");
        }

        movimientosEntity movimiento = bancaService.obtenerMovimientoId(id);
        if (movimiento == null) {
            throw new RuntimeException("Movimiento no encontrado");
        }

        usuarioEntity usuario = usuarioRepository.findByUserName(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        boolean esDueno = usuario.getCuentas() != null && usuario.getCuentas().stream()
                .anyMatch(c -> c.getClabe().equals(movimiento.getCuentaOrigen())
                        || c.getClabe().equals(movimiento.getCuentaDestino()));

        if (!"EJECUTIVO".equals(usuario.getRol()) && !esDueno) {
            throw new RuntimeException("No tienes permiso para ver este comprobante");
        }

        byte[] pdf = pdfService.generarComprobanteMovimiento(movimiento);
        return respuestaPdf(pdf, "comprobante-movimiento-" + id + ".pdf");
    }

    // Historial de gastos y ganancias del cliente autenticado (nunca de otro
    // usuario: la clabe siempre sale de su propia cuenta).
    @GetMapping("/perfil/historial-pdf")
    public ResponseEntity<byte[]> descargarHistorial(Authentication auth) {
        if (auth == null) {
            throw new RuntimeException("No autenticado");
        }

        usuarioEntity usuario = usuarioRepository.findByUserName(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        List<movimientosEntity> movimientos = new ArrayList<>();
        String clabe = "";
        if (usuario.getCuentas() != null && !usuario.getCuentas().isEmpty()) {
            clabe = usuario.getCuentas().get(0).getClabe();
            movimientos = movimientoCuentaRepository.findByCuentaOrigenOrCuentaDestinoOrderByFechaDescIdDesc(clabe,
                    clabe);
        }

        byte[] pdf = pdfService.generarHistorialCliente(usuario, clabe, movimientos);
        return respuestaPdf(pdf, "historial-" + usuario.getUserName() + ".pdf");
    }

    // Content-Disposition "inline" para que el navegador muestre la
    // previsualizacion del PDF en una pestaña antes de que el usuario decida
    // descargarlo.
    private ResponseEntity<byte[]> respuestaPdf(byte[] pdf, String nombreArchivo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename(nombreArchivo).build());
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

}
