package com.dbeaver.osgi.dependency.processing.inter;

import com.dbeaver.osgi.dependency.processing.BundleInfo;
import com.dbeaver.osgi.dependency.processing.util.Version;
import com.dbeaver.osgi.dependency.processing.util.VersionRange;
import org.jkiss.utils.Pair;

public interface IImportListener {
    void notifyAboutDependency(Pair<String, VersionRange> packageToImport, Pair<BundleInfo, Version> info);
}
