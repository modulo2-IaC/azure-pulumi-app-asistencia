package com.asistencia.iac.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cargador portátil y colaborativo de archivos .env y variables de entorno del sistema.
 *
 * Facilita el trabajo colaborativo en equipo:
 *   - Cualquier desarrollador clona el repositorio, crea su archivo .env local y ejecuta
 *     pulumi sin necesidad de configurar manualmente decenas de comandos.
 *   - En entornos CI/CD (GitHub Actions) o contenedores, toma automáticamente las
 *     variables del sistema (System.getenv).
 *   - Es 100% independiente de rutas de máquinas locales particulares.
 */
public class EnvLoader {

    private static final Map<String, String> ENV_VARS = new HashMap<>();
    private static boolean initialized = false;

    private EnvLoader() { /* Clase utilitaria */ }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // Carga únicamente el archivo .env de la raíz del proyecto
        Path envPath = Path.of(".env");
        if (Files.isRegularFile(envPath)) {
            loadFromFile(envPath);
        }
    }

    private static void loadFromFile(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String val = line.substring(eqIdx + 1).trim();

                    // Eliminar comillas dobles o simples circundantes
                    if ((val.startsWith("\"") && val.endsWith("\"")) ||
                        (val.startsWith("'") && val.endsWith("'"))) {
                        if (val.length() >= 2) {
                            val = val.substring(1, val.length() - 1);
                        }
                    }
                    ENV_VARS.put(key, val);
                }
            }
        } catch (IOException ignored) {
        }
    }

    /**
     * Obtiene una variable buscando primero en variables de entorno del sistema,
     * luego en el archivo .env local.
     */
    public static Optional<String> get(String key) {
        init();
        // 1. Variable de entorno del sistema (ej. GitHub Actions secrets o terminal)
        String sysVal = System.getenv(key);
        if (sysVal != null && !sysVal.isBlank()) {
            return Optional.of(sysVal.trim());
        }

        // 2. Archivo .env local
        String envVal = ENV_VARS.get(key);
        if (envVal != null && !envVal.isBlank()) {
            return Optional.of(envVal.trim());
        }

        return Optional.empty();
    }

    public static String get(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }
}
