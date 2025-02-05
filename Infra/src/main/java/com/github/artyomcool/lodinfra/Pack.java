package com.github.artyomcool.lodinfra;

import com.github.artyomcool.lodinfra.h3common.Archive;
import com.github.artyomcool.lodinfra.h3common.LodFile;
import com.github.artyomcool.lodinfra.ui.DiffUi;
import com.github.artyomcool.lodinfra.ui.Gui;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.DataFormatException;

public class Pack {

    private static final String VERSION = "1.9";
    public static Application APP = null;

    public static void main(String[] a) throws IOException {
        System.out.println("Version: " + VERSION);
        try {
            String selfPath = ".";
            List<String> args = new ArrayList<>(Arrays.asList(a));
            for (Iterator<String> iterator = args.iterator(); iterator.hasNext(); ) {
                String arg = iterator.next();
                if (arg.startsWith("-W")) {
                    iterator.remove();
                    selfPath = arg.substring(2);
                    break;
                }
            }
            Path self = Path.of(selfPath).normalize().toAbsolutePath();
            switch (args.get(0)) {
                case "-cfg" -> execConfig(self, args.subList(1, args.size()));
                case "-unpack" -> execUnpack(self, args.get(1), args.get(2), args.get(3), args.get(4), args.get(5));
                case "-gui" -> execGui(self, args.subList(1, args.size()));
                case "-diff" -> execDiff(self, args.subList(1, args.size()));
                default -> execPack(self, args);
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

    private static void execConfig(Path self, List<String> args) throws InterruptedException {
        Properties properties = readProperties(self, "cfg.config", true);
        applyArgs(args, properties);

        ConfigGui gui = new ConfigGui(properties);

        run(gui);
    }

    private static void run(Application gui) throws InterruptedException {
        APP = gui;
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.subpixeltext", "false");
        CountDownLatch platform = new CountDownLatch(1);
        Platform.startup(platform::countDown);
        platform.await();

        AtomicReference<Throwable> error = new AtomicReference<>();
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

    private static void execPack(Path self, List<String> args) throws DataFormatException, IOException {
        Properties properties = getArguments(self, args);

        String pathPattern = properties.getProperty("outputPattern");

        DirectoryResourceCollector collector = new DirectoryResourceCollector(self, pathPattern);
        collector.logPath = properties.getProperty("logsPath", "logs");
        collector.resPath = properties.getProperty("resPath", ".");
        collector.dry = Boolean.parseBoolean(properties.getProperty("dryRun", "false"));
        collector.logDetailedDiff = Boolean.parseBoolean(properties.getProperty("logDetailedDiff", "false"));
        boolean checkTimeStamps = Boolean.parseBoolean(properties.getProperty("checkTimestamps", "false"));
        collector.compressionLevel = Integer.parseInt(properties.getProperty("compressionLevel", "0"));
        String allowedLangs = properties.getProperty("allowedLangs", "").toLowerCase();
        String dontWarnAboutNames = properties.getProperty("dontWarnAboutNames", "").toLowerCase();

        Path timestampFile = self.resolve("lastTs");
        if (checkTimeStamps) {
            collector.previouslyModifiedAt = loadTimestamp(timestampFile, allowedLangs);
        }
        collector.allowedLangs = new HashSet<>(Arrays.asList(allowedLangs.split(",")));
        collector.dontWarnAboutNames = new HashSet<>(Arrays.asList(dontWarnAboutNames.split(",")));

        collector.collectResources();

        if (checkTimeStamps) {
            storeTimestamp(timestampFile, collector.nowModifiedAt, allowedLangs);
        }

        if (Boolean.parseBoolean(properties.getProperty("showBye", "true"))) {
            bye();
        }
    }

    private static Map<String, String> loadTimestamp(Path file, String allowedLangs) {
        try {
            if (!Files.exists(file)) {
                System.out.println("No incremental state file");
                return Collections.emptyMap();
            }
            String text = Files.readString(file);
            Properties properties = new Properties();
            properties.load(new StringReader(text));
            String prevIgnoreLangs = properties.getProperty("*allowedLangs");
            if (prevIgnoreLangs != null && prevIgnoreLangs.equals(allowedLangs)) {
                Map<String, String> map = new TreeMap<>();
                for (String name : properties.stringPropertyNames()) {
                    if (name.startsWith("*")) {
                        continue;
                    }
                    map.put(name, properties.getProperty(name));
                }
                return map;
            } else {
                System.out.println("No incremental state: language changed");
                return Collections.emptyMap();
            }
        } catch (Exception e) {
            System.out.println("Incremental state invalid");
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    private static void storeTimestamp(Path file, Map<String, String> now, String allowedLangs) throws IOException {
        Properties properties = new Properties();
        properties.put("*allowedLangs", allowedLangs);
        properties.putAll(now);
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

    private static Properties getArguments(Path self, List<String> args) throws IOException {
        Properties properties = loadProperties(self);
        applyArgs(args, properties);
        System.out.println("Args: ");
        for (String propertyName : properties.stringPropertyNames()) {
            System.out.println(propertyName + "=" + properties.getProperty(propertyName));
        }
        return properties;
    }

    private static void applyArgs(List<String> args, Properties properties) {
        for (String arg : args) {
            if (arg.startsWith("-W")) {
                continue;
            }
            if (!arg.startsWith("-P")) {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
            String[] argToValue = arg.split("[=:]", 2);
            properties.setProperty(argToValue[0].substring(2), argToValue.length > 1 ? argToValue[1] : "");
        }
    }

    private static Properties loadProperties(Path self) throws IOException {
        for (String configName : Arrays.asList("dev.config", "release.config")) {
            Properties properties = readProperties(self, configName, false);
            if (properties == null) {
                continue;
            }
            String validationFileLocation = properties.getProperty("validationFileLocation");
            if (validationFileLocation == null) {
                throw new IOException("Config is broken: no validationFileLocation defined");
            }
            for (String file : validationFileLocation.split("\\|")) {
                if (Files.exists(self.resolve(file))) {
                    System.out.println("Config with suffix " + configName + " is suitable and will be used");
                    return properties;
                }
            }
            System.out.println("Config with suffix " + configName + " is not suitable");
        }
        System.err.println("No valid config found");
        return new Properties();
    }

    private static Properties readProperties(Path self, String configName, boolean useDefault) {
        Properties properties = new Properties();
        properties.setProperty("self", self.toAbsolutePath().toString());
        try {
            try (BufferedReader stream = Files.newBufferedReader(self.resolve(configName))) {
                properties.load(stream);
            }
        } catch (IOException e) {
            System.err.println("No config file with name " + configName);
            return useDefault ? properties : null;
        }

        return properties;
    }

    private static void execUnpack(Path self, String pathPattern, String supportedLangs, String supportedLods, String pcxFormat, String dryRun) throws IOException, DataFormatException {
        String[] langs = supportedLangs.split(";");
        String[] lods = supportedLods.split(";");

        for (String lang : langs) {
            for (String lod : lods) {
                Path path = Utils.resolveTemplate(self, pathPattern, lang, lod);
                if (!Files.exists(path)) {
                    continue;
                }

                Archive lodFile = LodFile.load(path);
                for (Archive.Element subFile : lodFile.files()) {
                    String name = subFile.name();
                    Path toStore = self.resolve(Path.of(lang + "@" + lod, name));
                    if (Boolean.parseBoolean(dryRun)) {
                        continue;
                    }

                    Files.createDirectories(toStore.getParent());
                    byte[] data = subFile.asBytes();

                    if (name.toLowerCase().endsWith(".pcx")) {
                        if (data[0] == 0x50 && data[1] == 0x33 && data[2] == 0x32 && data[3] == 0x46) {

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

    private static void execGui(Path self, List<String> args) throws Exception {
        Properties arguments = getArguments(self, args);
        String out = arguments.getProperty("gui_out");

        Gui gui = new Gui(Path.of(out));
        run(gui);
    }

    private static void execDiff(Path self, List<String> args) throws Exception {
        Properties cfg = getArguments(self, args);

        String leftDir = cfg.getProperty("left_dir");
        String rightDir = cfg.getProperty("right_dir");
        String logs = cfg.getProperty("logsPath", "logs");
        String nick = cfg.getProperty("userName", "unknown");

        String cfgDir = cfg.getProperty("commonCfgDir");

        try (BufferedReader stream = Files.newBufferedReader(Path.of(cfgDir, "diff.cfg"))) {
            cfg.load(stream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        applyArgs(args, cfg);
        resolveReferences(cfg);

        DiffUi gui = new DiffUi(Path.of(leftDir), Path.of(rightDir), cfg, self.resolve(logs).resolve("sync"), nick);

        run(gui);
    }

    private static String resolveReferences(Properties cfg, String property) {
        int substituteIndex;
        int substitutionEnd = -1;

        StringBuilder result = null;

        while (true) {
            substituteIndex = property.indexOf("${", substitutionEnd + 1);
            if (substituteIndex == -1) {
                if (result == null) {
                    return property;
                }
                result.append(property, substitutionEnd + 1, property.length());
                return result.toString();
            }

            substitutionEnd = property.indexOf("}", substituteIndex);
            if (result == null) {
                result = new StringBuilder();
                result.append(property, 0, substituteIndex);
            }
            String p = cfg.getProperty(property.substring(substituteIndex + 2, substitutionEnd));
            if (p != null) {
                result.append(resolveReferences(cfg, p));
            }
        }
    }

    private static void resolveReferences(Properties cfg) {
        for (Object key : cfg.keySet()) {
            cfg.put(key, resolveReferences(cfg, cfg.getProperty((String) key)));
        }
    }

}
