package com.github.artyomcool.lodinfra.data.dto;

import java.util.HashMap;

public class LocalizedString extends HashMap<String, String> {

    private transient String _lang;

    public void setLang(String lang) {
        this._lang = lang;
    }

    @Override
    public String toString() {
        return get(_lang);
    }

}
