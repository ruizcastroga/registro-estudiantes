package com.tuempresa.registro.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Enumeration;

/**
 * Gestor de licencias basado en Hardware ID.
 * Genera y valida licencias offline usando el ID único de la máquina.
 *
 * NOTA: Esta es una implementación básica para demostración.
 * En producción, se recomienda usar técnicas de ofuscación adicionales
 * y almacenar la clave secreta de forma segura.
 */
public class LicenseManager {

    private static final Logger logger = LoggerFactory.getLogger(LicenseManager.class);

    // Clave secreta para la generación de licencias (cambiar en producción)
    // IMPORTANTE: Esta clave debería estar ofuscada o almacenada de forma segura
    private static final String SECRET_KEY = "TuClaveSecretaMuySegura2024!@#$";

    // Prefijo de las licencias
    private static final String LICENSE_PREFIX = "REG-STU-";

    // Formato de fecha para licencias
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    // Instancia singleton
    private static LicenseManager instance;

    // Cache del machine ID
    private String cachedMachineId;

    /**
     * Constructor privado para singleton.
     */
    private LicenseManager() {
        // Constructor privado
    }

    /**
     * Obtiene la instancia única del LicenseManager.
     *
     * @return Instancia de LicenseManager
     */
    public static synchronized LicenseManager getInstance() {
        if (instance == null) {
            instance = new LicenseManager();
        }
        return instance;
    }

    /**
     * Genera un ID único de la máquina basado en MAC address y hostname.
     * Este ID se usa para vincular la licencia a una máquina específica.
     *
     * @return ID único de la máquina
     */
    public String getMachineId() {
        if (cachedMachineId != null) {
            return cachedMachineId;
        }

        try {
            StringBuilder machineInfo = new StringBuilder();

            // Obtener hostname
            String hostname = InetAddress.getLocalHost().getHostName();
            machineInfo.append(hostname);

            // Obtener MAC address de la primera interfaz de red válida
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();

                // Ignorar interfaces loopback y virtuales
                if (networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }

                byte[] mac = networkInterface.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    for (byte b : mac) {
                        machineInfo.append(String.format("%02X", b));
                    }
                    break; // Usar solo la primera MAC válida
                }
            }

            // Generar hash del machine ID
            cachedMachineId = hashString(machineInfo.toString()).substring(0, 16).toUpperCase();
            logger.debug("Machine ID generado: {}", cachedMachineId);
            return cachedMachineId;

        } catch (Exception e) {
            logger.error("Error al generar Machine ID", e);
            // Fallback a un ID basado en propiedades del sistema
            String fallback = System.getProperty("user.name") +
                    System.getProperty("os.name") +
                    System.getProperty("os.arch");
            cachedMachineId = hashString(fallback).substring(0, 16).toUpperCase();
            return cachedMachineId;
        }
    }

    /**
     * Genera una licencia para una máquina y nombre de colegio específicos.
     * NOTA: Este método es solo para uso del desarrollador/administrador.
     *
     * @param machineId  ID de la máquina
     * @param schoolName Nombre del colegio
     * @return Clave de licencia generada
     */
    public String generateLicense(String machineId, String schoolName) {
        try {
            // Crear string base para la licencia
            String baseString = machineId + "|" + schoolName + "|" + SECRET_KEY;

            // Generar hash
            String hash = hashString(baseString);

            // Formatear como licencia legible
            String license = LICENSE_PREFIX + formatLicenseKey(hash.substring(0, 24));

            logger.info("Licencia generada para: {} - {}", schoolName, machineId);
            return license;

        } catch (Exception e) {
            logger.error("Error al generar licencia", e);
            return null;
        }
    }

    /**
     * Genera una licencia con fecha de expiración.
     *
     * @param machineId   ID de la máquina
     * @param schoolName  Nombre del colegio
     * @param expiresAt   Fecha de expiración
     * @return Clave de licencia generada
     */
    public String generateLicenseWithExpiry(String machineId, String schoolName,
                                             LocalDateTime expiresAt) {
        try {
            String expiryDate = expiresAt.format(DATE_FORMATTER);
            String baseString = machineId + "|" + schoolName + "|" + expiryDate + "|" + SECRET_KEY;
            String hash = hashString(baseString);

            // Incluir fecha de expiración en la licencia (codificada)
            String license = LICENSE_PREFIX + expiryDate.substring(2) + "-" +
                    formatLicenseKey(hash.substring(0, 20));

            logger.info("Licencia con expiración generada para: {} (expira: {})",
                    schoolName, expiresAt);
            return license;

        } catch (Exception e) {
            logger.error("Error al generar licencia con expiración", e);
            return null;
        }
    }

    /**
     * Valida una licencia contra el machine ID y nombre del colegio.
     *
     * @param license    Licencia a validar
     * @param schoolName Nombre del colegio
     * @return true si la licencia es válida
     */
    public boolean validateLicense(String license, String schoolName) {
        if (license == null || license.isEmpty()) {
            logger.warn("Licencia vacía o nula");
            return false;
        }

        try {
            String machineId = getMachineId();

            // Verificar formato básico
            if (!license.startsWith(LICENSE_PREFIX)) {
                logger.warn("Formato de licencia inválido");
                return false;
            }

            // Generar la licencia esperada y comparar
            String expectedLicense = generateLicense(machineId, schoolName);

            if (license.equals(expectedLicense)) {
                logger.info("Licencia válida para: {}", schoolName);
                return true;
            }

            // Intentar validar como licencia con expiración
            if (validateLicenseWithExpiry(license, schoolName)) {
                return true;
            }

            logger.warn("Licencia inválida: no coincide con la máquina o colegio");
            return false;

        } catch (Exception e) {
            logger.error("Error al validar licencia", e);
            return false;
        }
    }

    /**
     * Valida una licencia con fecha de expiración.
     *
     * @param license    Licencia a validar
     * @param schoolName Nombre del colegio
     * @return true si la licencia es válida y no ha expirado
     */
    private boolean validateLicenseWithExpiry(String license, String schoolName) {
        try {
            // Extraer fecha de la licencia (formato: REG-STU-YYMMDD-XXXX-XXXX-XXXX)
            String content = license.substring(LICENSE_PREFIX.length());
            if (content.length() < 7 || content.charAt(6) != '-') {
                return false;
            }

            String dateStr = "20" + content.substring(0, 6); // Agregar siglo
            LocalDateTime expiryDate = LocalDateTime.parse(dateStr + "T23:59:59");

            // Verificar si ha expirado
            if (LocalDateTime.now().isAfter(expiryDate)) {
                logger.warn("Licencia expirada: {}", expiryDate);
                return false;
            }

            // Regenerar y comparar
            String expectedLicense = generateLicenseWithExpiry(getMachineId(), schoolName, expiryDate);
            return license.equals(expectedLicense);

        } catch (Exception e) {
            logger.debug("No es una licencia con expiración válida");
            return false;
        }
    }

    /**
     * Verifica si el sistema está en modo demo/trial.
     * El modo demo permite usar la aplicación sin licencia con funcionalidad limitada.
     *
     * @return true si está en modo demo
     */
    public boolean isDemoMode() {
        // Por ahora, siempre permitimos modo demo
        // En producción, esto podría verificar días de prueba, etc.
        return true;
    }

    /**
     * Obtiene los días restantes del período de prueba.
     *
     * @return Días restantes (-1 si no hay límite en modo demo)
     */
    public int getDemoTrialDaysRemaining() {
        // En modo demo completo, no hay límite
        return -1;
    }

    /**
     * Genera un hash SHA-256 de un string.
     *
     * @param input String a hashear
     * @return Hash en formato hexadecimal
     */
    private String hashString(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convertir a hexadecimal
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();

        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 no disponible", e);
            // Fallback simple (no seguro, solo para emergencias)
            return Base64.getEncoder().encodeToString(input.getBytes()).toUpperCase();
        }
    }

    /**
     * Formatea una clave de licencia en grupos de 4 caracteres.
     *
     * @param key Clave sin formato
     * @return Clave formateada (XXXX-XXXX-XXXX-...)
     */
    private String formatLicenseKey(String key) {
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                formatted.append('-');
            }
            formatted.append(key.charAt(i));
        }
        return formatted.toString();
    }

    /**
     * Información de licencia para mostrar al usuario.
     */
    public static class LicenseInfo {
        private final boolean valid;
        private final boolean demo;
        private final String schoolName;
        private final LocalDateTime expiresAt;
        private final String machineId;

        public LicenseInfo(boolean valid, boolean demo, String schoolName,
                          LocalDateTime expiresAt, String machineId) {
            this.valid = valid;
            this.demo = demo;
            this.schoolName = schoolName;
            this.expiresAt = expiresAt;
            this.machineId = machineId;
        }

        public boolean isValid() { return valid; }
        public boolean isDemo() { return demo; }
        public String getSchoolName() { return schoolName; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public String getMachineId() { return machineId; }
    }

    /**
     * Obtiene la información completa de la licencia actual.
     *
     * @param storedLicense Licencia almacenada
     * @param schoolName    Nombre del colegio
     * @return Información de la licencia
     */
    public LicenseInfo getLicenseInfo(String storedLicense, String schoolName) {
        String machineId = getMachineId();
        boolean valid = validateLicense(storedLicense, schoolName);
        boolean demo = !valid && isDemoMode();

        return new LicenseInfo(valid, demo, schoolName, null, machineId);
    }
}
