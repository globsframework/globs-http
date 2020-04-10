package org.globsframework.remote.shared.impl;

import org.globsframework.remote.shared.SharedDataManager;

public class SharedPathImpl implements SharedDataManager.Path {
    private String fullPath;
    private int elementCount = 0;

    public SharedPathImpl(String fullPath) {
        if (!fullPath.startsWith("/")){
            fullPath = "/" + fullPath;
        }
        while (fullPath.endsWith("/") && fullPath.length() != 1) {
            fullPath = fullPath.substring(0, fullPath.length() - 1);
        }
        if (fullPath.length() == 1) {
            elementCount = 0;
        } else {
            for (int i = 0; i < fullPath.length(); i++) {
                if (fullPath.charAt(i) == '/') {
                    elementCount++;
                }
            }
        }
        this.fullPath = fullPath;
    }

    public String getFullPath() {
        return fullPath;
    }

    public int getElementCount() {
        return elementCount;
    }

    public String toString() {
        return fullPath;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SharedPathImpl that = (SharedPathImpl) o;

        if (fullPath != null ? !fullPath.equals(that.fullPath) : that.fullPath != null) return false;

        return true;
    }

    public int hashCode() {
        return fullPath != null ? fullPath.hashCode() : 0;
    }
}
