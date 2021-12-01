package org.apache.logging.log4j;

import static org.apache.logging.log4j.LogBuilder.NOOP;

public interface Logger {
    default LogBuilder atTrace() { return NOOP; }
    default LogBuilder atDebug() { return NOOP; }
    default LogBuilder atInfo() { return NOOP; }
    default LogBuilder atWarn() { return NOOP; }
    default LogBuilder atError() { return NOOP; }

}
