package com.github.artyomcool.lodinfra.data;

import com.github.artyomcool.lodinfra.data.dto.Data;
import com.github.artyomcool.lodinfra.data.dto.DataEntry;
import com.github.artyomcool.lodinfra.data.dto.LocalizedString;
import com.github.artyomcool.lodinfra.data.dto.Config;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonSerializer {

    private final Gson configGson;
    private final Gson dataGson;

    public JsonSerializer(String lang) {
        configGson = new GsonBuilder()
                .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
                .setPrettyPrinting()
                .registerTypeAdapterFactory(new TypeAdapterFactory() {
                    @Override
                    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                        if (LocalizedString.class.isAssignableFrom(type.getRawType())) {
                            TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
                            return new TypeAdapter<>() {
                                @Override
                                public void write(JsonWriter out, T value) throws IOException {
                                    delegate.write(out, value);
                                }

                                @Override
                                public T read(JsonReader in) throws IOException {
                                    T obj = delegate.read(in);
                                    LocalizedString read = (LocalizedString) obj;
                                    read.setLang(lang);
                                    return obj;
                                }
                            };
                        }
                        return null;
                    }
                })
                .create();

        dataGson = new GsonBuilder()
                .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
                .setPrettyPrinting()
                .create();
    }

    public Config loadConfig(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return configGson.fromJson(reader, Config.class);
        }
    }

    public void saveData(Data data, Path path) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            dataGson.toJson(data, writer);
        }
    }

    public Data loadData(Path path) throws IOException {
        try (Reader input = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return dataGson.fromJson(input, Data.class);
        }
    }

    public Object elementFromText(String text) {
        return dataGson.fromJson(text, Object.class);
    }

    public String elementToText(Object e) {
        return dataGson.toJson(e);
    }

    public DataEntry deepCopy(DataEntry data) {
        return dataGson.fromJson(dataGson.toJsonTree(data), DataEntry.class);
    }
}
