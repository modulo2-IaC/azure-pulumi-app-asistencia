package com.asistencia.iac.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Cargador colaborativo de configuración basado en YAML y SnakeYAML.
 *
 * Jerarquía de resolución:
 *   1. Variable de entorno del sistema (System.getenv), ideal para CI/CD (GitHub Actions).
 *   2. Archivo estructurado `config.yaml` en la raíz del proyecto (SnakeYAML).
 *   3. Valor por defecto inteligente.
 */
public class YamlConfigLoader {

    private static Map<String, Object> yamlData = Collections.emptyMap();
    private static boolean initialized = false;

    private YamlConfigLoader() { /* Clase utilitaria */ }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        Path path = Path.of("config.yaml");
        if (Files.isRegularFile(path)) {
            try (InputStream in = Files.newInputStream(path)) {
                Yaml yaml = new Yaml();
                Map<String, Object> loaded = yaml.load(in);
                if (loaded != null) {
                    yamlData = loaded;
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Resuelve un valor consultando primero System.getenv(envKey) y luego
     * la propiedad jerárquica en config.yaml (ej. "database.url").
     */
    public static Optional<String> get(String yamlPath, String envKey) {
        init();

        // 1. Variable de entorno del sistema (GitHub Actions Secrets o terminal)
        if (envKey != null && !envKey.isBlank()) {
            String sysVal = System.getenv(envKey);
            if (sysVal != null && !sysVal.isBlank()) {
                return Optional.of(sysVal.trim());
            }
        }

        // 2. Archivo config.yaml estructurado
        return getFromYaml(yamlPath);
    }

    public static String get(String yamlPath, String envKey, String defaultValue) {
        return get(yamlPath, envKey).orElse(defaultValue);
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> getFromYaml(String path) {
        if (yamlData.isEmpty() || path == null || path.isBlank()) {
            return Optional.empty();
        }

        String[] parts = path.split("\\.");
        Object current = yamlData;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return Optional.empty();
            }
        }

        if (current == null) {
            return Optional.empty();
        }

        String val = String.valueOf(current).trim();
        return val.isBlank() ? Optional.empty() : Optional.of(val);
    }
}
