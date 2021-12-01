package org.apache.logging.log4j;

public class LogManager {
    public static org.apache.logging.log4j.Logger getLogger(Class<?> c) {
        return new Logger() {
        };
    }
}
