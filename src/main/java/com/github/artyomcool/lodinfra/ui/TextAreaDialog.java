package com.github.artyomcool.lodinfra.ui;

import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class TextAreaDialog extends Dialog<ButtonType> {

    ButtonType push = new ButtonType("Push", ButtonBar.ButtonData.OK_DONE);
    private final TextArea textArea;

    public TextAreaDialog(String title, String text) {
        DialogPane dialogPane = getDialogPane();
        
        setTitle(title);

        dialogPane.getButtonTypes().setAll(push, ButtonType.CANCEL);


        textArea = new TextArea(text);
        textArea.setEditable(true);
        textArea.setWrapText(true);
        
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);
        
        GridPane root = new GridPane();
        root.setMaxWidth(Double.MAX_VALUE);
        root.add(textArea, 0, 0);
        
        dialogPane.setContent(root);
    }

    public String showForText() {
        if (showAndWait().orElse(ButtonType.CANCEL) != push) {
            return null;
        }

        return textArea.getText();
    }
}
