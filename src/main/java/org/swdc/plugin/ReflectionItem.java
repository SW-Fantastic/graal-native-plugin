package org.swdc.plugin;

import io.github.classgraph.ClassInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReflectionItem {

    private String name;
    private boolean allDeclaredConstructors = true;
    private boolean allPublicConstructors = true;
    private boolean allDeclaredMethods = true;
    private boolean allPublicMethods = true;
    private boolean allDeclaredClasses = true;
    private boolean allPublicClasses = true;
    private boolean allDeclaredFields = true;
    private boolean allPublicFields = true;

    public ReflectionItem(String fullName) {
        this.name = fullName;
    }

    public String getName() {
        return name;
    }

    public boolean isAllDeclaredClasses() {
        return allDeclaredClasses;
    }

    public boolean isAllDeclaredConstructors() {
        return allDeclaredConstructors;
    }

    public boolean isAllDeclaredMethods() {
        return allDeclaredMethods;
    }

    public boolean isAllPublicClasses() {
        return allPublicClasses;
    }

    public boolean isAllPublicConstructors() {
        return allPublicConstructors;
    }

    public boolean isAllPublicMethods() {
        return allPublicMethods;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAllDeclaredClasses(boolean allDeclaredClasses) {
        this.allDeclaredClasses = allDeclaredClasses;
    }

    public void setAllDeclaredConstructors(boolean allDeclaredConstructors) {
        this.allDeclaredConstructors = allDeclaredConstructors;
    }

    public boolean isAllDeclaredFields() {
        return allDeclaredFields;
    }

    public boolean isAllPublicFields() {
        return allPublicFields;
    }

    public void setAllDeclaredFields(boolean allDeclaredFields) {
        this.allDeclaredFields = allDeclaredFields;
    }

    public void setAllPublicFields(boolean allPublicFields) {
        this.allPublicFields = allPublicFields;
    }

    public void setAllDeclaredMethods(boolean allDeclaredMethods) {
        this.allDeclaredMethods = allDeclaredMethods;
    }

    public void setAllPublicClasses(boolean allPublicClasses) {
        this.allPublicClasses = allPublicClasses;
    }

    public void setAllPublicConstructors(boolean allPublicConstructors) {
        this.allPublicConstructors = allPublicConstructors;
    }

    public void setAllPublicMethods(boolean allPublicMethods) {
        this.allPublicMethods = allPublicMethods;
    }

    public static List<ReflectionItem> resolve(ClassInfo clazz) {

        List<ReflectionItem> list = new ArrayList<>();
        ClassInfo info = clazz.getSuperclass();
        if (info != null) {
            String superClazz = info.getName();
            while (!superClazz.startsWith("java") && !superClazz.startsWith("javax")) {
                ReflectionItem item = new ReflectionItem(superClazz);
                list.add(item);
                info = info.getSuperclass();
                if (info == null) {
                    break;
                }
                superClazz = info.getName();
            }

        }
        list.add(new ReflectionItem(clazz.getName()));
        return list;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ReflectionItem) {
            ReflectionItem other = (ReflectionItem)obj;
            return name.equals(other.getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
