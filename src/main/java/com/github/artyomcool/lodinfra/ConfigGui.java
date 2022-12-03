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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;

public class ConfigGui extends Application {

    private final Properties properties;

    public ConfigGui(Properties properties) {
        this.properties = properties;
    }

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
        game.setText(properties.getProperty("gameDir"));
        res.setText(properties.getProperty("resDir"));
        dropbox.setText(Path.of(properties.getProperty("self")).getParent().toString());

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

        generate.setOnAction(e -> {
            String[] fileList = properties.getProperty("fileList").split(";");
            for (String file : fileList) {
                String[] split = file.split(":");
                String templateFile = split[0];
                String resultFile = split[1];

                properties.setProperty("userName", name.getText());
                properties.setProperty("gameDir", game.getText());
                properties.setProperty("resDir", res.getText());
                properties.setProperty("dropboxDir", dropbox.getText());

                Path in = Path.of(dropbox.getText(), templateFile);
                Path out = Path.of(res.getText(), resultFile);

                try {
                    String template = Files.readString(in);
                    StringBuilder result = new StringBuilder();
                    int from = 0;
                    while (true) {
                        int next = template.indexOf("${", from);
                        if (next == -1) {
                            result.append(template, from, template.length());
                            break;
                        }
                        result.append(template, from, next);

                        int nextFrom = template.indexOf("}", next);
                        String property = template.substring(next + 2, nextFrom);
                        String str = properties.getProperty(property, "").replaceAll("\\\\", Matcher.quoteReplacement("\\\\"));
                        result.append(str);

                        from = nextFrom + 1;
                    }

                    Files.writeString(out, result);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        });
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