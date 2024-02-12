package com.cribl.logcollector;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Objects;

import static org.springframework.security.config.Customizer.withDefaults;


/**
 * This Spring security bean configures our security for the Cribl logger.
 * I'm using the out of box Spring Security configuration here which is HTTP Basic Auth.
 * Could wire in OAuth, SAML 2.0, Okta, LDAP or other security mechanisms here using different auth services in the
 * security filter chain. Springs security filter chain is a very similar mechanism to Apache's filter chains.
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Autowired
    private Environment envProps;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests((authorize) -> authorize
                        .anyRequest().authenticated()
                )
                .httpBasic(withDefaults())
                .formLogin(withDefaults());
        return http.build();
    }

    @Bean
    public InMemoryUserDetailsManager userDetailsService() {
        UserDetails user = User.withDefaultPasswordEncoder()
                .username(Objects.requireNonNull(envProps.getProperty("com.cribl.logcollector.ws.username")))
                .password(Objects.requireNonNull(envProps.getProperty("com.cribl.logcollector.ws.password")))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

}