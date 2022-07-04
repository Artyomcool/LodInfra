package com.github.artyomcool.lodinfra.ui;

import com.github.artyomcool.lodinfra.data.Helpers;
import com.github.artyomcool.lodinfra.data.dto.Data;
import com.github.artyomcool.lodinfra.data.dto.DataEntry;
import com.github.artyomcool.lodinfra.data.dto.TabGroup;

import java.util.function.Supplier;

abstract class Entry {
    final TabGroup group;
    final DataEntry data;

    String title;

    Entry(TabGroup group, DataEntry data) {
        this.group = group;
        this.data = data;

        title = invalidateTitle();
    }

    public String invalidateTitle() {
        return Helpers.evalString(group.title, data);
    }

    @Override
    public String toString() {
        return title;
    }

    public abstract void remove();

    public abstract Entry copy();
}
