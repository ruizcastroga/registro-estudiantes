# Sistema de Registro de Estudiantes

Sistema de escritorio para el control de acceso y registro de entrada/salida en instituciones educativas. Gestiona estudiantes, visitantes y personal mediante escaneo de carnés con código de barras, con roles de usuario, sesiones con tiempo de espera y generación de carnés imprimibles.

---

## Descripción General

La aplicación funciona como una estación de control de acceso operada por un guardia. Cualquier persona que intente entrar o salir del plantel escanea su carné, y el sistema valida si tiene permitido hacerlo, muestra información relevante (como tutores autorizados para menores), y registra la actividad en un historial auditorio.

La interfaz está diseñada para ser operada desde teclado/escáner sin necesidad de mouse, con el campo de entrada siempre activo para recibir el código de barras del lector.

---

## Módulos

### 1. Scanner (pantalla principal)
El punto de entrada de la aplicación. El guardia deja el cursor aquí y el escáner de código de barras envía el código automáticamente.

- **Escaneo de estudiantes**: muestra nombre, foto (si tiene), estado y tutores autorizados
- **Escaneo de personal**: muestra nombre, departamento y estado del miembro del staff
- **Escaneo de visitantes**: detecta el carné de visitante, solicita datos del visitante (cédula, nombre, apellido, motivo) y registra la entrada; al escanear por segunda vez, registra la salida
- Historial de escaneos del día en la misma pantalla
- Muestra estadísticas rápidas: escaneos del día, personas dentro

### 2. Estudiantes
CRUD completo para la gestión del padrón de estudiantes.

- Búsqueda instantánea por nombre o código de barras
- Campos: código de barras, nombre, apellido, grado, fecha de nacimiento, estado
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
  - Roles: **Administrador** (acceso total) y **Guardia** (solo lectura + escaneo)
  - Campos: nombre de usuario, contraseña, rol, tiempo de sesión individual
  - No se puede eliminar el propio usuario con sesión activa
- **Base de datos**: exportar copia de seguridad / importar desde respaldo

---

## Sistema de Sesiones y Roles

La aplicación tiene una barra de sesión en la parte superior de cada módulo.

| Rol | Permisos |
|-----|----------|
| **Administrador** | Todas las operaciones: agregar, editar, eliminar registros; gestionar usuarios y ajustes; importar/exportar datos |
| **Guardia** | Solo puede operar el scanner; visualizar listas y detalles |

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

## Stack Tecnológico

| Componente | Tecnología | Versión |
|------------|-----------|---------|
| Lenguaje | Java | 17+ |
| UI | JavaFX | 21.0.1 |
| Base de datos | SQLite (sqlite-jdbc) | 3.44.1.0 |
| Códigos de barras | ZXing | 3.5.2 |
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

La base de datos `registro_estudiantes.db` se crea automáticamente en el directorio de trabajo al primer inicio.

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

SQLite local, archivo `registro_estudiantes.db`. Tablas principales:

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

---

## Archivos de Prueba

El repositorio incluye archivos CSV de ejemplo:

- `datos_estudiantes_prueba.csv` — Estudiantes de muestra para importar
- `carnés_prueba.csv` — Carnés de visitante de muestra

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
