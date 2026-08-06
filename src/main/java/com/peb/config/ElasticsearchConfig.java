package com.peb.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

/**
 * Elasticsearch-Verbindungskonfiguration. Liest Host/Port/Credentials
 * aus application.properties (peb.elasticsearch.*), damit nichts
 * hartcodiert ist und pro Umgebung (dev/test/prod) unterschiedliche
 * ES-Cluster angesprochen werden können.
 *
 * HINWEIS: KEIN @EnableElasticsearchRepositories mehr (bewusst entfernt).
 * Spring Data Repository-Beans werden vom Framework eager validiert
 * (Fail-Fast bei Query-Ableitungsfehlern) - SELBST wenn
 * spring.main.lazy-initialization=true gesetzt ist. Das hätte den
 * App-Start weiterhin an die ES-Erreichbarkeit gekoppelt.
 * SearchIndexService nutzt daher direkt ElasticsearchOperations statt
 * eines Repository-Interfaces (siehe SearchIndexService-Klassenkommentar).
 *
 * @Lazy auf Klassenebene: markiert die (von der Basisklasse geerbten)
 * @Bean-Methoden (u.a. elasticsearchClient()) als lazy-init - zusätzliche
 * Absicherung neben der globalen Property, für den Fall, dass diese Config
 * direkt (ohne Umweg über ElasticsearchOperations) referenziert wird.
 */
@Lazy
@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${peb.elasticsearch.host}:${peb.elasticsearch.port}")
    private String hostAndPort;

    @Value("${peb.elasticsearch.username:}")
    private String username;

    @Value("${peb.elasticsearch.password:}")
    private String password;

    @Override
    public ClientConfiguration clientConfiguration() {
        ClientConfiguration.MaybeSecureClientConfigurationBuilder builder =
                ClientConfiguration.builder().connectedTo(hostAndPort);

        if (!username.isBlank()) {
            return builder.withBasicAuth(username, password).build();
        }
        return builder.build();
    }
}
