/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2026 DBeaver Corp and others
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

import com.dbeaver.osgi.dependency.processing.p2.repository.exception.RepositoryInitialisationError;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class P2RepositoryManagerTest {
    private static final String LOCAL_REPOSITORIES =
        "file:${env.DBEAVER_DEV_HOME}/dbeaver-deps-ce/p2/repository/target/repository/;"
            + "file:${env.DBEAVER_DEV_HOME}/dbeaver-deps-pro/p2/repository/target/repository/;"
            + "https://download.eclipse.org/releases/${eclipse-version}/";

    @Test
    void requiresDevHomeForLocalRepositories() {
        for (String devHome : new String[] {null, "", "  "}) {
            assertThrows(RepositoryInitialisationError.class,
                () -> P2RepositoryManager.resolveLocalRepositories(LOCAL_REPOSITORIES, devHome));
        }
    }

    @Test
    void resolvesBothRepositoriesWithSpacesInDevHome() throws RepositoryInitialisationError {
        Path devHome = Path.of("local development").toAbsolutePath();
        String[] repositories = P2RepositoryManager.resolveLocalRepositories(LOCAL_REPOSITORIES, devHome.toString()).split(";");
        assertEquals(devHome.resolve("dbeaver-deps-ce/p2/repository/target/repository"), Path.of(URI.create(repositories[0])));
        assertEquals(devHome.resolve("dbeaver-deps-pro/p2/repository/target/repository"), Path.of(URI.create(repositories[1])));
        assertEquals("https://download.eclipse.org/releases/${eclipse-version}/", repositories[2]);
    }

    @Test
    void remoteRepositoriesDoNotRequireDevHome() throws RepositoryInitialisationError {
        String repository = "https://repo.dbeaver.net/p2/ce/latest/";
        assertEquals(repository, P2RepositoryManager.resolveLocalRepositories(repository, null));
    }
}
