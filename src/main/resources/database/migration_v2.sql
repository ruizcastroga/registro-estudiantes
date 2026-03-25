-- ============================================
-- Migración v2 - Sistema de Registro de Estudiantes
-- Nuevas tablas: staff, admin_users, activity_logs
-- Nuevas columnas: created_by, updated_by en todas las tablas
-- ============================================

-- Tabla de usuarios administradores
CREATE TABLE IF NOT EXISTS admin_users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'Administrador',  -- 'Administrador' o 'Operador'
    is_active INTEGER DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- Tabla de personal/staff
CREATE TABLE IF NOT EXISTS staff (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    barcode TEXT UNIQUE NOT NULL,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    id_number TEXT,
    department TEXT,
    status TEXT DEFAULT 'active',
    created_by TEXT,
    updated_by TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_staff_barcode ON staff(barcode);
CREATE INDEX IF NOT EXISTS idx_staff_name ON staff(last_name, first_name);
CREATE INDEX IF NOT EXISTS idx_staff_status ON staff(status);

-- Tabla unificada de logs de actividad (entrada/salida)
CREATE TABLE IF NOT EXISTS activity_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    person_type TEXT NOT NULL,           -- 'student', 'staff', 'visitor'
    person_id INTEGER NOT NULL,
    person_name TEXT,
    badge_code TEXT,
    log_type TEXT DEFAULT 'exit',        -- 'entry', 'exit'
    scanned_by TEXT,
    notes TEXT,
    created_by TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_activity_logs_person ON activity_logs(person_type, person_id);
CREATE INDEX IF NOT EXISTS idx_activity_logs_time ON activity_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_activity_logs_type ON activity_logs(log_type);

-- Agregar columnas created_by y updated_by a tablas existentes (SQLite ignora si ya existen)
-- Students
ALTER TABLE students ADD COLUMN created_by TEXT;
ALTER TABLE students ADD COLUMN updated_by TEXT;

-- Guardians
ALTER TABLE guardians ADD COLUMN created_by TEXT;
ALTER TABLE guardians ADD COLUMN updated_by TEXT;

-- Visitor badges
ALTER TABLE visitor_badges ADD COLUMN created_by TEXT;
ALTER TABLE visitor_badges ADD COLUMN updated_by TEXT;

-- Visitor logs
ALTER TABLE visitor_logs ADD COLUMN created_by TEXT;

-- Configuración por defecto para timeout de sesión
INSERT OR IGNORE INTO app_config (key, value, description) VALUES
    ('session_timeout_minutes', '15', 'Tiempo de vigencia de la sesión de administrador en minutos');

-- Versión de la base de datos
INSERT OR REPLACE INTO app_config (key, value, description, updated_at) VALUES
    ('db_version', '2', 'Versión del esquema de la base de datos', CURRENT_TIMESTAMP);
