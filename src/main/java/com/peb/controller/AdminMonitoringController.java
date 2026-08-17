package com.peb.controller;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class AdminMonitoringController {

    private final MeterRegistry meterRegistry;
    private final DataSource dataSource;

    public AdminMonitoringController(MeterRegistry meterRegistry, DataSource dataSource) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
    }

    @GetMapping("/admin/monitoring")
    public String monitoringPage() {
        return "admin/monitoring";
    }

    @GetMapping("/api/admin/monitoring/snapshot")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> snapshot() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", Instant.now().toEpochMilli());

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("processCpuPercent", round(safePercent(readGauge("process.cpu.usage"))));
        metrics.put("systemCpuPercent", round(safePercent(readGauge("system.cpu.usage"))));
        metrics.put("heapUsedMb", round(bytesToMb(readGauge("jvm.memory.used", "area", "heap"))));
        metrics.put("heapMaxMb", round(bytesToMb(readGauge("jvm.memory.max", "area", "heap"))));
        metrics.put("activeHttpSessions", round(readGauge("tomcat.sessions.active.current")));
        payload.put("metrics", metrics);

        payload.put("connections", resolveConnectionStats());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/api/admin/monitoring/connections/active")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> activeConnections() {
        return ResponseEntity.ok(resolveConnectionStats());
    }

    private Map<String, Object> resolveConnectionStats() {
        Map<String, Object> connections = new LinkedHashMap<>();

        if (dataSource instanceof HikariDataSource hikariDataSource) {
            HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
            if (pool != null) {
                connections.put("active", pool.getActiveConnections());
                connections.put("idle", pool.getIdleConnections());
                connections.put("total", pool.getTotalConnections());
                connections.put("awaiting", pool.getThreadsAwaitingConnection());
                return connections;
            }
        }

        connections.put("active", null);
        connections.put("idle", null);
        connections.put("total", null);
        connections.put("awaiting", null);
        connections.put("message", "Kein Hikari-Pool verfügbar");
        return connections;
    }

    private double readGauge(String meterName, String... tags) {
        Gauge gauge = meterRegistry.find(meterName).tags(tags).gauge();
        if (gauge == null) {
            return Double.NaN;
        }
        return gauge.value();
    }

    private double safePercent(double value) {
        if (Double.isNaN(value)) {
            return Double.NaN;
        }
        return value * 100.0;
    }

    private double bytesToMb(double value) {
        if (Double.isNaN(value)) {
            return Double.NaN;
        }
        return value / (1024.0 * 1024.0);
    }

    private Double round(double value) {
        if (Double.isNaN(value)) {
            return null;
        }
        return Math.round(value * 100.0) / 100.0;
    }
}
