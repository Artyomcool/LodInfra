package com.github.artyomcool.lodinfra;

import java.nio.file.Path;

public enum LodType {
    LOD,
    SND,
    VID;

    public static LodType forPath(Path lodPath) {
        String lodName = lodPath.toString();
        return switch (lodName.substring(lodName.lastIndexOf('.') + 1).toLowerCase()) {
            case "snd" -> LodType.SND;
            case "vid" -> LodType.VID;
            case "lod" -> LodType.LOD;
            default -> null;    // sometimes can be interpreted as LOD
        };
    }
}
