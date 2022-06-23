package com.github.artyomcool.lodinfra;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class ConfigGui extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        VBox root = new VBox();
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/theme.css").toExternalForm());

        TextField name = new TextField();
        TextField game = new TextField();
        TextField res = new TextField();
        TextField dropbox = new TextField();

        name.setText(System.getProperty("user.name"));
        game.setText("C:\\Games\\HotA");
        res.setText("C:\\Games\\HotA_Resources");
        dropbox.setText(Path.of(".").toFile().getAbsolutePath());

        root.getChildren().addAll(
                withLabel(name, "Ваш ник / Your nick"),
                pathSelector(game, primaryStage, "Папка игры / Game dir"),
                pathSelector(res, primaryStage, "Папка с ресурсами для работы / Resources in progress dir"),
                pathSelector(dropbox, primaryStage, "Папка с ресурсами в дропбоксе / Resources in dropbox dir")
        );

        Button generate = new Button("Сгенерировать исполняемые файлы / Generate executables");
        root.getChildren().add(generate);

        root.setPadding(new Insets(4));

        primaryStage.setTitle("Конфигурация / Config");
        primaryStage.setScene(scene);
        primaryStage.sizeToScene();
        primaryStage.show();
    }

    private Pane pathSelector(TextField field, Stage primaryStage, String name) {
        Button button = new Button("...");
        button.setOnAction(event -> {
            DirectoryChooser fileChooser = new DirectoryChooser();
            fileChooser.setTitle("Open Resource File");
            File file = fileChooser.showDialog(primaryStage);
            if (file != null) {
                field.setText(file.getAbsolutePath());
            }
        });
        HBox fieldWithButton = new HBox(field, button);
        HBox.setHgrow(field, Priority.ALWAYS);
        HBox.setMargin(field, new Insets(0, 4, 0, 0));
        return withLabel(fieldWithButton, name);
    }

    private Pane withLabel(Node pane, String label) {
        Pane result = new VBox(new Label(label), pane);
        result.setPadding(new Insets(4));
        return result;
    }

}