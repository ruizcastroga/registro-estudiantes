# Sistema de Registro de Estudiantes

Sistema de escritorio para el control de acceso y registro de entrada/salida en instituciones educativas. Gestiona estudiantes, visitantes y personal mediante escaneo de carnés con código de barras, con roles de usuario, sesiones con tiempo de espera y generación de carnés imprimibles.

---

## Descripción General

La aplicación funciona como una estación de control de acceso operada por un guardia. Cualquier persona que intente entrar o salir del plantel escanea su carné, y el sistema valida si tiene permitido hacerlo, muestra información relevante (como tutores autorizados para menores), y registra la actividad en un historial auditable.

La pantalla principal (Scanner) está optimizada para uso con lector de código de barras: el campo de entrada siempre está activo y listo para recibir el código. Los módulos de gestión (Estudiantes, Personal, Visitantes, etc.) se operan con mouse y teclado como cualquier aplicación de escritorio.

---

## Módulos

### 1. Scanner (pantalla principal)
El punto de entrada de la aplicación. El guardia deja el cursor aquí y el escáner de código de barras envía el código automáticamente.

- **Escaneo de estudiantes**: muestra nombre, foto (si tiene), estado y tutores autorizados
- **Escaneo de personal**: muestra nombre, departamento y estado del miembro del staff
- **Escaneo de visitantes**: detecta el carné de visitante, solicita datos del visitante (cédula, nombre, apellido, motivo) y registra la entrada; al escanear por segunda vez, registra la salida
- Historial de escaneos de la sesión actual visible en la misma pantalla
- Muestra estadísticas rápidas: total de escaneos del día y personas actualmente dentro

### 2. Estudiantes
CRUD completo para la gestión del padrón de estudiantes.

- Búsqueda instantánea por nombre o código de barras
- Campos: código de barras, nombre, apellido, grado, estado, foto (opcional)
- Estados: **Activo**, **Inactivo**, **Suspendido** (el scanner muestra el estado apropiado al escanear)
- Opción "Menor de edad — requiere acompañante": al escanear, se muestran los tutores legales registrados
- Gestión de **Tutores Legales**: nombre, cédula, teléfono, relación (se muestran al guardia en pantalla)
- Importación masiva desde **CSV** con plantilla descargable
- Todas las modificaciones requieren sesión de administrador activa

### 3. Visitantes
Control de visitantes con sistema de carnés físicos reutilizables.

- **Visitantes dentro ahora**: tabla en tiempo real de quién está en el recinto
- **Gestionar carnés**: agregar carnés con código único (ej: `VIS-01`), importar desde CSV
  - Estados del carné: Disponible, En uso, Perdido
  - Acciones: cambiar estado, eliminar
- **Historial de visitas**: registro completo con cédula, nombre, motivo, hora de entrada y salida
  - Búsqueda por carné, cédula o nombre
  - Exportar a CSV, filtrar por rango de fechas, borrar historial
- El registro de entrada/salida se realiza desde el **Scanner principal** (no desde este módulo)

### 4. Personal
Gestión del staff de la institución (docentes, administrativos, etc.).

- Búsqueda por nombre, cédula o departamento
- Campos: cédula, nombre, apellido, departamento, estado
- El código de barras del carné se genera **automáticamente** desde la cédula
- Estados: **Activo**, **Inactivo**, **Suspendido**
- Importación masiva desde CSV con plantilla descargable
- Modificaciones requieren sesión de administrador

### 5. Creador de Carné
Generación e impresión de carnés físicos en formato CR80 (85.6 × 54 mm, tamaño tarjeta de crédito).

- Tipos soportados: **Estudiante**, **Visitante**, **Personal**
- Búsqueda de registros existentes para prellenar el formulario
- Vista previa en tiempo real del carné mientras se completan los datos
- Foto del portador: seleccionar desde archivo, previsualizar en el carné
- Código de barras generado automáticamente con ZXing
- El carné incluye: nombre de la institución, escudo/logo, nombre, cédula/grado, foto y código de barras
- Impresión directa desde la aplicación

### 6. Ajustes
Configuración del sistema y gestión de usuarios.

- **Configuración general**: nombre de la institución, escudo/logo, tiempo de espera de sesión
- **Gestión de usuarios**:
  - Roles: **Administrador** (acceso total) y **Operador** (solo lectura + escaneo)
  - Campos: nombre de usuario, contraseña, rol, tiempo de sesión individual
  - No se puede eliminar el propio usuario con sesión activa
- **Base de datos**: exportar copia de seguridad / importar desde respaldo
- **API REST**: ver clave de acceso, copiarla al portapapeles, regenerarla

---

## Sistema de Sesiones y Roles

La aplicación tiene una barra de sesión en la parte superior de cada módulo.

| Rol | Permisos |
|-----|----------|
| **Administrador** | Todas las operaciones: agregar, editar, eliminar registros; gestionar usuarios y ajustes; importar/exportar datos |
| **Operador** | Solo puede operar el scanner y visualizar listas y detalles; no puede modificar registros |

- Las sesiones expiran por inactividad (tiempo configurable por usuario, predeterminado: 10 minutos)
- Un temporizador visible cuenta regresivamente en la barra de sesión
- Al expirar, el sistema cierra sesión automáticamente
- El primer inicio requiere crear un usuario administrador

---

## Navegación

Todos los módulos tienen la misma barra de navegación en la parte superior derecha:

```
Scanner | Estudiantes | Visitantes | Personal | Creador de Carné | Ajustes | ? Ayuda
```

El módulo activo aparece deshabilitado (atenuado). El botón **? Ayuda** abre una guía contextual del módulo actual.

---

## API REST

La aplicación expone una API REST embebida en el **puerto 8080** que se inicia automáticamente al abrir el programa y se detiene al cerrarlo. No requiere instalación adicional.

### Casos de uso principales
- **Importar datos masivamente** desde sistemas externos (SIGE, Excel vía script, otro software de gestión)
- **Exportar datos** para reportes, respaldos o migración
- **Integración** con otros sistemas de la institución

### Autenticación
Todos los endpoints requieren el header `X-API-Key`. La clave se genera automáticamente al primer inicio y se puede consultar o regenerar en **Ajustes → API REST**.

### Endpoints disponibles

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/health` | Estado del servidor y la base de datos |
| GET | `/api/students/export` | Exportar todos los estudiantes en JSON |
| POST | `/api/students/import` | Importar estudiantes desde array JSON |
| GET | `/api/staff/export` | Exportar todo el personal en JSON |
| POST | `/api/staff/import` | Importar personal desde array JSON |
| GET | `/api/visitors/badges/export` | Exportar todos los carnés de visitante |
| POST | `/api/visitors/badges/import` | Importar carnés de visitante |
| GET | `/api/visitors/logs/export` | Historial de visitas (parámetros `from`/`to`) |
| GET | `/api/entry-logs/export` | Historial de escaneos del scanner (parámetros `from`/`to`) |

### Documentación completa
Ver [`API.md`](./API.md) para la guía de configuración, datos de prueba, plan de pruebas con Postman y referencia completa de endpoints.

---

## Stack Tecnológico

| Componente | Tecnología | Versión |
|------------|-----------|---------|
| Lenguaje | Java | 17+ |
| UI | JavaFX | 21.0.1 |
| Base de datos | SQLite (sqlite-jdbc) | 3.44.1.0 |
| Códigos de barras | ZXing | 3.5.2 |
| JSON (API REST) | Jackson Databind + JavaTime | 2.17.2 |
| Servidor HTTP (API REST) | JDK built-in (`jdk.httpserver`) | JDK 17+ |
| Logging | SLF4J + Logback | 2.0.9 / 1.4.14 |
| Build | Maven | 3.8+ |

---

## Requisitos

- **JDK 17** o superior (compatible con JDK 21)
- **Maven 3.8+**
- Lector de códigos de barras USB tipo HID (actúa como teclado) — opcional, funciona también escribiendo manualmente

---

## Instalación y Ejecución

```bash
# 1. Clonar el repositorio
git clone <url-del-repositorio>
cd registro-estudiantes

# 2. Compilar
mvn clean compile

# 3. Ejecutar
mvn javafx:run

# 4. (Opcional) Empaquetar como JAR ejecutable
mvn clean package
```

La base de datos se crea automáticamente en `~/.registro-estudiantes/registro_estudiantes.db` (Linux/macOS) o `%USERPROFILE%\.registro-estudiantes\registro_estudiantes.db` (Windows) al primer inicio.

---

## Estructura del Proyecto

```
src/main/java/com/tuempresa/registro/
├── Main.java                           # Punto de entrada
├── controllers/                        # Controladores JavaFX (MVC)
│   ├── ScannerController.java          # Pantalla principal de escaneo
│   ├── StudentCRUDController.java      # Gestión de estudiantes
│   ├── VisitorController.java          # Control de visitantes
│   ├── StaffAdminController.java       # Gestión de personal
│   ├── CarneController.java            # Creador de carnés
│   └── SettingsController.java         # Ajustes y usuarios
├── dao/                                # Data Access Objects (SQLite)
│   ├── DatabaseConnection.java         # Conexión singleton a SQLite
│   ├── StudentDAO.java                 # CRUD de estudiantes
│   ├── GuardianDAO.java                # CRUD de tutores legales
│   ├── EntryLogDAO.java                # Historial de escaneos
│   ├── StaffDAO.java                   # CRUD de personal
│   ├── VisitorBadgeDAO.java            # Gestión de carnés de visitante
│   ├── VisitorLogDAO.java              # Historial de visitas
│   ├── AdminUserDAO.java               # Gestión de usuarios del sistema
│   └── ActivityLogDAO.java             # Log de actividad administrativa
├── models/                             # Modelos de datos (POJOs)
│   ├── Student.java
│   ├── Guardian.java
│   ├── EntryLog.java
│   ├── StaffMember.java
│   ├── VisitorBadge.java
│   ├── VisitorLog.java
│   └── AdminUser.java
├── services/                           # Capa de negocio
│   ├── StudentService.java             # Lógica de validación de estudiantes
│   ├── StaffService.java               # Lógica de personal
│   ├── VisitorService.java             # Lógica de carnés y visitas
│   └── CsvImportService.java           # Importación masiva desde CSV
├── api/                                # API REST embebida (puerto 8080)
│   ├── ApiServer.java                  # Servidor HTTP, ciclo de vida, API key
│   ├── ApiUtils.java                   # Helpers compartidos (auth, JSON, params)
│   └── handlers/
│       ├── HealthHandler.java          # GET /api/health
│       ├── StudentsHandler.java        # /api/students/export|import
│       ├── StaffHandler.java           # /api/staff/export|import
│       ├── VisitorsHandler.java        # /api/visitors/badges|logs
│       └── EntryLogsHandler.java       # /api/entry-logs/export
└── utils/
    ├── SessionManager.java             # Gestión de sesiones con timeout
    ├── SecurityManager.java            # Hash de contraseñas
    └── LicenseManager.java             # Validación de licencia (offline/demo)

src/main/resources/
├── fxml/                               # Vistas FXML (JavaFX)
│   ├── scanner-view.fxml
│   ├── student-crud.fxml
│   ├── visitor-view.fxml
│   ├── staff-admin.fxml
│   ├── carne-view.fxml
│   └── settings-view.fxml
├── css/
│   └── styles.css                      # Tema visual de toda la aplicación
├── database/
│   ├── schema.sql                      # Esquema inicial de la BD
│   └── migration_v2.sql                # Migraciones de versiones
├── images/                             # Directorio para recursos de imagen
└── logback.xml                         # Configuración de logging
```

---

## Base de Datos

Base de datos SQLite almacenada localmente en `~/.registro-estudiantes/registro_estudiantes.db` (Linux/macOS) o `%USERPROFILE%\.registro-estudiantes\registro_estudiantes.db` (Windows). Se crea automáticamente al primer inicio. Tablas principales:

| Tabla | Descripción |
|-------|-------------|
| `students` | Padrón de estudiantes |
| `guardians` | Tutores legales de estudiantes |
| `entry_logs` | Historial de escaneos (entrada/salida) |
| `staff_members` | Miembros del personal |
| `visitor_badges` | Carnés de visitante y su estado |
| `visitor_logs` | Historial de visitas con entrada y salida |
| `admin_users` | Usuarios del sistema (administrador/guardia) |
| `activity_logs` | Auditoría de acciones administrativas |
| `settings` | Configuración del sistema (nombre, logo, timeout) |
| `app_config` | Configuración interna de la app (incluye la API key) |

---

## Archivos de Prueba

El repositorio incluye archivos de ejemplo para importación CSV y para la API REST:

**CSV (importación desde la app):**
- `datos_estudiantes_prueba.csv` — Estudiantes de muestra para importar
- `carnés_prueba.csv` — Carnés de visitante de muestra

**API REST (importación vía Postman/scripts):**
- `scripts/mock_data/students.json` — 15 estudiantes de prueba con grados y estados variados
- `scripts/mock_data/staff.json` — 6 miembros del personal de distintos departamentos
- `scripts/mock_data/visitor_badges.json` — 10 carnés de visitante (`VIS-API-01` a `VIS-API-10`)
- `scripts/seed_api.sh` — Carga todos los datos de prueba en un comando (Linux/macOS)
- `scripts/seed_api.ps1` — Ídem para Windows (PowerShell)
- `postman/registro_api.postman_collection.json` — Colección Postman con todos los endpoints
- `postman/registro_api.postman_environment.json` — Variables de entorno para Postman

---

## Logging

Los logs se escriben en:
- **Consola**: nivel INFO y superior
- **`logs/app.log`**: nivel DEBUG (rotación diaria, máx. 10 MB)
- **`logs/error.log`**: solo errores

---

## Licencia

Todos los derechos reservados.

Para soporte técnico o reportar problemas, contacte al equipo de desarrollo.
