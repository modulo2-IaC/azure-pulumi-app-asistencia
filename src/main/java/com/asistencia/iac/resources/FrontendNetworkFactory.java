package com.asistencia.iac.resources;

import com.pulumi.azurenative.network.VirtualNetwork;
import com.pulumi.azurenative.network.VirtualNetworkArgs;
import com.pulumi.azurenative.network.Subnet;
import com.pulumi.azurenative.network.SubnetArgs;
import com.pulumi.azurenative.network.NetworkSecurityGroup;
import com.pulumi.azurenative.network.NetworkSecurityGroupArgs;
import com.pulumi.azurenative.network.NetworkInterface;
import com.pulumi.azurenative.network.NetworkInterfaceArgs;
import com.pulumi.azurenative.network.PublicIPAddress;
import com.pulumi.azurenative.network.PublicIPAddressArgs;
import com.pulumi.azurenative.network.enums.IPAllocationMethod;
import com.pulumi.azurenative.network.enums.PublicIPAddressSkuName;
import com.pulumi.azurenative.network.enums.SecurityRuleAccess;
import com.pulumi.azurenative.network.enums.SecurityRuleDirection;
import com.pulumi.azurenative.network.enums.SecurityRuleProtocol;
import com.pulumi.azurenative.network.inputs.AddressSpaceArgs;
import com.pulumi.azurenative.network.inputs.NetworkInterfaceIPConfigurationArgs;
import com.pulumi.azurenative.network.inputs.PublicIPAddressSkuArgs;
import com.pulumi.azurenative.network.inputs.SecurityRuleArgs;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.asistencia.iac.config.StackConfig;

import java.util.List;
import java.util.Map;

/**
 * Factory responsable de crear la infraestructura de red para el Frontend React/Vite.
 */
public class FrontendNetworkFactory {

    private FrontendNetworkFactory() { /* Clase utilitaria */ }

    public static class NetworkResult {
        public final NetworkInterface nic;
        public final PublicIPAddress  publicIp;

        public NetworkResult(NetworkInterface nic, PublicIPAddress publicIp) {
            this.nic      = nic;
            this.publicIp = publicIp;
        }
    }

    public static NetworkResult create(StackConfig config, ResourceGroup rg) {
        String env = config.getEnvironment();
        Map<String, String> tags = Map.of(
            "project",     "asistencia",
            "component",   "frontend-network",
            "environment", env,
            "managed-by",  "pulumi"
        );

        // 1. VNet Frontend (10.1.0.0/16)
        VirtualNetwork vnet = new VirtualNetwork(
            "vnet-frontend",
            VirtualNetworkArgs.builder()
                .virtualNetworkName("vnet-asistencia-frontend-" + env)
                .resourceGroupName(rg.name())
                .location(config.getLocation())
                .addressSpace(AddressSpaceArgs.builder()
                    .addressPrefixes(List.of("10.1.0.0/16"))
                    .build())
                .tags(tags)
                .build()
        );

        // 2. Subred Frontend (10.1.1.0/24)
        Subnet subnet = new Subnet(
            "subnet-frontend",
            SubnetArgs.builder()
                .subnetName("subnet-frontend")
                .virtualNetworkName(vnet.name())
                .resourceGroupName(rg.name())
                .addressPrefix("10.1.1.0/24")
                .build()
        );

        // 3. NSG Frontend: Puertos 22 (SSH) y 80 (HTTP Nginx)
        NetworkSecurityGroup nsg = new NetworkSecurityGroup(
            "nsg-frontend",
            NetworkSecurityGroupArgs.builder()
                .networkSecurityGroupName("nsg-asistencia-frontend-" + env)
                .resourceGroupName(rg.name())
                .location(config.getLocation())
                .securityRules(List.of(
                    SecurityRuleArgs.builder()
                        .name("allow-ssh")
                        .priority(100)
                        .direction(SecurityRuleDirection.Inbound)
                        .access(SecurityRuleAccess.Allow)
                        .protocol(SecurityRuleProtocol.Tcp)
                        .sourcePortRange("*")
                        .destinationPortRange("22")
                        .sourceAddressPrefix("*")
                        .destinationAddressPrefix("*")
                        .build(),
                    SecurityRuleArgs.builder()
                        .name("allow-http")
                        .priority(110)
                        .direction(SecurityRuleDirection.Inbound)
                        .access(SecurityRuleAccess.Allow)
                        .protocol(SecurityRuleProtocol.Tcp)
                        .sourcePortRange("*")
                        .destinationPortRange("80")
                        .sourceAddressPrefix("*")
                        .destinationAddressPrefix("*")
                        .build()
                ))
                .tags(tags)
                .build()
        );

        // 4. IP Pública Estática Standard
        PublicIPAddress publicIp = new PublicIPAddress(
            "pip-frontend",
            PublicIPAddressArgs.builder()
                .publicIpAddressName("pip-asistencia-frontend-" + env)
                .resourceGroupName(rg.name())
                .location(config.getLocation())
                .sku(PublicIPAddressSkuArgs.builder()
                    .name(PublicIPAddressSkuName.Standard)
                    .build())
                .publicIPAllocationMethod(IPAllocationMethod.Static)
                .tags(tags)
                .build()
        );

        // 5. Interfaz de Red (NIC)
        NetworkInterface nic = new NetworkInterface(
            "nic-frontend",
            NetworkInterfaceArgs.builder()
                .networkInterfaceName("nic-asistencia-frontend-" + env)
                .resourceGroupName(rg.name())
                .location(config.getLocation())
                .networkSecurityGroup(com.pulumi.azurenative.network.inputs.NetworkSecurityGroupArgs.builder()
                    .id(nsg.id())
                    .build())
                .ipConfigurations(List.of(
                    NetworkInterfaceIPConfigurationArgs.builder()
                        .name("ipconfig-frontend")
                        .subnet(com.pulumi.azurenative.network.inputs.SubnetArgs.builder()
                            .id(subnet.id())
                            .build())
                        .publicIPAddress(com.pulumi.azurenative.network.inputs.PublicIPAddressArgs.builder()
                            .id(publicIp.id())
                            .build())
                        .privateIPAllocationMethod(IPAllocationMethod.Dynamic)
                        .build()
                ))
                .tags(tags)
                .build()
        );

        return new NetworkResult(nic, publicIp);
    }
}
