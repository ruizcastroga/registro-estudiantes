package com.tuempresa.registro.utils;

import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;

import java.util.Optional;

/**
 * Utility methods for showing JavaFX dialogs that correctly size themselves
 * to fit their content — no truncation, scrollbar for long text.
 */
public final class DialogUtils {

    private DialogUtils() {}

    /**
     * Standard alert with auto-sizing. Prevents content truncation for
     * short and medium-length messages.
     *
     * @return the button the user clicked
     */
    public static Optional<ButtonType> alert(Alert.AlertType type,
                                             String title,
                                             String header,
                                             String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.getDialogPane().setMinWidth(460);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        return alert.showAndWait();
    }

    /**
     * Scrollable dialog for long content such as help text or import results
     * that may contain many error lines. Shows a ScrollPane so no text is cut off.
     */
    public static void scrollable(Alert.AlertType type,
                                  String title,
                                  String header,
                                  String content) {
        Label label = new Label(content);
        label.setWrapText(true);
        label.setPadding(new Insets(4, 8, 4, 4));
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-text-fill: #212121; -fx-font-size: 13px;");

        ScrollPane scrollPane = new ScrollPane(label);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(380);
        scrollPane.setPrefWidth(540);
        scrollPane.setStyle(
            "-fx-background-color: white; " +
            "-fx-background: white; " +
            "-fx-border-color: transparent;"
        );

        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.getDialogPane().setContent(scrollPane);
        alert.getDialogPane().setPrefWidth(580);
        alert.showAndWait();
    }
}
