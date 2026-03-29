# REST API — Manual de Configuración y Pruebas

La API REST corre **embebida dentro de la aplicación** en el puerto `8080`. Se inicia automáticamente cuando abres el programa y se detiene cuando lo cierras. No requiere instalación extra.

---

## 1. Requisitos previos

| Herramienta | Para qué |
|-------------|----------|
| La aplicación corriendo (`mvn javafx:run`) | El servidor API solo funciona cuando la app está abierta |
| [Postman](https://www.postman.com/downloads/) | Probar endpoints desde una interfaz visual |
| `curl` (opcional) | Probar desde terminal |
| Python 3 (opcional) | El script de seed usa `python3 -m json.tool` para formatear |

---

## 2. Obtener tu API Key

1. Abre la aplicación e inicia sesión como **Administrador**
2. Ve a **Ajustes** → pestaña **API REST**
3. Copia la clave del campo **API Key**

> La clave se genera automáticamente la primera vez y se guarda en la base de datos. Si necesitas cambiarla, haz clic en **Regenerar** — la clave anterior deja de funcionar de inmediato.

**Forma alternativa — ver la clave en los logs al arrancar:**
```
API Key: a1b2c3d4-e5f6-7890-abcd-ef1234567890
```
El log aparece en la consola donde corriste `mvn javafx:run`.

---

## 3. Verificar que el servidor está activo

```bash
curl -s -H "X-API-Key: TU_CLAVE" http://localhost:8080/api/health
```

Respuesta esperada:
```json
{
  "status": "ok",
  "version": "1.0.0",
  "db": "connected",
  "port": 8080
}
```

Si recibes `Connection refused`, la aplicación no está corriendo o el puerto 8080 está ocupado por otro proceso.

---

## 4. Configurar Postman

### 4.1 Importar el environment

1. En Postman, haz clic en **Environments** (ícono de ojo en la barra lateral)
2. Clic en **Import**
3. Selecciona el archivo `postman/registro_api.postman_environment.json`
4. Una vez importado, haz clic en el environment y **edita la variable `api_key`** — pega tu clave del paso 2
5. Selecciona el environment en el menú desplegable superior derecho de Postman

### 4.2 Importar la colección

1. Clic en **Collections** → **Import**
2. Selecciona `postman/registro_api.postman_collection.json`
3. La colección aparece con estas carpetas:
   - **Health** — verificación del servidor
   - **Students** — export e import de estudiantes
   - **Staff** — export e import de personal
   - **Visitors** — carnés y logs de visitantes
   - **Entry Logs** — historial de escaneos del scanner
   - **Auth errors** — pruebas de seguridad (espera 401)

### 4.3 Verificar que el auth funciona

La colección tiene autenticación configurada a nivel de colección: envía automáticamente el header `X-API-Key: {{api_key}}` en todas las peticiones. No necesitas configurarlo request por request.

---

## 5. Cargar datos de prueba (Seed)

Los archivos en `scripts/mock_data/` contienen datos ficticios listos para importar. Úsalos para poblar una base de datos vacía antes de probar los exports.

### Linux / macOS
```bash
./scripts/seed_api.sh TU_CLAVE_AQUI
```

### Windows (PowerShell)
```powershell
.\scripts\seed_api.ps1 -ApiKey "TU_CLAVE_AQUI"
```

El script importa:
- **15 estudiantes** con diferentes grados, estados y configuraciones de acompañante
- **6 miembros del staff** de distintos departamentos
- **10 carnés de visitante** (VIS-API-01 a VIS-API-10)

Resultado esperado:
```
==> Checking server at http://localhost:8080...
    OK: Server is up

==> Importing Students from students.json...
    OK: imported=15  skipped=0

==> Importing Staff from staff.json...
    OK: imported=6  skipped=0

==> Importing Visitor Badges from visitor_badges.json...
    OK: imported=10  skipped=0
```

---

## 6. Plan de pruebas — qué ejecutar y qué esperar

### Paso 1 — Health check
**Request:** `GET /api/health`
**Esperado:** `200 OK` con `"status": "ok"` y `"db": "connected"`

---

### Paso 2 — Export vacío (antes del seed)
**Request:** `GET /api/students/export`
**Esperado:** `200 OK` con array vacío `[]` (si la BD está limpia)

---

### Paso 3 — Cargar datos de prueba
Ejecuta el script de seed (sección 5) o usa la request **"POST /api/students/import"** de la colección de Postman con el body de ejemplo.

---

### Paso 4 — Export con datos
**Request:** `GET /api/students/export`
**Esperado:** `200 OK` con array de 15 objetos. Verifica que cada uno tenga:
```json
{
  "barcode": "100000001",
  "firstName": "Valentina",
  "lastName": "Araya Brenes",
  "grade": "Kinder",
  "isMinor": true,
  "requiresGuardian": true,
  "status": "active"
}
```

---

### Paso 5 — Import de estudiantes
**Request:** `POST /api/students/import`
**Body:**
```json
[
  {
    "barcode": "TEST-001",
    "firstName": "Carlos",
    "lastName": "Prueba López",
    "grade": "5to Primaria",
    "isMinor": true,
    "requiresGuardian": true,
    "status": "active"
  }
]
```
**Esperado:**
```json
{ "imported": 1, "skipped": 0, "errors": [] }
```
Verifica en la app (módulo Estudiantes) que el nuevo registro apareció.

---

### Paso 6 — Import con duplicado
Ejecuta la request **"POST /api/students/import — duplicate"** de la colección.
**Esperado:** `imported: 0, skipped: 1` con mensaje de error indicando que el barcode ya existe.

---

### Paso 7 — Import con campos vacíos
Ejecuta la request **"POST /api/students/import — missing fields"**.
**Esperado:** `skipped: 1` con error `"'barcode' is required"`.

---

### Paso 8 — Staff export e import
Misma secuencia que estudiantes pero con `/api/staff/`. Verifica que el campo `idNumber` se usa como código de barras del carné.

---

### Paso 9 — Visitor badges
**Import:** `POST /api/visitors/badges/import` con los códigos de prueba
**Export:** `GET /api/visitors/badges/export`
Verifica que los carnés aparecen en el módulo de Visitantes con estado "Disponible".

---

### Paso 10 — Visitor logs export
**Request:** `GET /api/visitors/logs/export?from=2026-01-01&to=2026-12-31`
**Esperado:** Array (vacío si no se han registrado visitas aún).

Para generar datos: escanea un carné de visitante en el Scanner principal de la app, luego vuelve a ejecutar la request.

---

### Paso 11 — Entry logs export
**Request:** `GET /api/entry-logs/export?from=2026-01-01&to=2026-12-31`
**Esperado:** Array con los escaneos del scanner en ese rango de fechas.

---

### Paso 12 — Pruebas de autenticación
Ejecuta las requests de la carpeta **"Auth errors"**:
- Sin header → `401 Unauthorized`
- Con header incorrecto → `401 Unauthorized`
- Con header correcto → `200 OK`

---

## 7. Referencia de endpoints

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/health` | Estado del servidor y la BD |
| GET | `/api/students/export` | Todos los estudiantes en JSON |
| POST | `/api/students/import` | Importar estudiantes desde JSON array |
| GET | `/api/staff/export` | Todo el personal en JSON |
| POST | `/api/staff/import` | Importar personal desde JSON array |
| GET | `/api/visitors/badges/export` | Todos los carnés de visitante |
| POST | `/api/visitors/badges/import` | Importar carnés (solo campo `code`) |
| GET | `/api/visitors/logs/export` | Historial de visitas (parámetros `from`/`to`) |
| GET | `/api/entry-logs/export` | Historial del scanner (parámetros `from`/`to`) |

### Parámetros de fecha
Los endpoints de export con rango de fechas aceptan parámetros opcionales:
- `from=YYYY-MM-DD` — fecha de inicio (default: últimos 30 días)
- `to=YYYY-MM-DD` — fecha de fin (default: hoy)

Ejemplo: `/api/visitors/logs/export?from=2026-03-01&to=2026-03-31`

### Autenticación
Todos los endpoints requieren el header:
```
X-API-Key: tu-clave-aqui
```
Sin la clave → `401 Unauthorized`

---

## 8. Estructura de los cuerpos JSON

### Estudiante (import)
```json
{
  "barcode":          "123456789",    // requerido — código único del carné
  "firstName":        "Juan",         // requerido
  "lastName":         "García López", // requerido
  "grade":            "5to Primaria", // opcional
  "isMinor":          true,           // opcional (default: true)
  "requiresGuardian": true,           // opcional (default: true)
  "status":           "active"        // opcional: "active" | "inactive" | "suspended"
}
```

### Personal (import)
```json
{
  "idNumber":   "201980001",     // requerido — cédula (se usa como barcode del carné)
  "firstName":  "Ana",          // requerido
  "lastName":   "Villalobos",   // requerido
  "department": "Docencia",     // opcional
  "status":     "active"        // opcional: "active" | "inactive" | "suspended"
}
```

### Carné de visitante (import)
```json
{
  "code": "VIS-01"    // requerido — código único del carné físico
}
```

### Respuesta de import
```json
{
  "imported": 14,
  "skipped":  1,
  "errors":   ["Row 3: barcode '999' already exists"]
}
```

---

## 9. Solución de problemas

| Problema | Causa probable | Solución |
|----------|---------------|----------|
| `Connection refused` | La app no está corriendo | Ejecuta `mvn javafx:run` |
| `401 Unauthorized` | API key incorrecta o faltante | Copia la clave desde Ajustes → API REST |
| `404 Not found` | URL mal escrita | Revisa la tabla de endpoints |
| `400 Bad Request` | JSON inválido o campos faltantes | Verifica el cuerpo del request |
| Puerto 8080 ocupado | Otro proceso usa ese puerto | Cierra el proceso o cambia el puerto |

### Verificar puerto en uso (Linux/macOS)
```bash
lsof -i :8080
# o
ss -tlnp | grep 8080
```

### Verificar puerto en uso (Windows)
```powershell
netstat -ano | findstr :8080
```
