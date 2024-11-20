package com.dbeaver.osgi.inter;

import com.dbeaver.osgi.BundleInfo;
import com.dbeaver.osgi.util.Version;
import com.dbeaver.osgi.util.VersionRange;
import org.jkiss.utils.Pair;

public interface IImportListener {
    void notifyAboutDependency(Pair<String, VersionRange> packageToImport, Pair<BundleInfo, Version> info);
}
