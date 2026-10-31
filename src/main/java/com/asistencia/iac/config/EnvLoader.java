package com.asistencia.iac.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cargador automático y transparente de archivos .env y variables del entorno.
 *
 * Permite ejecutar la infraestructura sin necesidad de escribir comandos
 * manuales como `pulumi config set`.
 *
 * Orden de resolución:
 *   1. Variable de entorno del sistema (System.getenv)
 *   2. Archivo .env local en el directorio de ejecución
 *   3. Archivo .env en directorios superiores o en asistencia-backend
 *   4. Valor por defecto inteligente
 */
public class EnvLoader {

    private static final Map<String, String> ENV_VARS = new HashMap<>();
    private static boolean initialized = false;

    private EnvLoader() { /* Clase utilitaria */ }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        List<Path> candidatePaths = List.of(
            Path.of(".env"),
            Path.of("../.env"),
            Path.of("../../.env"),
            Path.of("../../../asistencia-backend/.env"),
            Path.of("../../../../asistencia-backend/.env")
        );

        for (Path path : candidatePaths) {
            if (Files.isRegularFile(path)) {
                loadFromFile(path);
                break; // Cargar el primer .env encontrado
            }
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
     * luego en el archivo .env cargado.
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
