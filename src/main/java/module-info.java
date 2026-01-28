/**
 * Módulo principal del Sistema de Registro de Estudiantes.
 * Define las dependencias y exportaciones necesarias para JavaFX.
 */
module com.tuempresa.registro {
    // Dependencias de JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // Dependencia de SQLite JDBC
    requires java.sql;
    requires org.xerial.sqlitejdbc;

    // Dependencias de logging
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;

    // Abrir paquetes para reflexión de JavaFX FXML
    opens com.tuempresa.registro to javafx.fxml;
    opens com.tuempresa.registro.controllers to javafx.fxml;
    opens com.tuempresa.registro.models to javafx.base;

    // Exportar paquetes públicos
    exports com.tuempresa.registro;
    exports com.tuempresa.registro.controllers;
    exports com.tuempresa.registro.models;
    exports com.tuempresa.registro.services;
    exports com.tuempresa.registro.dao;
    exports com.tuempresa.registro.utils;
}
