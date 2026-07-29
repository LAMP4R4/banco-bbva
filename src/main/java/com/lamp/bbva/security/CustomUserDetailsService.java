package com.lamp.bbva.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.lamp.bbva.entity.cuentaEntity;
import com.lamp.bbva.entity.usuarioEntity;
import com.lamp.bbva.repository.usuarioRepository;

@Service

public class CustomUserDetailsService implements UserDetailsService {

    private final usuarioRepository usuarioRepository;

    public CustomUserDetailsService(usuarioRepository usuario) {
        this.usuarioRepository = usuario;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        usuarioEntity usuarioLogin = usuarioRepository.findByUserName(username)
                .orElseThrow(() -> new UsernameNotFoundException("No se encontro el cliente"));

        // Un cliente con la cuenta principal DESHABILITADA no puede iniciar sesion
        boolean habilitado = true;
        if (usuarioLogin.getCuentas() != null && !usuarioLogin.getCuentas().isEmpty()) {
            cuentaEntity cuentaPrincipal = usuarioLogin.getCuentas().get(0);
            habilitado = !"DESHABILITADA".equals(cuentaPrincipal.getEstado());
        }

        // aqui se usa el builde del spring securrity
        // se agrega automaticamente el rol del usuario
        return User.builder()
                .username(usuarioLogin.getUserName())
                .password(usuarioLogin.getPassword())
                .roles(usuarioLogin.getRol())
                .disabled(!habilitado)
                .build();

    }
}
