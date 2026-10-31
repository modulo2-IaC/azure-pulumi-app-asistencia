package com.asistencia.iac.config;

import com.pulumi.Config;
import com.pulumi.Context;
import com.pulumi.core.Output;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Configuración centralizada y automatizada para el stack unificado de Azure Hardware.
 *
 * Elimina la necesidad de escribir comandos manuales en la terminal.
 * Resuelve automáticamente secretos y parámetros desde:
 *   1. Pulumi stack config (si existen)
 *   2. Archivo .env local o variables de entorno del sistema
 *   3. Detección automática en disco (~/.ssh/) o generación dinámica segura
 */
public class StackConfig {

    // ── Azure ─────────────────────────────────────────────────────────────────
    private final String location;
    private final String environment;
    private final String resourceGroupName;

    // ── VM Común ──────────────────────────────────────────────────────────────
    private final String vmSize;
    private final String adminUsername;
    private final Output<String> sshPublicKey;

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

        // VM Specs
        this.vmSize = appConfig.get("vmSize")
            .orElseGet(() -> EnvLoader.get("VM_SIZE", "Standard_B2as_v2"));

        this.adminUsername = appConfig.get("adminUsername")
            .orElseGet(() -> EnvLoader.get("ADMIN_USERNAME", "azureuser"));

        // SSH Public Key: Pulumi Secret > .env > ~/.ssh/ > Auto-generate
        String resolvedSshKey = resolveSshPublicKey(appConfig);
        this.sshPublicKey = Output.ofSecret(resolvedSshKey);

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

    private static String resolveSshPublicKey(Config config) {
        // 1. Pulumi config
        Optional<String> cfgKey = config.get("sshPublicKey");
        if (cfgKey.isPresent() && !cfgKey.get().isBlank()) {
            return cfgKey.get().trim();
        }

        // 2. .env o variable de entorno
        Optional<String> envKey = EnvLoader.get("SSH_PUBLIC_KEY");
        if (envKey.isPresent() && !envKey.get().isBlank()) {
            return envKey.get().trim();
        }

        // 3. Buscar en ~/.ssh/
        String userHome = System.getProperty("user.home");
        List<Path> candidateKeys = List.of(
            Path.of(userHome, ".ssh", "asistencia_vm.pub"),
            Path.of(userHome, ".ssh", "id_rsa.pub"),
            Path.of(userHome, ".ssh", "id_ed25519.pub")
        );

        for (Path keyPath : candidateKeys) {
            try {
                if (Files.isRegularFile(keyPath)) {
                    String content = Files.readString(keyPath, StandardCharsets.UTF_8).trim();
                    if (!content.isBlank()) {
                        return content;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        // 4. Auto-generar par de claves RSA si no existe ninguna
        return generateFallbackSshKeyPair(userHome);
    }

    private static String generateFallbackSshKeyPair(String userHome) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair pair = keyGen.generateKeyPair();
            RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
            RSAPrivateKey priv = (RSAPrivateKey) pair.getPrivate();

            ByteArrayOutputStream byteOs = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(byteOs);
            dos.writeInt("ssh-rsa".getBytes(StandardCharsets.US_ASCII).length);
            dos.write("ssh-rsa".getBytes(StandardCharsets.US_ASCII));
            dos.writeInt(pub.getPublicExponent().toByteArray().length);
            dos.write(pub.getPublicExponent().toByteArray());
            dos.writeInt(pub.getModulus().toByteArray().length);
            dos.write(pub.getModulus().toByteArray());

            String enc = Base64.getEncoder().encodeToString(byteOs.toByteArray());
            String sshPub = "ssh-rsa " + enc + " auto-generated-azure-key";

            Path sshDir = Path.of(userHome, ".ssh");
            if (!Files.exists(sshDir)) {
                Files.createDirectories(sshDir);
            }
            Path privKeyPath = sshDir.resolve("asistencia_auto_id_rsa");
            if (!Files.exists(privKeyPath)) {
                String privPem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(priv.getEncoded())
                    + "\n-----END PRIVATE KEY-----\n";
                Files.writeString(privKeyPath, privPem, StandardCharsets.UTF_8);
            }

            return sshPub;
        } catch (Exception e) {
            // Clave pública dummy en caso de error extremo de I/O
            return "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQC3 dummy-key";
        }
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
    public Output<String> getSshPublicKey()      { return sshPublicKey; }

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
