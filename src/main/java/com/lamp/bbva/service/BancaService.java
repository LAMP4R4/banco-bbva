package com.lamp.bbva.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lamp.bbva.entity.SolicitudCreditoEntity;
import com.lamp.bbva.entity.cuentaEntity;
import com.lamp.bbva.entity.movimientosEntity;
import com.lamp.bbva.entity.usuarioEntity;
import com.lamp.bbva.repository.cuentaRepository;
import com.lamp.bbva.repository.movimientoCuentaRepository;
import com.lamp.bbva.repository.solicitudCreditoRepository;
import com.lamp.bbva.repository.usuarioRepository;

@Service

public class BancaService {

    private final usuarioRepository usuarioRepository;
    private final cuentaRepository cuentaRepository;
    private final movimientoCuentaRepository movimientoRepository;
    private final solicitudCreditoRepository solicitudCreditoRepository;
    private final PasswordEncoder passwordEncoder;

    public BancaService(usuarioRepository usuarioRepository,
            cuentaRepository cuentaRepository,
            movimientoCuentaRepository movimientoRepository,
            solicitudCreditoRepository solicitudCreditoRepository,
            @Lazy PasswordEncoder passwordEncoder) { // Se usa @Lazy para evitar dependencias circulares con
                                                     // SecurityConfig
        this.usuarioRepository = usuarioRepository;
        this.cuentaRepository = cuentaRepository;
        this.movimientoRepository = movimientoRepository;
        this.solicitudCreditoRepository = solicitudCreditoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Transferencia entre CLABEs con garantía transaccional (ACID)
    @Transactional(rollbackFor = Exception.class)
    public void transferirMonto(String clabeOrigen, String clabeDestino, Double monto, String descripcion) {
        if (monto <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero.");
        }
        if (clabeOrigen.equals(clabeDestino)) {
            throw new IllegalArgumentException("La cuenta de destino no puede ser la misma que la de origen.");
        }

        cuentaEntity origen = cuentaRepository.findByClabe(clabeOrigen)
                .orElseThrow(() -> new RuntimeException("La cuenta de origen no existe."));

        cuentaEntity destino = cuentaRepository.findByClabe(clabeDestino)
                .orElseThrow(() -> new RuntimeException("La cuenta de destino no existe."));

        // Una cuenta retenida o deshabilitada no puede enviar transferencias
        if ("RETENIDA".equals(origen.getEstado()) || "DESHABILITADA".equals(origen.getEstado())) {
            throw new RuntimeException("Tu cuenta está " + origen.getEstado().toLowerCase()
                    + " y no puede realizar transferencias.");
        }

        // Una cuenta retenida o deshabilitada tampoco puede recibir transferencias
        if ("RETENIDA".equals(destino.getEstado()) || "DESHABILITADA".equals(destino.getEstado())) {
            throw new RuntimeException("La cuenta destino está " + destino.getEstado().toLowerCase()
                    + " y no puede recibir transferencias.");
        }

        // Validar fondos del remitente
        if (origen.getSaldo() < monto) {
            throw new FondosInsuficientesException("No cuentas con saldo suficiente para esta operación.");
        }

        // 1. CARGO a la cuenta origen
        origen.setSaldo(origen.getSaldo() - monto);
        cuentaRepository.save(origen);

        // --- PUNTO DE FALLO POTENCIAL ---
        // Si ocurriera un error en este punto (por ejemplo, desconexión de DB),
        // @Transactional deshará el cargo de la cuenta de origen.

        // 2. ABONO a la cuenta destino
        destino.setSaldo(destino.getSaldo() + monto);
        cuentaRepository.save(destino);

        // 3. Registrar el Movimiento
        movimientosEntity movimiento = new movimientosEntity();
        movimiento.setCuentaOrigen(clabeOrigen);
        movimiento.setCuentaDestino(clabeDestino);
        movimiento.setMonto(monto);
        movimiento.setDescripcion(descripcion);
        movimiento.setTipo("Transferencia");
        movimiento.setEstadoMovimiento("AUTORIZADO");
        movimiento.setFecha(LocalDate.now());
        movimientoRepository.save(movimiento);
    }

    // Inicia una transferencia usando el usuario autenticado (Auditoría Activa)
    @Transactional(rollbackFor = Exception.class)
    public void transferirDesdeUsuario(String username, String clabeDestino, Double monto, String descripcion) {
        usuarioEntity usuario = usuarioRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        if (usuario.getCuentas() == null || usuario.getCuentas().isEmpty()) {
            throw new RuntimeException("El usuario no tiene una cuenta bancaria asignada.");
        }

        // Usamos la cuenta principal (la primera asignada) del usuario autenticado
        String clabeOrigen = usuario.getCuentas().get(0).getClabe();

        transferirMonto(clabeOrigen, clabeDestino, monto, descripcion);
    }

    // Registra un cliente y le asigna una CLABE aleatoria única de 18 dígitos
    @Transactional(rollbackFor = Exception.class)
    public usuarioEntity crearClienteConCuenta(String nombre, String username, String password, Double saldoInicial) {
        if (usuarioRepository.findByUserName(username).isPresent()) {
            throw new RuntimeException("El nombre de usuario ya está registrado.");
        }
        if (usuarioRepository.findByNombre(nombre).isPresent()) {
            throw new RuntimeException("El nombre de cliente ya existe.");
        }

        usuarioEntity usuario = new usuarioEntity();

        usuario.setUserName(username);
        usuario.setNombre(nombre);
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setRol("CLIENTE");
        usuarioEntity usuarioGuardado = usuarioRepository.save(usuario);

        // Generar CLABE única de 18 dígitos
        String clabe = generarClabeUnica();

        cuentaEntity cuenta = new cuentaEntity();
        cuenta.setClabe(clabe);
        cuenta.setSaldo(saldoInicial);
        cuenta.setUsuario(usuarioGuardado);
        cuenta.setEstado("ACTIVA");
        cuentaRepository.save(cuenta);

        List<cuentaEntity> list = new ArrayList<>();
        list.add(cuenta);
        usuarioGuardado.setCuentas(list);

        return usuarioGuardado;
    }

    // La firma crea una solicitud; el abono se realiza hasta que un ejecutivo la
    // aprueba.
    @Transactional(rollbackFor = Exception.class)
    public SolicitudCreditoEntity guardarSolicitudCredito(String username, Double monto, String firmaBase64) {
        usuarioEntity usuario = usuarioRepository.findByUserName(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        if (!esCliente(usuario)) {
            throw new RuntimeException("Solo los clientes pueden solicitar créditos.");
        }

        if (usuario.getCuentas() != null && !usuario.getCuentas().isEmpty()) {
            String estadoCuenta = usuario.getCuentas().get(0).getEstado();
            if ("RETENIDA".equals(estadoCuenta) || "DESHABILITADA".equals(estadoCuenta)) {
                throw new RuntimeException("Tu cuenta está " + estadoCuenta.toLowerCase()
                        + " y no puede solicitar créditos.");
            }
        }

        SolicitudCreditoEntity solicitud = new SolicitudCreditoEntity();
        solicitud.setUsuario(usuario);
        solicitud.setMontoSolicitado(monto);
        solicitud.setFirmaBase64(firmaBase64);
        solicitud.setEstado("PENDIENTE");
        solicitud.setFecha(LocalDateTime.now());
        return solicitudCreditoRepository.save(solicitud);
    }

    @Transactional(rollbackFor = Exception.class)
    public SolicitudCreditoEntity aprobarSolicitudCredito(Long solicitudId) {
        SolicitudCreditoEntity solicitud = solicitudCreditoRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("La solicitud de crédito no existe."));

        if (!"PENDIENTE".equals(solicitud.getEstado())) {
            throw new RuntimeException("Esta solicitud ya fue procesada.");
        }

        usuarioEntity usuario = solicitud.getUsuario();

        // Los clientes antiguos que no tengan cuenta reciben una cuenta con $0
        // antes de acreditarles el monto aprobado.
        cuentaEntity cuenta = cuentaRepository.findByUsuario(usuario).stream()
                .findFirst()
                .orElseGet(() -> crearCuentaParaUsuario(usuario));

        // ABONAR el monto al saldo
        cuenta.setSaldo(cuenta.getSaldo() + solicitud.getMontoSolicitado());
        cuentaRepository.save(cuenta);

        // REGISTRAR MOVIMIENTO
        movimientosEntity movimiento = new movimientosEntity();
        movimiento.setCuentaOrigen("CRÉDITO-BANCO");
        movimiento.setCuentaDestino(cuenta.getClabe());
        movimiento.setMonto(solicitud.getMontoSolicitado());
        movimiento.setEstadoMovimiento("AUTORIZADO");
        movimiento.setTipo("Credito");
        movimiento.setDescripcion("Abono de crédito aprobado - Solicitud #" + solicitudId);
        movimiento.setFecha(LocalDate.now());
        movimientoRepository.save(movimiento);

        // ACTUALIZAR ESTADO DEL CRÉDITO
        solicitud.setEstado("APROBADO");
        solicitud.setMontoPendiente(solicitud.getMontoSolicitado());
        return solicitudCreditoRepository.save(solicitud);
    }

    // Registra un abono del cliente hacia un credito ya aprobado: se descuenta
    // de su cuenta (igual que se le acredito) y reduce el saldo pendiente hasta
    // marcar el credito como PAGADO al llegar a $0.
    @Transactional(rollbackFor = Exception.class)
    public SolicitudCreditoEntity registrarAbonoCredito(Long solicitudId, Double montoAbono) {
        if (montoAbono == null || montoAbono <= 0) {
            throw new IllegalArgumentException("El monto del abono debe ser mayor a cero.");
        }

        SolicitudCreditoEntity solicitud = solicitudCreditoRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("La solicitud de crédito no existe."));

        if (!"APROBADO".equals(solicitud.getEstado())) {
            throw new RuntimeException("Solo se pueden registrar abonos a créditos aprobados.");
        }

        // Compatibilidad con creditos aprobados antes de existir este saldo
        Double pendiente = solicitud.getMontoPendiente() != null
                ? solicitud.getMontoPendiente()
                : solicitud.getMontoSolicitado();

        if (montoAbono > pendiente) {
            throw new RuntimeException("El abono no puede ser mayor al saldo pendiente ($"
                    + String.format("%.2f", pendiente) + ").");
        }

        usuarioEntity usuario = solicitud.getUsuario();
        cuentaEntity cuenta = cuentaRepository.findByUsuario(usuario).stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("El cliente no tiene una cuenta bancaria asignada."));

        if (cuenta.getSaldo() < montoAbono) {
            throw new FondosInsuficientesException("El cliente no cuenta con saldo suficiente para este abono.");
        }

        // CARGO a la cuenta del cliente
        cuenta.setSaldo(cuenta.getSaldo() - montoAbono);
        cuentaRepository.save(cuenta);

        // REGISTRAR MOVIMIENTO
        movimientosEntity movimiento = new movimientosEntity();
        movimiento.setCuentaOrigen(cuenta.getClabe());
        movimiento.setCuentaDestino("CRÉDITO-BANCO");
        movimiento.setMonto(montoAbono);
        movimiento.setEstadoMovimiento("AUTORIZADO");
        movimiento.setTipo("Pago Credito");
        movimiento.setDescripcion("Abono a crédito - Solicitud #" + solicitudId);
        movimiento.setFecha(LocalDate.now());
        movimientoRepository.save(movimiento);

        // ACTUALIZAR SALDO PENDIENTE DEL CRÉDITO
        double nuevoPendiente = pendiente - montoAbono;
        if (nuevoPendiente <= 0.001) {
            nuevoPendiente = 0.0;
            solicitud.setEstado("PAGADO");
        }
        solicitud.setMontoPendiente(nuevoPendiente);

        return solicitudCreditoRepository.save(solicitud);
    }

    @Transactional(rollbackFor = Exception.class)
    public SolicitudCreditoEntity rechazarSolicitudCredito(Long solicitudId) {
        SolicitudCreditoEntity solicitud = solicitudCreditoRepository.findById(solicitudId)
                .orElseThrow(() -> new RuntimeException("La solicitud de crédito no existe."));

        if (!"PENDIENTE".equals(solicitud.getEstado())) {
            throw new RuntimeException("Esta solicitud ya fue procesada.");
        }

        solicitud.setEstado("RECHAZADO");
        return solicitudCreditoRepository.save(solicitud);
    }

    private cuentaEntity crearCuentaParaUsuario(usuarioEntity usuario) {
        cuentaEntity cuenta = new cuentaEntity();
        cuenta.setClabe(generarClabeUnica());
        cuenta.setSaldo(0.0);
        cuenta.setUsuario(usuario);
        cuenta.setEstado("ACTIVA");
        cuentaEntity cuentaGuardada = cuentaRepository.save(cuenta);

        if (usuario.getCuentas() == null) {
            usuario.setCuentas(new ArrayList<>());
        }
        usuario.getCuentas().add(cuentaGuardada);
        usuarioRepository.save(usuario);

        return cuentaGuardada;
    }

    // Modifica nombre y usuario de un cliente ya registrado
    @Transactional(rollbackFor = Exception.class)
    public usuarioEntity actualizarDatosCliente(Long clienteId, String nombre, String username) {
        usuarioEntity usuario = usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado."));

        usuarioRepository.findByUserName(username)
                .filter(u -> !u.getId().equals(clienteId))
                .ifPresent(u -> {
                    throw new RuntimeException("El nombre de usuario ya está en uso.");
                });

        usuario.setNombre(nombre);
        usuario.setUserName(username);
        return usuarioRepository.save(usuario);
    }

    // El propio cliente actualiza su username, telefono, correo y
    // opcionalmente su contraseña desde "Mi Perfil" (la contraseña solo se
    // toca si viene no vacía)
    @Transactional(rollbackFor = Exception.class)
    public usuarioEntity actualizarPerfilPropio(String usernameActual, String nuevoUsername, String telefono,
            String correo, String nuevaPassword) {
        usuarioEntity usuario = usuarioRepository.findByUserName(usernameActual)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado."));

        usuarioRepository.findByUserName(nuevoUsername)
                .filter(u -> !u.getId().equals(usuario.getId()))
                .ifPresent(u -> {
                    throw new RuntimeException("El nombre de usuario ya está en uso.");
                });

        usuario.setUserName(nuevoUsername);
        usuario.setTelefono(telefono);
        usuario.setCorreo(correo);

        if (nuevaPassword != null && !nuevaPassword.isBlank()) {
            usuario.setPassword(passwordEncoder.encode(nuevaPassword));
        }

        return usuarioRepository.save(usuario);
    }

    // Cambia el estado de la cuenta principal de un cliente (ACTIVA,
    // DESHABILITADA, RETENIDA)
    @Transactional(rollbackFor = Exception.class)
    public void cambiarEstadoCuenta(Long clienteId, String nuevoEstado) {
        usuarioEntity usuario = usuarioRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado."));

        if (usuario.getCuentas() == null || usuario.getCuentas().isEmpty()) {
            throw new RuntimeException("El cliente no tiene una cuenta asignada.");
        }

        cuentaEntity cuenta = usuario.getCuentas().get(0);
        cuenta.setEstado(nuevoEstado);
        cuentaRepository.save(cuenta);
    }

    private boolean esCliente(usuarioEntity usuario) {
        return "CLIENTE".equals(usuario.getRol());
    }

    private String generarClabeUnica() {
        Random random = new Random();
        String clabe;
        do {
            // Estructura: 012 (código banco simulado) + 15 dígitos aleatorios
            StringBuilder sb = new StringBuilder("012");
            for (int i = 0; i < 15; i++) {
                sb.append(random.nextInt(10));
            }
            clabe = sb.toString();
        } while (cuentaRepository.findByClabe(clabe).isPresent());
        return clabe;
    }

    // metodo para buscar los movimientos
    public List<movimientosEntity> todosMovimientos() {
        return movimientoRepository.findAllByOrderByFechaDescIdDesc();
    }

    // OBTENERE MOVIMIENTOS POR ID
    public movimientosEntity obtenerMovimientoId(Long id) {
        return movimientoRepository.findById(id).orElse(null);

    }

    // metodo para modificar estado del movimiento
    @Transactional(rollbackFor = Exception.class)
    public void actualizarMovimiento(Long id, String nuevoEstado) {
        movimientosEntity movimiento = obtenerMovimientoId(id);
        if (movimiento == null) {
            return;
        }

        // Al cancelar un movimiento se revierte el cargo/abono en ambas cunetas
        if ("CANCELADO".equals(nuevoEstado) && !"CANCELADO".equals(movimiento.getEstadoMovimiento())) {
            revertirMovimiento(movimiento);
        }

        movimiento.setEstadoMovimiento(nuevoEstado);
        movimientoRepository.save(movimiento);
    }

    // Deshace el efecto en saldos de un movimiento (transferencia o deposito)
    // alccancelarlo
    private void revertirMovimiento(movimientosEntity movimiento) {
        cuentaRepository.findByClabe(movimiento.getCuentaDestino()).ifPresent(destino -> {
            if (destino.getSaldo() < movimiento.getMonto()) {
                throw new FondosInsuficientesException(
                        "No se puede cancelar: el destinatario ya no cuenta con fondos suficientes.");
            }
            destino.setSaldo(destino.getSaldo() - movimiento.getMonto());
            cuentaRepository.save(destino);
        });

        // La cuenta origen no existe como cuenta real para los depositos
        cuentaRepository.findByClabe(movimiento.getCuentaOrigen()).ifPresent(origen -> {
            origen.setSaldo(origen.getSaldo() + movimiento.getMonto());
            cuentaRepository.save(origen);
        });
    }

    // eliminar movimiento
    public void eliminarMovimiento(Long id) {
        movimientoRepository.deleteById(id);
    }
}
