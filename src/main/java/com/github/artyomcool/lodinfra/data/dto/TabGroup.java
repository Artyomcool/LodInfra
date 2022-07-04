package com.github.artyomcool.lodinfra.data.dto;

import java.util.List;

public class TabGroup extends Group {
    public TabType type;
    public String id;
    public String title;
    public String alias;
    public List<TabGroup> tabs;
}
