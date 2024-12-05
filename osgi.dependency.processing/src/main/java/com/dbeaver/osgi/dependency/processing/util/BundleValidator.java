package com.dbeaver.osgi.dependency.processing.util;

import jakarta.annotation.Nonnull;

import java.util.List;

public class BundleValidator {
    public static final List<String> PACKAGES_FOR_DEV_PROPERTIES = List.of(
        "org.jkiss",
        "io.cloudbeaver",
        "com.dbeaver",
        "swtbot-simple",
        "connections",
        "unit"
    );

    public static boolean isBundleAcceptable(@Nonnull String bundleName) {
        return !bundleName.startsWith("org.jkiss.bundle") &&
            PACKAGES_FOR_DEV_PROPERTIES.stream().anyMatch(bundleName::startsWith);
    }
}
