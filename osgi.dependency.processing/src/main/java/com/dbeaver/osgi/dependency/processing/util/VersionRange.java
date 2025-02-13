/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dbeaver.osgi.dependency.processing.util;

import org.jkiss.code.Nullable;
import org.jkiss.utils.Pair;

public class VersionRange extends Pair<Version, Version> {
    private final boolean includingFirst;
    private final boolean includingSecond;

    public VersionRange(Version first, Version second, boolean includingFirst, boolean includingSecond) {
        super(first, second);
        this.includingFirst = includingFirst;
        this.includingSecond = includingSecond;
    }
    @Nullable
    public static VersionRange fromString(String range) {
        range = trimColons(range);
        if (range == null || "0.0.0".equals(range)) {
            return null;
        }
        // Some artifacts might use this weird notation, does the same thing
        range = range.replace("((", "(").replace("))", ")");
        if (range.contains("(") || range.contains("[")) {
            boolean includingFirst = range.startsWith("[");
            boolean includingSecond = range.endsWith("]");
            String[] versions = range.substring(1, range.length() - 1).split(",");
            Version first = null, second = null;
            if (!versions[0].trim().isEmpty()) {
                first = new Version(versions[0].trim());
            }
            if (!versions[1].trim().isEmpty()) {
                second = new Version(versions[1].trim());
            }
            return new VersionRange(first, second, includingFirst, includingSecond);
        } else {
            Version version = new Version(range);
            return new VersionRange(version, null, true, true);
        }
    }

    @Nullable
    private static String trimColons(String range) {
        if (range == null) {
            return null;
        }
        if (range.startsWith("\"") && range.endsWith("\"")) {
            range = range.substring(1, range.length() - 1);
        }
        return range;
    }

    public boolean versionIsSuitable(Version version) {
        boolean isValid = true;
        if (getFirst() != null) {
            int comparisonWithFirst = version.compareTo(getFirst());
            isValid &= comparisonWithFirst > 0 | (includingFirst && comparisonWithFirst == 0);
        }
        if (getSecond() != null) {
            int comparisonWithSecond = version.compareTo(getSecond());
            isValid &= comparisonWithSecond < 0 | (includingSecond && comparisonWithSecond == 0);
        }
        return isValid;
    }

    public static boolean isVersionsCompatible(VersionRange versionRange, Version version) {
        if (versionRange != null && version != null) {
            return versionRange.versionIsSuitable(version);
        } else {
            return true;
        }
    }
}
