package com.github.artyomcool.lodinfra;

import com.sun.javafx.stage.StageHelper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class Pack {

    public static void main(String[] args) throws IOException {
        try {
            Path self = Path.of(".").normalize().toAbsolutePath();
            if (args.length == 0) {
                execConfig(self);
            } else if (args[0].equals("-unpack")) {
                execUnpack(self, args[1], args[2], args[3], args[4], args[5]);
            } else if (args[0].equals("-gui")) {
                execGui(self, args);
            } else {
                execPack(self, args);
            }
        } catch (Exception e) {
            Console c = System.console();
            if (c != null) {
                c.format("Unexpected error");
                e.printStackTrace(c.writer());
                c.readLine();
            } else {
                System.err.println("Unexpected error");
                e.printStackTrace();
                //noinspection ResultOfMethodCallIgnored
                System.in.read();
            }
        }
    }

    private static void execConfig(Path self) {
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.subpixeltext", "false");
        Application.launch(ConfigGui.class);
    }

    private static void execPack(Path self, String... args) throws DataFormatException, IOException {
        Properties properties = getArguments(self, args);

        String pathPattern = properties.getProperty("outputPattern");

        DirectoryResourceCollector collector = new DirectoryResourceCollector(self, pathPattern);
        collector.dry = Boolean.parseBoolean(properties.getProperty("dryRun", "false"));
        collector.logDetailedDiff = Boolean.parseBoolean(properties.getProperty("logDetailedDiff", "false"));
        boolean checkTimeStamps = Boolean.parseBoolean(properties.getProperty("checkTimestamps", "false"));
        collector.compressionLevel = Integer.parseInt(properties.getProperty("compressionLevel", "0"));
        String allowedLangs = properties.getProperty("allowedLangs", "").toLowerCase();
        String dontWarnAboutNames = properties.getProperty("dontWarnAboutNames", "").toLowerCase();

        Path timestampFile = self.resolve("lastTs");
        Instant now = Instant.now();
        if (checkTimeStamps) {
            collector.ignoreBeforeTimestamp = loadTimestamp(timestampFile, allowedLangs);
        }
        collector.allowedLangs = new HashSet<>(Arrays.asList(allowedLangs.split(",")));
        collector.dontWarnAboutNames = new HashSet<>(Arrays.asList(dontWarnAboutNames.split(",")));

        collector.collectResources();

        if (checkTimeStamps) {
            storeTimestamp(timestampFile, now, allowedLangs);
        }

        bye();
    }

    private static Instant loadTimestamp(Path file, String allowedLangs) {
        try {
            String text = Files.readString(file);
            Properties properties = new Properties();
            properties.load(new StringReader(text));
            String prevIgnoreLangs = properties.getProperty("allowedLangs");
            if (prevIgnoreLangs != null && prevIgnoreLangs.equals(allowedLangs)) {
                return Instant.parse(properties.getProperty("ts"));
            } else {
                System.out.println("No incremental state: language changed");
            }
        } catch (Exception ignored) {
            System.out.println("No valid incremental state");
        }
        return Instant.MIN;
    }

    private static void storeTimestamp(Path file, Instant now, String allowedLangs) throws IOException {
        Properties properties = new Properties();
        properties.put("allowedLangs", allowedLangs);
        properties.put("ts", now.toString());
        StringWriter writer = new StringWriter();
        properties.store(writer, "State of incremental work");
        Files.writeString(file, writer.toString());
    }

    private static void bye() {
        Console c = System.console();
        if (c != null) {
            c.format("Done, Press Enter to pay respect");
            c.readLine();
        }
    }

    private static Properties getArguments(Path self, String... args) throws IOException {
        Properties properties = loadProperties(self);
        for (String arg : args) {
            if (!arg.startsWith("-P")) {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
            String[] argToValue = arg.split("[=:]");
            properties.setProperty(argToValue[0].substring(2), argToValue.length > 1 ? argToValue[1] : "");
        }
        return properties;
    }

    private static Properties loadProperties(Path self) throws IOException {
        for (String configName : Arrays.asList("dev.config", "release.config")) {
            Properties properties = readProperties(self, configName);
            if (properties == null) {
                continue;
            }
            String validationFileLocation = properties.getProperty("validationFileLocation");
            if (validationFileLocation == null) {
                throw new IOException("Config is broken: no validationFileLocation defined");
            }
            for (String file : validationFileLocation.split("\\|")) {
                if (Files.exists(Path.of(file))) {
                    System.out.println("Config with suffix " + configName + " is suitable and will be used");
                    return properties;
                }
            }
            System.out.println("Config with suffix " + configName + " is not suitable");
        }
        System.err.println("No valid config found");
        return new Properties();
    }

    private static Properties readProperties(Path self, String configName) {
        Properties properties = new Properties();
        try {
            try (BufferedReader stream = Files.newBufferedReader(self.resolve(configName))) {
                properties.load(stream);
            }
        } catch (IOException e) {
            System.err.println("No config file with name " + configName);
            return null;
        }

        return properties;
    }

    private static void execUnpack(Path self, String pathPattern, String supportedLangs, String supportedLods, String pcxFormat, String dryRun) throws IOException, DataFormatException {
        String[] langs = supportedLangs.split(";");
        String[] lods = supportedLods.split(";");

        for (String lang : langs) {
            for (String lod : lods) {
                Path path = Utils.resolveTemplate(pathPattern, lang, lod);
                if (!Files.exists(path)) {
                    continue;
                }

                LodFile lodFile = LodFile.load(path);
                for (LodFile.SubFileMeta subFile : lodFile.subFiles) {
                    String name = new String(subFile.name).trim();
                    Path toStore = self.resolve(Path.of(lang + "@" + lod, name));
                    if (Boolean.parseBoolean(dryRun)) {
                        continue;
                    }

                    byte[] data;

                    Files.createDirectories(toStore.getParent());
                    if (subFile.compressedSize == 0) {
                        data = Arrays.copyOfRange(
                                lodFile.originalData,
                                subFile.globalOffsetInFile,
                                subFile.globalOffsetInFile + subFile.uncompressedSize
                        );
                    } else {
                        byte[] uncompressed = new byte[subFile.uncompressedSize];
                        Inflater inflater = new Inflater();
                        inflater.setInput(
                                lodFile.originalData,
                                subFile.globalOffsetInFile,
                                subFile.compressedSize
                        );
                        inflater.inflate(uncompressed);
                        inflater.end();

                        data = uncompressed;

                    }

                    if (name.toLowerCase().endsWith(".pcx")) {
                        if (data[0] == 0x50 && data[1] == 0x33 && data[2] == 0x32 && data[3] == 0x46){

                        } else {
                            ImageData png = "bmp".equals(pcxFormat) ? ResourceConverter.toBmp(data) : ResourceConverter.toPng(data);
                            toStore = toStore.resolveSibling(name + (png.hasPalette ? ".idx." : ".") + pcxFormat);
                            data = png.data;
                        }
                    }

                    Files.write(
                            toStore,
                            data,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.TRUNCATE_EXISTING
                    );
                }
            }
        }

        bye();
    }

    private static void execGui(Path self, String... args) throws Exception {
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.subpixeltext", "false");

        Properties arguments = getArguments(self, Arrays.copyOfRange(args, 1, args.length));

        AtomicReference<Throwable> error = new AtomicReference<>();

        CountDownLatch platform = new CountDownLatch(1);
        Platform.startup(platform::countDown);
        platform.await();

        String out = arguments.getProperty("gui_out");

        Gui gui = new Gui(self.resolve(out));
        gui.init();

        CountDownLatch guiStarted = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                gui.start(new Stage());
            } catch (Throwable t) {
                error.set(t);
            }
            guiStarted.countDown();
        });

        guiStarted.await();
        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
    }

}
