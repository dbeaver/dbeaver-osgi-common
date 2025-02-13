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

package com.dbeaver.osgi.dependency.processing.p2.repository;

import com.dbeaver.osgi.dependency.processing.BundleInfo;
import com.dbeaver.osgi.dependency.processing.p2.P2BundleLookupCache;
import com.dbeaver.osgi.dependency.processing.p2.repository.exception.RepositoryInitialisationError;
import com.dbeaver.osgi.dependency.processing.xml.ContentParserXmlExtension;

import java.nio.file.Path;

public interface IRepository<BUNDLE extends BundleInfo> {
    String getName();

    Path resolveBundle(BUNDLE bundleInfo);

    void init(P2BundleLookupCache cache, ContentParserXmlExtension extension) throws RepositoryInitialisationError;

}
