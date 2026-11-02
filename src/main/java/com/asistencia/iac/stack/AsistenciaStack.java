package com.asistencia.iac.stack;

import com.pulumi.Context;
import com.pulumi.core.Output;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.asistencia.iac.config.StackConfig;
import com.asistencia.iac.resources.ResourceGroupFactory;
import com.asistencia.iac.resources.BackendNetworkFactory;
import com.asistencia.iac.resources.BackendNetworkFactory.NetworkResult;
import com.asistencia.iac.resources.BackendVmFactory;
import com.asistencia.iac.resources.BackendVmFactory.BackendVmResult;
import com.asistencia.iac.resources.FrontendNetworkFactory;
import com.asistencia.iac.resources.FrontendVmFactory;
import com.asistencia.iac.resources.FrontendVmFactory.FrontendVmResult;

/**
 * Orquestador unificado del hardware completo de la aplicación Asistencia en Azure.
 *
 * Principio arquitectónico:
 *   - Crea un único grafo de dependencias en Pulumi.
 *   - El backend y el frontend se aprovisionan en una sola ejecución.
 *   - La IP pública del backend se enlaza reactivamente como Output hacia el Frontend,
 *     configurando el Reverse Proxy de Nginx de forma 100% automatizada.
 */
public class AsistenciaStack {

    public static void define(Context ctx) {
        StackConfig config = new StackConfig(ctx);

        // 1. Resource Group contenedor unificado
        ResourceGroup rg = ResourceGroupFactory.create(config);

        // 2. Infraestructura Backend (Red + VM NestJS)
        NetworkResult backendNet    = BackendNetworkFactory.create(config, rg);
        BackendVmResult backendVm   = BackendVmFactory.create(config, rg, backendNet);

        // 3. Infraestructura Frontend (Red + VM Nginx/Vite)
        // Pasamos directamente backendVm.publicIpAddress para que Nginx configure el reverse proxy
        FrontendNetworkFactory.NetworkResult frontendNet = FrontendNetworkFactory.create(config, rg);
        FrontendVmResult frontendVm = FrontendVmFactory.create(
            config, rg, frontendNet, backendVm.publicIpAddress
        );

        // 4. Exportar salidas útiles del stack unificado
        ctx.export("resourceGroupName", rg.name());
        ctx.export("environment",       Output.of(config.getEnvironment()));
        ctx.export("location",          Output.of(config.getLocation()));
        ctx.export("gitBranch",         Output.of(config.getGitBranch()));

        // Salidas Backend
        ctx.export("backendPublicIp",   backendVm.publicIpAddress);
        ctx.export("backendApiUrl",
            backendVm.publicIpAddress.applyValue(ip -> "http://" + ip + ":" + config.getAppPort()));
        ctx.export("backendDocsUrl",
            backendVm.publicIpAddress.applyValue(ip -> "http://" + ip + ":" + config.getAppPort() + "/api/v1/docs"));

        // Salidas Frontend
        ctx.export("frontendPublicIp",  frontendVm.publicIpAddress);
        ctx.export("frontendUrl",
            frontendVm.publicIpAddress.applyValue(ip -> "http://" + ip));

        // Credenciales y comandos de acceso (Autenticación por contraseña)
        ctx.export("adminUsername",     Output.of(config.getAdminUsername()));
        ctx.export("sshBackendCmd",
            backendVm.publicIpAddress.applyValue(ip ->
                "ssh " + config.getAdminUsername() + "@" + ip));
        ctx.export("sshFrontendCmd",
            frontendVm.publicIpAddress.applyValue(ip ->
                "ssh " + config.getAdminUsername() + "@" + ip));

        ctx.export("checkBackendLogsCmd",
            backendVm.publicIpAddress.applyValue(ip ->
                "ssh " + config.getAdminUsername() + "@" + ip + " 'cat /var/log/asistencia-setup.log'"));
        ctx.export("checkFrontendLogsCmd",
            frontendVm.publicIpAddress.applyValue(ip ->
                "ssh " + config.getAdminUsername() + "@" + ip + " 'cat /var/log/asistencia-setup.log'"));
        ctx.export("checkBackendStatusCmd",
            backendVm.publicIpAddress.applyValue(ip ->
                "ssh " + config.getAdminUsername() + "@" + ip + " 'systemctl status asistencia-backend'"));
        ctx.export("checkFrontendStatusCmd",
            frontendVm.publicIpAddress.applyValue(ip ->
                "ssh " + config.getAdminUsername() + "@" + ip + " 'systemctl status nginx'"));
        ctx.export("checkPm2Cmd",
            backendVm.publicIpAddress.applyValue(ip ->
                "ssh " + config.getAdminUsername() + "@" + ip + " 'systemctl status asistencia-backend'"));
    }
}
