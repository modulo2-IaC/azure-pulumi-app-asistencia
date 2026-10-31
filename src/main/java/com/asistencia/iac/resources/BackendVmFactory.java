package com.asistencia.iac.resources;

import com.pulumi.core.Output;
import com.pulumi.azurenative.compute.VirtualMachine;
import com.pulumi.azurenative.compute.VirtualMachineArgs;
import com.pulumi.azurenative.compute.enums.CachingTypes;
import com.pulumi.azurenative.compute.enums.DiskCreateOptionTypes;
import com.pulumi.azurenative.compute.enums.StorageAccountTypes;
import com.pulumi.azurenative.compute.inputs.HardwareProfileArgs;
import com.pulumi.azurenative.compute.inputs.ImageReferenceArgs;
import com.pulumi.azurenative.compute.inputs.LinuxConfigurationArgs;
import com.pulumi.azurenative.compute.inputs.ManagedDiskParametersArgs;
import com.pulumi.azurenative.compute.inputs.NetworkInterfaceReferenceArgs;
import com.pulumi.azurenative.compute.inputs.NetworkProfileArgs;
import com.pulumi.azurenative.compute.inputs.OSDiskArgs;
import com.pulumi.azurenative.compute.inputs.OSProfileArgs;
import com.pulumi.azurenative.compute.inputs.StorageProfileArgs;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.asistencia.iac.config.StackConfig;
import com.asistencia.iac.resources.BackendNetworkFactory.NetworkResult;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Factory responsable de crear la Máquina Virtual del Backend NestJS con Cloud-Init.
 *
 * Utiliza autenticación exclusiva por contraseña de administrador (sin claves SSH).
 */
public class BackendVmFactory {

    private BackendVmFactory() { /* Clase utilitaria */ }

    public static class BackendVmResult {
        public final VirtualMachine vm;
        public final Output<String> publicIpAddress;

        public BackendVmResult(VirtualMachine vm, Output<String> publicIpAddress) {
            this.vm              = vm;
            this.publicIpAddress = publicIpAddress;
        }
    }

    public static BackendVmResult create(StackConfig config, ResourceGroup rg, NetworkResult net) {
        String env = config.getEnvironment();

        // Generar cloud-init interpolado con los secretos resueltos
        Output<String> cloudInitScript = buildCloudInitScript(config);
        Output<String> customDataB64   = cloudInitScript.applyValue(
            script -> Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_8))
        );

        VirtualMachine vm = new VirtualMachine(
            "vm-backend",
            VirtualMachineArgs.builder()
                .vmName("vm-asistencia-backend-" + env)
                .resourceGroupName(rg.name())
                .location(config.getLocation())

                .hardwareProfile(HardwareProfileArgs.builder()
                    .vmSize(config.getVmSize())
                    .build())

                .storageProfile(StorageProfileArgs.builder()
                    .imageReference(ImageReferenceArgs.builder()
                        .publisher("Canonical")
                        .offer("0001-com-ubuntu-server-jammy")
                        .sku("22_04-lts-gen2")
                        .version("latest")
                        .build())
                    .osDisk(OSDiskArgs.builder()
                        .name("osdisk-asistencia-backend-" + env)
                        .createOption(DiskCreateOptionTypes.FromImage)
                        .caching(CachingTypes.ReadWrite)
                        .managedDisk(ManagedDiskParametersArgs.builder()
                            .storageAccountType(StorageAccountTypes.Standard_LRS)
                            .build())
                        .build())
                    .build())

                .osProfile(OSProfileArgs.builder()
                    .computerName("backend-vm")
                    .adminUsername(config.getAdminUsername())
                    .adminPassword(config.getVmPassword())
                    .customData(customDataB64)
                    .linuxConfiguration(LinuxConfigurationArgs.builder()
                        .disablePasswordAuthentication(false)
                        .build())
                    .build())

                .networkProfile(NetworkProfileArgs.builder()
                    .networkInterfaces(List.of(
                        NetworkInterfaceReferenceArgs.builder()
                            .id(net.nic.id())
                            .primary(true)
                            .build()
                    ))
                    .build())

                .tags(Map.of(
                    "project",     "asistencia",
                    "component",   "backend-vm",
                    "environment", env,
                    "managed-by",  "pulumi"
                ))
                .build()
        );

        Output<String> publicIpAddress = net.publicIp.ipAddress()
            .applyValue(ip -> ip.filter(s -> !s.isBlank() && !s.contains(" ")).orElse("127.0.0.1"));

        return new BackendVmResult(vm, publicIpAddress);
    }

    private static Output<String> buildCloudInitScript(StackConfig config) {
        String template = loadTemplate("cloud-init-backend.yaml");

        return Output.tuple(
            config.getDatabaseUrl(),
            config.getJwtSecret(),
            config.getAdminPassword()
        ).applyValue(tuple -> {
            String dbUrl         = tuple.t1;
            String jwtSecret     = tuple.t2;
            String adminPassword = tuple.t3;

            return template
                .replace("${node_version}",          config.getNodeVersion())
                .replace("${admin_username}",        config.getAdminUsername())
                .replace("${repo_url}",              config.getBackendRepoUrl())
                .replace("${git_branch}",            config.getGitBranch())
                .replace("${app_port}",              config.getAppPort())
                .replace("${database_url}",          dbUrl)
                .replace("${jwt_secret}",            jwtSecret)
                .replace("${cors_origin}",           config.getCorsOrigin())
                .replace("${app_timezone}",          config.getAppTimezone())
                .replace("${run_migrations}",        config.getRunMigrations())
                .replace("${admin_registration_no}", config.getAdminRegistrationNo())
                .replace("${admin_password}",        adminPassword)
                .replace("${admin_full_name}",       config.getAdminFullName());
        });
    }

    private static String loadTemplate(String resourceName) {
        try (InputStream in = BackendVmFactory.class.getResourceAsStream("/" + resourceName)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }

        try {
            Path path = Path.of("src/main/resources", resourceName);
            if (Files.exists(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }

        throw new IllegalStateException("No se pudo cargar la plantilla cloud-init para backend: " + resourceName);
    }
}
