package com.github.artyomcool.lodinfra;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.DataFormatException;

public class Pack {

    public static void main(String[] args) throws DataFormatException, IOException, InvalidFormatException {
        Path self = Path.of(".").normalize().toAbsolutePath();
        System.out.println(self);
        System.out.println(self.getParent().resolve(args[0]).toAbsolutePath());
        DirectoryResourceCollector.collectResources(
                self,
                self.resolveSibling(args[0]),
                self.resolveSibling(args[1]),
                false
        );
    }

}
