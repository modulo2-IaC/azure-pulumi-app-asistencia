package com.asistencia.iac.resources;

import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.resources.ResourceGroupArgs;
import com.asistencia.iac.config.StackConfig;

import java.util.Map;

/**
 * Factory responsable de aprovisionar el Resource Group contenedor de todo el hardware.
 */
public class ResourceGroupFactory {

    private ResourceGroupFactory() { /* Clase utilitaria */ }

    public static ResourceGroup create(StackConfig config) {
        return new ResourceGroup(
            "rg-hardware",
            ResourceGroupArgs.builder()
                .resourceGroupName(config.getResourceGroupName())
                .location(config.getLocation())
                .tags(Map.of(
                    "project",     "asistencia",
                    "component",   "hardware",
                    "environment", config.getEnvironment(),
                    "managed-by",  "pulumi"
                ))
                .build()
        );
    }
}
