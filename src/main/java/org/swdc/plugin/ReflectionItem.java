package org.swdc.plugin;

import io.github.classgraph.ClassInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReflectionItem {

    private String type = null;

    private List<ReflectionField> fields = new ArrayList<>();

    private boolean allDeclaredConstructors = true;
    private boolean allPublicConstructors = true;
    private boolean allDeclaredMethods = true;
    private boolean allPublicMethods = true;
    private boolean allPublicClasses = true;
    private boolean allDeclaredFields = true;
    private boolean allPublicFields = true;

    public ReflectionItem(String fullName) {
        this.type = fullName;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public static List<ReflectionItem> resolve(ClassInfo clazz) {

        List<ReflectionItem> list = new ArrayList<>();
        ClassInfo info = clazz.getSuperclass();
        if (info != null) {
            String superClazz = info.getName();
            while (superClazz != null) {
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
            return type.equals(other.getType());
        }
        return false;
    }

    public List<ReflectionField> getFields() {
        return fields;
    }

    public void setFields(List<ReflectionField> fields) {
        this.fields = fields;
    }

    @Override
    public int hashCode() {
        return type.hashCode();
    }
}
