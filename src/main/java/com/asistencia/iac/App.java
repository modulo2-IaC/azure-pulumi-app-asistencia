package com.asistencia.iac;

import com.pulumi.Pulumi;
import com.asistencia.iac.stack.AsistenciaStack;

/**
 * Punto de entrada principal para el aprovisionamiento unificado en Azure.
 */
public class App {

    public static void main(String[] args) {
        Pulumi.run(AsistenciaStack::define);
    }
}
