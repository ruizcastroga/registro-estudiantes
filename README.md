# Sistema de Registro de Estudiantes

Sistema de escritorio para el registro y control de salida de estudiantes en colegios. Permite escanear carnés de estudiantes mediante un lector de códigos de barras y validar si pueden salir solos o requieren acompañante autorizado.

## Características

- **Escaneo de carnés**: Compatible con lectores de códigos de barras (ej: 3nStar SC310)
- **Validación de salida**: Verifica si el estudiante puede salir solo o requiere acompañante
- **Gestión de estudiantes**: CRUD completo para administrar estudiantes
- **Gestión de acudientes**: Registro de personas autorizadas para recoger estudiantes
- **Historial de escaneos**: Registro de todas las entradas/salidas
- **Sistema de licencias**: Licencias offline basadas en Hardware ID
- **Modo demo**: Permite usar la aplicación sin licencia para evaluación

## Stack Tecnológico

- **Java 17+**
- **JavaFX 21** - Interfaz gráfica
- **Maven** - Gestión de dependencias y build
- **SQLite** - Base de datos local
- **SLF4J + Logback** - Logging

## Requisitos

- JDK 17 o superior
- Maven 3.8+
- Lector de códigos de barras (opcional, funciona también con teclado)

## Instalación

1. Clonar el repositorio:
```bash
git clone <url-del-repositorio>
cd registro-estudiantes
```

2. Compilar el proyecto:
```bash
mvn clean compile
```

3. Ejecutar la aplicación:
```bash
mvn javafx:run
```

## Estructura del Proyecto

```
src/main/java/com/tuempresa/registro/
├── Main.java                    # Punto de entrada de la aplicación
├── controllers/                 # Controladores JavaFX
│   ├── ScannerController.java   # Controlador de la vista del scanner
│   └── StudentCRUDController.java # Controlador de gestión de estudiantes
├── dao/                         # Data Access Objects
│   ├── DatabaseConnection.java  # Conexión a SQLite
│   ├── StudentDAO.java          # DAO de estudiantes
│   ├── GuardianDAO.java         # DAO de acudientes
│   └── EntryLogDAO.java         # DAO de registros de entrada/salida
├── models/                      # Modelos de datos
│   ├── Student.java             # Modelo de estudiante
│   ├── Guardian.java            # Modelo de acudiente
│   └── EntryLog.java            # Modelo de registro
├── services/                    # Capa de servicios
│   └── StudentService.java      # Lógica de negocio
└── utils/                       # Utilidades
    └── LicenseManager.java      # Gestión de licencias

src/main/resources/
├── fxml/                        # Vistas FXML
│   ├── scanner-view.fxml        # Vista principal del scanner
│   └── student-crud.fxml        # Vista de gestión de estudiantes
├── css/                         # Estilos
│   └── styles.css               # Estilos de la aplicación
├── database/                    # Base de datos
│   └── schema.sql               # Schema SQL
└── logback.xml                  # Configuración de logging
```

## Uso

### Pantalla Principal (Scanner)

1. El sistema se enfoca automáticamente en el campo de entrada
2. Escanee el carné del estudiante con el lector de códigos de barras
3. El sistema mostrará:
   - **Verde "PUEDE SALIR"**: El estudiante puede salir solo
   - **Naranja "REQUIERE ACOMPAÑANTE"**: El estudiante necesita un acudiente autorizado
4. Si el estudiante no está registrado, se ofrece la opción de registro rápido

### Gestión de Estudiantes

1. Click en "Gestionar Estudiantes" en la pantalla principal
2. Desde aquí puede:
   - Ver lista de todos los estudiantes
   - Buscar por nombre o código
   - Agregar nuevos estudiantes
   - Editar información existente
   - Eliminar estudiantes
   - Gestionar acudientes autorizados

## Base de Datos

La aplicación usa SQLite y crea automáticamente un archivo `registro_estudiantes.db` en el directorio de ejecución.

### Tablas Principales

- **students**: Información de estudiantes
- **guardians**: Acudientes autorizados
- **entry_logs**: Historial de escaneos
- **license_info**: Información de licencia

## Sistema de Licencias

El sistema utiliza licencias basadas en Hardware ID:

1. Al iniciar, se genera un Machine ID único basado en la MAC address
2. La licencia se vincula a este ID y al nombre del colegio
3. Sin licencia válida, la aplicación funciona en modo demo (sin restricciones)

Para obtener una licencia, contacte al proveedor con su Machine ID.

## Configuración de Logging

Los logs se escriben en:
- **Consola**: Nivel INFO y superior
- **logs/app.log**: Nivel DEBUG (rotación diaria, max 10MB)
- **logs/error.log**: Solo errores

## Desarrollo

### Ejecutar en modo desarrollo
```bash
mvn javafx:run
```

### Compilar JAR ejecutable
```bash
mvn clean package
```

### Ejecutar tests
```bash
mvn test
```

## Datos de Prueba

El sistema incluye datos de prueba para desarrollo:
- 5 estudiantes de ejemplo
- Acudientes asociados

Códigos de barras de prueba:
- `STU001` - Juan Carlos García López (requiere acompañante)
- `STU002` - María Fernanda Rodríguez Pérez (requiere acompañante)
- `STU003` - Pedro Antonio Martínez Silva (puede salir solo)
- `STU004` - Ana Sofía López Hernández (requiere acompañante)
- `STU005` - Carlos Eduardo Sánchez Mora (puede salir solo)

## Licencia

Todos los derechos reservados.

## Soporte

Para soporte técnico o reportar problemas, contacte al equipo de desarrollo.
