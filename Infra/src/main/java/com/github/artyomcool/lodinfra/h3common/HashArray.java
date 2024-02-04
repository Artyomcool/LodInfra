package com.github.artyomcool.lodinfra.h3common;

import java.util.Arrays;

public class HashArray {
    public final int[] array;
    public final int hash;

    public HashArray(int[] array) {
        this.array = array;
        this.hash = Arrays.hashCode(array);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HashArray hashArray = (HashArray) o;

        if (hash != hashArray.hash) return false;
        return Arrays.equals(array, hashArray.array);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
