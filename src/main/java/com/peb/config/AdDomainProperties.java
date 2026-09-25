package com.peb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "peb.ad")
public class AdDomainProperties {

    private String defaultDomain = "DE";
    private Map<String, Domain> domains = new LinkedHashMap<>();

    public String getDefaultDomain() {
        return defaultDomain;
    }

    public void setDefaultDomain(String defaultDomain) {
        this.defaultDomain = defaultDomain;
    }

    public Map<String, Domain> getDomains() {
        return domains;
    }

    public void setDomains(Map<String, Domain> domains) {
        this.domains = domains;
    }

    public static class Domain {
        private boolean enabled;
        private String domain;
        private String url;
        private String rootDn;
        private String searchFilter = "(&(objectClass=user)(sAMAccountName={0}))";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDomain() {
            return domain;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getRootDn() {
            return rootDn;
        }

        public void setRootDn(String rootDn) {
            this.rootDn = rootDn;
        }

        public String getSearchFilter() {
            return searchFilter;
        }

        public void setSearchFilter(String searchFilter) {
            this.searchFilter = searchFilter;
        }
    }
}