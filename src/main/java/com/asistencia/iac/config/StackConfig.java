package com.asistencia.iac.config;

import com.pulumi.Config;
import com.pulumi.Context;
import com.pulumi.core.Output;

import java.security.SecureRandom;

/**
 * Configuración centralizada y automatizada para el stack unificado de Azure Hardware.
 *
 * Soporta colaboración en equipo y CI/CD:
 *   1. Pulumi stack config (si existen)
 *   2. Archivo .env local o variables de entorno del sistema (System.getenv)
 *   3. Valores por defecto seguros y conformes a los estándares de Azure
 *
 * Utiliza autenticación exclusiva por contraseña para las Máquinas Virtuales (sin claves SSH).
 */
public class StackConfig {

    // ── Azure ─────────────────────────────────────────────────────────────────
    private final String location;
    private final String environment;
    private final String resourceGroupName;

    // ── VM Común (Autenticación exclusiva por contraseña) ──────────────────────
    private final String vmSize;
    private final String adminUsername;
    private final Output<String> vmPassword;

    // ── Backend (NestJS + PostgreSQL) ─────────────────────────────────────────
    private final Output<String> databaseUrl;
    private final Output<String> jwtSecret;
    private final String corsOrigin;
    private final String appTimezone;
    private final String appPort;
    private final String runMigrations;
    private final String adminRegistrationNo;
    private final Output<String> adminPassword;
    private final String adminFullName;
    private final String backendRepoUrl;

    // ── Frontend (React + Vite + Nginx) ───────────────────────────────────────
    private final String frontendRepoUrl;
    private final String viteApiUrl;

    // ── Común ─────────────────────────────────────────────────────────────────
    private final String gitBranch;
    private final String nodeVersion;

    public StackConfig(Context ctx) {
        Config azureConfig = ctx.config("azure-native");
        Config appConfig   = ctx.config("hardware");

        // Azure & Resource Group
        this.location = appConfig.get("location")
            .orElseGet(() -> azureConfig.get("location")
                .orElseGet(() -> EnvLoader.get("AZURE_LOCATION", "swedencentral")));

        this.environment = appConfig.get("environment")
            .orElseGet(() -> EnvLoader.get("ENVIRONMENT", "dev"));

        this.resourceGroupName = appConfig.get("resourceGroupName")
            .orElse("rg-asistencia-hardware-" + environment);

        // VM Specs & Password (Azure requiere: >= 12 chars, mayúscula, minúscula, número y símbolo)
        this.vmSize = appConfig.get("vmSize")
            .orElseGet(() -> EnvLoader.get("VM_SIZE", "Standard_B2as_v2"));

        this.adminUsername = appConfig.get("adminUsername")
            .orElseGet(() -> EnvLoader.get("ADMIN_USERNAME", "azureuser"));

        this.vmPassword = appConfig.getSecret("vmPassword")
            .applyValue(opt -> opt.orElseGet(() -> EnvLoader.get("VM_PASSWORD", "Asistencia2026!")));

        // Database URL: Pulumi Secret > .env
        this.databaseUrl = appConfig.getSecret("databaseUrl")
            .applyValue(opt -> opt.orElseGet(() ->
                EnvLoader.get("DATABASE_URL")
                    .orElse("postgresql://postgres:postgres@localhost:5432/neondb?sslmode=require")
            ));

        // JWT Secret: Pulumi Secret > .env > Auto-generate 64-char hex
        this.jwtSecret = appConfig.getSecret("jwtSecret")
            .applyValue(opt -> opt.orElseGet(() ->
                EnvLoader.get("JWT_SECRET").orElseGet(StackConfig::generateRandomSecret)
            ));

        // App Params
        this.corsOrigin          = appConfig.get("corsOrigin").orElseGet(() -> EnvLoader.get("CORS_ORIGIN", "*"));
        this.appTimezone         = appConfig.get("appTimezone").orElseGet(() -> EnvLoader.get("APP_TIMEZONE", "America/La_Paz"));
        this.appPort             = appConfig.get("appPort").orElseGet(() -> EnvLoader.get("PORT", "3000"));
        this.runMigrations       = appConfig.get("runMigrations").orElseGet(() -> EnvLoader.get("RUN_MIGRATIONS", "true"));
        this.adminRegistrationNo = appConfig.get("adminRegistrationNo").orElseGet(() -> EnvLoader.get("ADMIN_REGISTRATION_NO", "123456789"));
        this.adminPassword       = Output.ofSecret(appConfig.get("adminPassword").orElseGet(() -> EnvLoader.get("ADMIN_PASSWORD", "123456789")));
        this.adminFullName       = appConfig.get("adminFullName").orElseGet(() -> EnvLoader.get("ADMIN_FULL_NAME", "ADMIN"));

        // Repositories & Branch
        this.backendRepoUrl  = appConfig.get("backendRepoUrl")
            .orElseGet(() -> EnvLoader.get("BACKEND_REPO_URL", "https://github.com/modulo2-IaC/app-asistencia-backend.git"));
        this.frontendRepoUrl = appConfig.get("frontendRepoUrl")
            .orElseGet(() -> EnvLoader.get("FRONTEND_REPO_URL", "https://github.com/modulo2-IaC/app-asistencia-frontend.git"));
        this.gitBranch       = appConfig.get("gitBranch")
            .orElseGet(() -> EnvLoader.get("GIT_BRANCH", "dev"));
        this.viteApiUrl      = appConfig.get("viteApiUrl")
            .orElseGet(() -> EnvLoader.get("VITE_API_URL", "/api/v1/"));
        this.nodeVersion     = appConfig.get("nodeVersion")
            .orElseGet(() -> EnvLoader.get("NODE_VERSION", "20"));
    }

    public static String generateRandomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public String getLocation()                  { return location; }
    public String getEnvironment()               { return environment; }
    public String getResourceGroupName()         { return resourceGroupName; }
    public String getVmSize()                    { return vmSize; }
    public String getAdminUsername()             { return adminUsername; }
    public Output<String> getVmPassword()        { return vmPassword; }

    public Output<String> getDatabaseUrl()       { return databaseUrl; }
    public Output<String> getJwtSecret()         { return jwtSecret; }
    public String getCorsOrigin()                { return corsOrigin; }
    public String getAppTimezone()               { return appTimezone; }
    public String getAppPort()                   { return appPort; }
    public String getRunMigrations()             { return runMigrations; }
    public String getAdminRegistrationNo()       { return adminRegistrationNo; }
    public Output<String> getAdminPassword()     { return adminPassword; }
    public String getAdminFullName()             { return adminFullName; }
    public String getBackendRepoUrl()            { return backendRepoUrl; }

    public String getFrontendRepoUrl()           { return frontendRepoUrl; }
    public String getViteApiUrl()                { return viteApiUrl; }

    public String getGitBranch()                 { return gitBranch; }
    public String getNodeVersion()               { return nodeVersion; }
}
