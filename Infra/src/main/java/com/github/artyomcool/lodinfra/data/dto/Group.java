package com.github.artyomcool.lodinfra.data.dto;

import java.util.ArrayList;
import java.util.List;

public class Group {
    public LocalizedString name = new LocalizedString();
    public List<Field> fields = new ArrayList<>();
}
