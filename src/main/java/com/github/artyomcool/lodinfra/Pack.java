package com.github.artyomcool.lodinfra;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Properties;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class Pack {

    public static void main(String[] args) throws IOException {
        try {
            Path self = Path.of(".").normalize().toAbsolutePath();

            if (args.length > 0 && args[0].equals("-unpack")) {
                execUnpack(self, args[1], args[2], args[3], args[4]);
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

    private static void execPack(Path self, String... args) throws DataFormatException, IOException {
        Properties properties = loadProperties(self);
        for (String arg : args) {
            if (!arg.startsWith("-P")) {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
            String[] argToValue = arg.split("[=:]");
            properties.setProperty(argToValue[0].substring(2), argToValue[1]);
        }

        String pathPattern = properties.getProperty("outputPattern");

        DirectoryResourceCollector.collectResources(
                self,
                pathPattern,
                Boolean.parseBoolean(properties.getProperty("dryRun")),
                Boolean.parseBoolean(properties.getProperty("logDetailedDiff")),
                Boolean.parseBoolean(properties.getProperty("checkTimestamps")),
                Integer.parseInt(properties.getProperty("compressionLevel")),
                properties.getProperty("ignoreLangs", "").split(",")
        );

        bye();
    }

    private static void bye() {
        Console c = System.console();
        if (c != null) {
            c.format("Done, Press Enter to pay respect");
            c.readLine();
        }
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

    private static void execUnpack(Path self, String pathPattern, String supportedLangs, String supportedLods, String dryRun) throws IOException, DataFormatException {
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
                    Path toStore = self.resolve(Path.of(lang + "@" + lod, new String(subFile.name).trim()));
                    System.out.println("Storing " + toStore);
                    if (Boolean.parseBoolean(dryRun)) {
                        continue;
                    }

                    Files.createDirectories(toStore.getParent());
                    if (subFile.compressedSize == 0) {
                        Files.write(
                                toStore,
                                Arrays.copyOfRange(
                                        lodFile.originalData,
                                        subFile.globalOffsetInFile,
                                        subFile.globalOffsetInFile + subFile.uncompressedSize
                                ),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING
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

                        Files.write(
                                toStore,
                                uncompressed,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.WRITE,
                                StandardOpenOption.TRUNCATE_EXISTING
                        );
                    }
                }
            }
        }

        bye();
    }

}
