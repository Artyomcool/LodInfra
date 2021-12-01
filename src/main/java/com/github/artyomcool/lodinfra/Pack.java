package com.github.artyomcool.lodinfra;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public class Pack {

    public static void main(String[] args) throws DataFormatException, IOException, InvalidFormatException {
        Path self = Path.of(".").normalize().toAbsolutePath();

        if (args.length > 0 && args[0].equals("-unpack")) {
            execUnpack(self, args[1], args[2], args[3]);
        } else {
            execPack(self, args[0]);
        }
        Console c = System.console();
        if (c != null) {
            c.format("Done, Press Enter to pay respect");
            c.readLine();
        }
    }

    private static void execPack(Path self, String pathPattern) throws DataFormatException, IOException, InvalidFormatException {
        DirectoryResourceCollector.collectResources(
                self,
                pathPattern,
                false
        );
    }

    private static void execUnpack(Path self, String pathPattern, String supportedLangs, String supportedLods) throws IOException, DataFormatException {
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
    }

}
