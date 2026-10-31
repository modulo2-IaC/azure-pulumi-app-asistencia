# ☁️ Asistencia — Infraestructura Unificada en Azure con Pulumi (Java) & CI/CD

Infraestructura como Código (**IaC**) desarrollada con **Pulumi en Java (Maven)** para aprovisionar, configurar y desplegar de forma **100% automatizada** las Máquinas Virtuales en **Microsoft Azure** para el sistema completo de control de asistencia.

Este proyecto unifica en un **único grafo de recursos** tanto la capa de **Backend (NestJS)** como la de **Frontend (React + Vite + Nginx)**, eliminando la necesidad de comandos manuales (`pulumi config set`) y de copiar direcciones IP entre componentes.

---

## 🏛 Arquitectura Global del Sistema

```mermaid
flowchart TD
    Client["💻 Navegador del Usuario / Cliente Web"]

    subgraph AzureCloud ["☁️ Microsoft Azure (swedencentral)"]
        subgraph UnifiedRG ["📦 Resource Group: rg-asistencia-hardware-dev"]
            
            subgraph FrontSub ["🌐 Frontend (React + Vite + Nginx)"]
                VNet_Front["VNet 10.1.0.0/16<br/>Subnet 10.1.1.0/24"]
                NSG_Front["NSG: Puertos 22 (SSH) y 80 (HTTP)"]
                PIP_Front["IP Pública Estática Standard"]
                VM_Front["VM Linux Ubuntu 22.04 LTS<br/>(Standard_B2as_v2)"]
                CloudInit_Front["Cloud-Init:<br/>1GB Swap, Nginx, SPA Router<br/>Reverse Proxy /api/ -> Backend:3000"]
                
                VNet_Front & NSG_Front & PIP_Front --> VM_Front
                VM_Front --> CloudInit_Front
            end

            subgraph BackSub ["⚙️ Backend (NestJS + TypeORM + PM2)"]
                VNet_Back["VNet 10.0.0.0/16<br/>Subnet 10.0.1.0/24"]
                NSG_Back["NSG: Puertos 22 (SSH) y 3000 (API)"]
                PIP_Back["IP Pública Estática Standard"]
                VM_Back["VM Linux Ubuntu 22.04 LTS<br/>(Standard_B2as_v2)"]
                CloudInit_Back["Cloud-Init:<br/>1GB Swap, Node.js 20, PM2<br/>Migraciones TypeORM & Seeds"]
                
                VNet_Back & NSG_Back & PIP_Back --> VM_Back
                VM_Back --> CloudInit_Back
            end

        end
    end

    NeonDB[("🐘 Neon PostgreSQL Cloud<br/>(Serverless con SSL)")]

    Client -->|HTTP :80| PIP_Front
    PIP_Front --> VM_Front
    VM_Front -->|Proxy Reverso Interno :80 /api/ -> :3000/api/| PIP_Back
    PIP_Back --> VM_Back
    VM_Back -->|SSL :5432| NeonDB
```

---

## 📁 Estructura del Repositorio

```text
hardware/
├── .github/
│   └── workflows/
│       └── deploy.yml              # CI/CD automatizado con GitHub Actions
├── .env.example                    # Plantilla de variables de entorno
├── .gitignore                      # Exclusión de archivos sensibles y compilados
├── pom.xml                         # Dependencias Maven (Pulumi Java SDK, azure-native)
├── Pulumi.yaml                     # Definición del proyecto Pulumi unificado
├── Pulumi.dev.yaml                 # Configuración del stack dev
├── README.md                       # Documentación completa (este archivo)
└── src/main/
    ├── resources/
    │   ├── cloud-init-backend.yaml  # Plantilla empaquetada en el JAR para Backend NestJS
    │   └── cloud-init-frontend.yaml # Plantilla empaquetada en el JAR para Frontend Vite/Nginx
    └── java/com/asistencia/iac/
        ├── App.java                 # Punto de entrada de la aplicación
        ├── config/
        │   ├── EnvLoader.java       # Carga automática transparente de .env
        │   └── StackConfig.java     # Configuración con resolución automática (SSH, JWT, BD)
        ├── stack/
        │   └── AsistenciaStack.java # Orquestador del grafo de dependencias
        └── resources/
            ├── ResourceGroupFactory.java   # Contenedor común en Azure
            ├── BackendNetworkFactory.java  # VNet 10.0.0.0/16, NSG (22, 3000), IP, NIC
            ├── BackendVmFactory.java       # VM Backend + Cloud-Init
            ├── FrontendNetworkFactory.java # VNet 10.1.0.0/16, NSG (22, 80), IP, NIC
            └── FrontendVmFactory.java      # VM Frontend + Cloud-Init enlazado al Backend
```

---

## ⚡ Filosofía "Cero Configuración Manual"

Este proyecto implementa mecanismos inteligentes para que **nunca tengas que configurar variables a mano**:

1. **Lectura automática de `.env`:** Basta con tener un archivo `.env` en la raíz (puedes crearlo a partir de `.env.example`). Si existe, el código Java lo carga automáticamente sin comandos adicionales.
2. **Auto-resolución de la IP del Backend:** El Frontend recibe directamente el `Output<String>` de la IP pública del backend generada en Azure. Pulumi espera a que la IP esté lista y la inyecta de forma transparente en el archivo de configuración de Nginx.
3. **Detección inteligente de Clave SSH:** Si no defines una clave SSH, el sistema busca automáticamente tus claves públicas existentes en `~/.ssh/id_rsa.pub`, `~/.ssh/id_ed25519.pub` o `~/.ssh/asistencia_vm.pub`. Si no tienes ninguna, **genera una clave RSA automáticamente** y guarda la clave privada en `~/.ssh/asistencia_auto_id_rsa`.
4. **Auto-generación de JWT Secret:** Si no defines `JWT_SECRET`, el sistema genera criptográficamente un secreto aleatorio de 64 caracteres.
5. **Rama `dev` por defecto:** Clona y arranca automáticamente desde la rama `dev` de ambos repositorios:
   * Backend: `https://github.com/modulo2-IaC/app-asistencia-backend.git`
   * Frontend: `https://github.com/modulo2-IaC/app-asistencia-frontend.git`

---

## 🚀 Despliegue Local Rápido

### 1. Requisitos Previos
* [Java JDK 11 o 17+](https://adoptium.net/)
* [Apache Maven 3.8+](https://maven.apache.org/)
* [Pulumi CLI 3.x+](https://www.pulumi.com/docs/install/)
* [Azure CLI](https://learn.microsoft.com/es-es/cli/azure/install-azure-cli) autenticado (`az login`)

### 2. Configurar el archivo `.env`
Copia la plantilla y coloca la URL de tu base de datos PostgreSQL (ej. Neon):

```powershell
cp .env.example .env
```

Edita `.env` con la URL de tu base de datos:
```ini
DATABASE_URL=postgresql://neondb_owner:tu_password@ep-misty-scene...neon.tech/neondb?sslmode=require
```

### 3. Vista previa y Despliegue
En un solo comando se aprovisiona toda la infraestructura:

```powershell
# Compilar el proyecto Java
mvn clean compile

# Vista previa de los 14 recursos a crear
pulumi preview

# Desplegar todo en Azure (Backend + Frontend)
pulumi up
```

---

## 🤖 Integración Continua y Despliegue con GitHub Actions (CI/CD)

El archivo [`.github/workflows/deploy.yml`](file:///.github/workflows/deploy.yml) automatiza el ciclo de vida en GitHub:

* **Pull Requests a `main` o `dev`:** Ejecuta `pulumi preview` y publica el resultado del plan como comentario en el PR.
* **Push a `main` o `dev`:** Ejecuta `pulumi up` automáticamente y despliega la infraestructura en Azure.
* **Manual (`workflow_dispatch`):** Permite ejecutar `preview` o `up` bajo demanda desde la pestaña **Actions** en GitHub.

### Configuración de Secretos en GitHub (Repository Secrets)

Ve a tu repositorio en GitHub: **Settings** > **Secrets and variables** > **Actions** y añade los siguientes secretos:

| Secreto en GitHub | Descripción | ¿Requerido? |
|---|---|---|
| `AZURE_CLIENT_ID` | Application / Client ID del Service Principal de Azure | ✅ Sí |
| `AZURE_TENANT_ID` | Directory / Tenant ID de Microsoft Entra ID (Azure AD) | ✅ Sí |
| `AZURE_SUBSCRIPTION_ID` | ID de la suscripción de Azure donde se desplegará | ✅ Sí |
| `PULUMI_ACCESS_TOKEN` | Token de acceso de tu cuenta de Pulumi Cloud | ✅ Sí |
| `DATABASE_URL` | Cadena de conexión PostgreSQL (con `?sslmode=require`) | ✅ Sí |
| `SSH_PUBLIC_KEY` | Clave pública SSH (opcional, se auto-genera si falta) | ⚪ Opcional |
| `JWT_SECRET` | Secreto para tokens JWT (opcional, se auto-genera si falta) | ⚪ Opcional |

> **Tip para Azure OIDC:** Si configuras credenciales federadas (OIDC) en tu Service Principal de Azure para GitHub Actions, no necesitas almacenar contraseñas ni `AZURE_CLIENT_SECRET`.

---

## 🔍 Verificación y Mantenimiento de las VMs

Al finalizar el despliegue, Pulumi exportará todas las URLs y comandos de administración:

```powershell
# Ver todas las salidas del stack
pulumi stack output

# Conectarse por SSH a cualquiera de las VMs
ssh azureuser@<backendPublicIp>
ssh azureuser@<frontendPublicIp>

# Consultar el estado del aprovisionamiento Cloud-Init
ssh azureuser@<backendPublicIp> "cat /var/log/asistencia-setup.log"
ssh azureuser@<frontendPublicIp> "cat /var/log/asistencia-setup.log"

# En el backend, comprobar el estado de los procesos con PM2
ssh azureuser@<backendPublicIp> "pm2 status"

# Actualizar el código desde Git en cualquier momento (ambas VMs incluyen ~/deploy.sh)
ssh azureuser@<backendPublicIp> "~/deploy.sh"
ssh azureuser@<frontendPublicIp> "~/deploy.sh"
```

---

## 🧹 Destrucción de Recursos

Para eliminar todos los recursos de Azure de forma limpia y evitar consumos no deseados:

```powershell
pulumi destroy
```
