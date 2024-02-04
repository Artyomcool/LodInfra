package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.h3common.Def;

import java.util.Arrays;
import java.util.List;

public enum DefType {
    DefDefault("Def-sprite: default", 0x40),
    DefCombatCreature("Def-sprite: combat creature", 0x42),
    DefAdventureObject("Def-sprite: adventure object", 0x43),
    DefAdventureHero("Def-sprite: adventure hero", 0x44),
    DefGroundTile("Def-sprite: ground tiles", 0x45),
    DefMousePointer("Def-sprite: mouse pointer", 0x46),
    DefInterface("Def-sprite: mouse pointer", 0x47),
    DefCombatHero("Def-sprite: combat hero", 0x49),

    D32("D32", 0x46323344), // D32F in little endian
    P32("P32", 0x46323350), // P32F in little endian
    Pcx8("Pcx8", 0x10),
    Pcx24("Pcx24", 0x11),
    Unknown("Unknown", 0)
    ;

    public static List<DefType> VALUES = Arrays.asList(DefType.values());

    public final String name;
    public final int type;

    DefType(String name, int type) {
        this.name = name;
        this.type = type;
    }

    public static DefType of(int type) {
        for (DefType value : VALUES) {
            if (value.type == type) {
                return value;
            }
        }
        return Unknown;
    }
}
