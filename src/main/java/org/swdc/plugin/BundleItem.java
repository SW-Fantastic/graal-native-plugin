package org.swdc.plugin;

import java.util.List;

public class BundleItem {

    private String name;

    private List<String> locales;

    public BundleItem(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<String> getLocales() {
        return locales;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLocales(List<String> locales) {
        this.locales = locales;
    }
}
