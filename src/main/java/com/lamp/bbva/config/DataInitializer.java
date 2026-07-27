package com.lamp.bbva.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.lamp.bbva.entity.usuarioEntity;
import com.lamp.bbva.repository.usuarioRepository;

//LA UNICA FUNCION ES CREAR UN USUSARIO EJECUTIVO Y UN USUSARIO CLIENTE
@Component

public class DataInitializer implements CommandLineRunner {
    private final usuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(usuarioRepository usuario, PasswordEncoder pass) {
        this.usuarioRepository = usuario;
        this.passwordEncoder = pass;
    }

    @Override
    public void run(String... args) throws Exception {

        if (usuarioRepository.count() == 0) {
            // Crear un usuario ejecutivo
            System.out.println("Agregando Prueba usuario Ejecutivo...");
            // 1 Creando un usuario ejecutivo
            usuarioEntity ejecutivo = new usuarioEntity();
            ejecutivo.setUserName("LAMP");
            ejecutivo.setNombre("Lorenzo Antonio Marin Parra");
            ejecutivo.setPassword(passwordEncoder.encode("12345678"));
            ejecutivo.setRol("EJECUTIVO");
            usuarioRepository.save(ejecutivo);
            System.out.println("Usuario Ejecutivo agregado: LAMP-12345678");

            // 2 Creando un usuario cliente
            usuarioEntity cliente = new usuarioEntity();
            cliente.setUserName("Acapulco");
            cliente.setNombre("Brando Acapulco Bedolla");
            cliente.setPassword(passwordEncoder.encode("1234"));
            cliente.setRol("CLIENTE");
            usuarioRepository.save(cliente);
            System.out.println("Usuario Cliente agregado: Acapulco-1234");

        }

    }
}