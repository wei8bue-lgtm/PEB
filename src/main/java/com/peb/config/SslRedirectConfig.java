package com.peb.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SSL-Konfiguration mit automatischem HTTP → HTTPS Redirect
 * HTTP-Anfragen auf Port 8100 werden auf HTTPS Port 8443 umgeleitet
 */
@Configuration
public class SslRedirectConfig {

    @Value("${server.http.port:8100}")
    private int httpPort;

    @Value("${server.port:8444}")
    private int httpsPort;

    /**
     * Registriert einen zusätzlichen HTTP-Connector, der alle
     * eingehenden HTTP-Requests auf HTTPS umleitet.
     */
    @Bean
    public ServletWebServerFactory servletContainer() {
        TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory() {
            @Override
            protected void postProcessContext(
                    org.apache.catalina.Context context) {

                // Erzwingt HTTPS für alle Ressourcen
                var securityConstraint =
                    new org.apache.tomcat.util.descriptor.web.SecurityConstraint();
                securityConstraint.setUserConstraint("CONFIDENTIAL");

                var collection =
                    new org.apache.tomcat.util.descriptor.web.SecurityCollection();
                collection.addPattern("/*");  // Alle Pfade absichern

                securityConstraint.addCollection(collection);
                context.addConstraint(securityConstraint);
            }
        };

        // HTTP-Connector hinzufügen
        tomcat.addAdditionalTomcatConnectors(createHttpConnector());
        return tomcat;
    }

    /**
     * Erstellt den HTTP-Connector mit Redirect-Konfiguration
     */
    private Connector createHttpConnector() {
        var connector = new Connector(
            TomcatServletWebServerFactory.DEFAULT_PROTOCOL
        );
        connector.setScheme("http");
        connector.setPort(httpPort);
        connector.setSecure(false);
        connector.setRedirectPort(httpsPort); // Redirect-Ziel
        return connector;
    }
}