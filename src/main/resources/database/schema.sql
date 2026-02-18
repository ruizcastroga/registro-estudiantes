-- ============================================
-- Schema de Base de Datos - Sistema de Registro de Estudiantes
-- Versión: 1.0.0
-- Base de datos: SQLite
-- ============================================

-- Tabla de estudiantes
-- Almacena la información principal de cada estudiante
CREATE TABLE IF NOT EXISTS students (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    barcode TEXT UNIQUE NOT NULL,           -- Código de barras único del carné
    first_name TEXT NOT NULL,               -- Nombre(s) del estudiante
    last_name TEXT NOT NULL,                -- Apellido(s) del estudiante
    grade TEXT,                             -- Grado/Curso (ej: "5to Primaria")
    is_minor INTEGER DEFAULT 1,             -- 1 = menor de edad, 0 = mayor de edad
    requires_guardian INTEGER DEFAULT 1,    -- 1 = requiere acompañante, 0 = puede salir solo
    photo_path TEXT,                        -- Ruta a la foto del estudiante (opcional)
    status TEXT DEFAULT 'active',           -- Estado: 'active', 'inactive', 'suspended'
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Índice para búsqueda rápida por código de barras
CREATE INDEX IF NOT EXISTS idx_students_barcode ON students(barcode);

-- Índice para búsqueda por nombre
CREATE INDEX IF NOT EXISTS idx_students_name ON students(last_name, first_name);

-- Índice para filtrar por estado
CREATE INDEX IF NOT EXISTS idx_students_status ON students(status);


-- Tabla de acudientes/tutores autorizados
-- Almacena las personas autorizadas para recoger a cada estudiante
CREATE TABLE IF NOT EXISTS guardians (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id INTEGER NOT NULL,            -- Referencia al estudiante
    name TEXT NOT NULL,                     -- Nombre completo del acudiente
    relationship TEXT,                      -- Relación: 'padre', 'madre', 'abuelo', etc.
    id_number TEXT,                         -- Número de documento de identidad
    phone TEXT,                             -- Teléfono de contacto
    authorized INTEGER DEFAULT 1,           -- 1 = autorizado, 0 = no autorizado
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
);

-- Índice para búsqueda por estudiante
CREATE INDEX IF NOT EXISTS idx_guardians_student ON guardians(student_id);

-- Índice para búsqueda por documento
CREATE INDEX IF NOT EXISTS idx_guardians_id_number ON guardians(id_number);


-- Tabla de registro de entradas/salidas
-- Almacena el historial de escaneos y movimientos
CREATE TABLE IF NOT EXISTS entry_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id INTEGER NOT NULL,            -- Referencia al estudiante
    guardian_id INTEGER,                    -- Referencia al acudiente (si aplica)
    entry_time DATETIME DEFAULT CURRENT_TIMESTAMP,  -- Hora de entrada/escaneo
    exit_time DATETIME,                     -- Hora de salida (si se registra)
    log_type TEXT DEFAULT 'exit',           -- Tipo: 'entry', 'exit'
    scanned_by TEXT,                        -- Usuario/guardia que escaneó
    notes TEXT,                             -- Notas adicionales
    FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
    FOREIGN KEY (guardian_id) REFERENCES guardians(id) ON DELETE SET NULL
);

-- Índice para búsqueda por estudiante
CREATE INDEX IF NOT EXISTS idx_entry_logs_student ON entry_logs(student_id);

-- Índice para búsqueda por fecha
CREATE INDEX IF NOT EXISTS idx_entry_logs_entry_time ON entry_logs(entry_time);

-- Índice para búsqueda por tipo de log
CREATE INDEX IF NOT EXISTS idx_entry_logs_type ON entry_logs(log_type);


-- Tabla de información de licencia
-- Almacena los datos de activación y licencia del software
CREATE TABLE IF NOT EXISTS license_info (
    id INTEGER PRIMARY KEY,
    license_key TEXT,                       -- Clave de licencia encriptada
    machine_id TEXT,                        -- ID único de la máquina
    school_name TEXT,                       -- Nombre del colegio/institución
    activated_at DATETIME,                  -- Fecha de activación
    expires_at DATETIME,                    -- Fecha de expiración
    is_active INTEGER DEFAULT 1,            -- 1 = activa, 0 = inactiva
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);


-- Tabla de configuración de la aplicación
-- Almacena configuraciones generales
CREATE TABLE IF NOT EXISTS app_config (
    key TEXT PRIMARY KEY,
    value TEXT,
    description TEXT,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Insertar configuraciones por defecto
INSERT OR IGNORE INTO app_config (key, value, description) VALUES
    ('school_name', 'Mi Colegio', 'Nombre del colegio'),
    ('max_history_display', '5', 'Cantidad máxima de registros en historial'),
    ('auto_clear_seconds', '10', 'Segundos para limpiar pantalla automáticamente'),
    ('sound_enabled', 'true', 'Habilitar sonidos de alerta'),
    ('default_guard_name', 'Guardia', 'Nombre por defecto del guardia');


-- Nota: No se incluyen datos de prueba.
-- Usar el importador CSV para cargar estudiantes.
