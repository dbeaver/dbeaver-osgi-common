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
package com.dbeaver.osgi.dependency.processing.p2;

import com.dbeaver.osgi.dependency.processing.p2.repository.RemoteP2BundleInfo;
import com.dbeaver.osgi.dependency.processing.p2.repository.RemoteP2Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class RemoteP2Feature {
    private static final Logger log = LoggerFactory.getLogger(RemoteP2BundleInfo.class);

    RemoteP2Repository repository;
    String name;
    String version;
    private Path path;
    private final Lock lock = new ReentrantLock();


    public RemoteP2Feature(String name, String version, RemoteP2Repository repository) {
        this.repository = repository;
        this.name = name;
        this.version = version;
    }

    public boolean resolveFeature() {
        while (!lock.tryLock()) {
            // Already resolving by another thread
            Thread.onSpinWait();
        }
        try {
            if (isDownloaded()) return true;
            log.info("Downloading " + getName() + "_" + getVersion() + " from " + getRepository().getName() + "... ");
            Path filePath = repository.resolveFeature(this);
            if (filePath == null) {
                return false;
            }
            this.path = filePath;
            return true;
        } finally {
            lock.unlock();
        }
    }

    private boolean isDownloaded() {
        return path != null;
    }

    public RemoteP2Repository getRepository() {
        return repository;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public Path getPath() {
        while (!lock.tryLock()) {
            // Already resolving by another thread
            Thread.onSpinWait();
        }
        try {
            return path;
        } finally {
            lock.unlock();
        }
    }
}
