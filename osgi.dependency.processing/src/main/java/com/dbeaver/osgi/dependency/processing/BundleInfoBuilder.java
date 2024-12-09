package com.dbeaver.osgi.dependency.processing;


import com.dbeaver.osgi.dependency.processing.util.Version;
import com.dbeaver.osgi.dependency.processing.util.VersionRange;
import org.jkiss.utils.Pair;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class BundleInfoBuilder {
    private Path path;
    private String bundleName;
    private String bundleVersion;
    private List<String> classpathLibs;
    private List<Pair<String, VersionRange>> requireBundles;
    private Set<String> reexportedBundles;
    private Set<Pair<String, Version>> exportPackages;
    private Set<Pair<String, VersionRange>> importPackages;
    private List<String> requiredFragments;
    private Pair<String, VersionRange> fragmentHost;
    private Integer startLevel;
    private String requiredExecutionEnvironment;

    public BundleInfoBuilder setPath(Path path) {
        this.path = path;
        return this;
    }

    public BundleInfoBuilder setBundleName(String bundleName) {
        this.bundleName = bundleName;
        return this;
    }

    public BundleInfoBuilder setBundleVersion(String bundleVersion) {
        this.bundleVersion = bundleVersion;
        return this;
    }

    public BundleInfoBuilder setClasspathLibs(List<String> classpathLibs) {
        this.classpathLibs = classpathLibs;
        return this;
    }

    public BundleInfoBuilder setRequireBundles(List<Pair<String, VersionRange>> requireBundles) {
        this.requireBundles = requireBundles;
        return this;
    }

    public BundleInfoBuilder setReexportedBundles(Set<String> reexportedBundles) {
        this.reexportedBundles = reexportedBundles;
        return this;
    }

    public BundleInfoBuilder setExportPackages(Set<Pair<String, Version>> exportPackages) {
        this.exportPackages = exportPackages;
        return this;
    }

    public BundleInfoBuilder setImportPackages(Set<Pair<String, VersionRange>> importPackages) {
        this.importPackages = importPackages;
        return this;
    }

    public BundleInfoBuilder setRequiredFragments(List<String> requiredFragments) {
        this.requiredFragments = requiredFragments;
        return this;
    }

    public BundleInfoBuilder setFragmentHost(Pair<String, VersionRange> fragmentHost) {
        this.fragmentHost = fragmentHost;
        return this;
    }

    public BundleInfoBuilder setStartLevel(Integer startLevel) {
        this.startLevel = startLevel;
        return this;
    }

    public BundleInfoBuilder setRequiredExecutionEnvironment(String requiredExecutionEnvironment) {
        this.requiredExecutionEnvironment = requiredExecutionEnvironment;
        return this;
    }

    public BundleInfo createBundleInfo() {
        return new BundleInfo(
            path,
            bundleName,
            bundleVersion,
            classpathLibs,
            requireBundles,
            reexportedBundles,
            exportPackages,
            importPackages,
            requiredFragments,
            fragmentHost,
            startLevel,
            requiredExecutionEnvironment
        );
    }
}