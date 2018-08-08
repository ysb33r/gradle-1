/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.fingerprint.impl

import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.DefaultFileSystemMirror
import org.gradle.api.internal.changedetection.state.DefaultFileSystemSnapshotter
import org.gradle.api.internal.changedetection.state.DefaultWellKnownFileLocations
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshot
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.fingerprint.FingerprintingStrategy
import org.gradle.internal.hash.TestFileHasher
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.test.fixtures.file.TestFile

class PathNormalizationStrategyTest extends AbstractProjectBuilderSpec {
    private StringInterner stringInterner = new StringInterner()

    public static final String IGNORED = "IGNORED"
    List<FileSystemSnapshot> roots
    TestFile jarFile1
    TestFile jarFile2
    TestFile resources
    String subDirA = "a"
    String subDirB = "b"
    String fileInRoot = "input.txt"
    String fileInSubdirA = "a/input-1.txt"
    String fileInSubdirB = "b/input-2.txt"
    TestFile emptyDir
    TestFile missingFile

    def setup() {
        StringInterner interner = Mock(StringInterner) {
            intern(_) >> { String string -> string }
        }

        jarFile1 = file("dir/libs/library-a.jar")
        jarFile1 << "JAR file #1"
        jarFile2 = file("dir/libs/library-b.jar")
        jarFile2 << "JAR file #2"
        resources = file("dir/resources")
        resources.file("input.txt") << "main input"
        resources.file("a/input-1.txt") << "input #1"
        resources.file("b/input-2.txt") << "input #2"
        emptyDir = file("empty-dir")
        emptyDir.mkdirs()
        missingFile = file("missing-file")

        def snapshotter = new DefaultFileSystemSnapshotter(new TestFileHasher(), interner, TestFiles.fileSystem(), new DefaultFileSystemMirror(new DefaultWellKnownFileLocations([])))

        roots = [
            snapshotter.snapshot(jarFile1),
            snapshotter.snapshot(jarFile2),
            snapshotter.snapshot(resources),
            snapshotter.snapshot(emptyDir),
            snapshotter.snapshot(missingFile)
        ]
    }


    def "sensitivity NONE"() {
        def fingerprints = collectFingerprints(IgnoredPathFingerprintingStrategy.INSTANCE)
        expect:
        allFilesToFingerprint.each { file ->
            if (file.isFile() || !file.exists()) {
                assert fingerprints[file] == IGNORED
            } else {
                assert fingerprints[file] == null
            }
        }
    }

    def "sensitivity NAME_ONLY"() {
        def fingerprints = collectFingerprints(NameOnlyFingerprintingStrategy.INSTANCE)
        expect:
        (allFilesToFingerprint - emptyDir - resources).each { file ->
            assert fingerprints[file] == file.name
        }
        fingerprints[emptyDir] == IGNORED
        fingerprints[resources] == IGNORED
    }

    def "sensitivity RELATIVE"() {
        def fingerprints = collectFingerprints(new RelativePathFingerprintingStrategy(stringInterner))
        expect:
        fingerprints[jarFile1]                      == jarFile1.name
        fingerprints[jarFile2]                      == jarFile2.name
        fingerprints[resources]                     == IGNORED
        fingerprints[resources.file(fileInRoot)]    == fileInRoot
        fingerprints[resources.file(subDirA)]       == subDirA
        fingerprints[resources.file(fileInSubdirA)] == fileInSubdirA
        fingerprints[resources.file(subDirB)]       == subDirB
        fingerprints[resources.file(fileInSubdirB)] == fileInSubdirB
        fingerprints[emptyDir]                      == IGNORED
        fingerprints[missingFile]                   == missingFile.name
    }

    def "sensitivity ABSOLUTE (include missing = true)"() {
        def fingerprints = collectFingerprints(AbsolutePathFingerprintingStrategy.INCLUDE_MISSING)
        expect:
        allFilesToFingerprint.each { file ->
            assert fingerprints[file] == file.absolutePath
        }
        fingerprints.size() == allFilesToFingerprint.size()
    }

    def "sensitivity ABSOLUTE (include missing = false)"() {
        def fingerprints = collectFingerprints(AbsolutePathFingerprintingStrategy.IGNORE_MISSING)
        expect:
        (allFilesToFingerprint - missingFile).each { file ->
            assert fingerprints[file] == file.absolutePath
        }
        fingerprints.size() == allFilesToFingerprint.size() - 1
    }

    List<File> getAllFilesToFingerprint() {
        [jarFile1, jarFile2, resources, emptyDir, missingFile] + [fileInRoot, subDirA, fileInSubdirA, subDirB, fileInSubdirB].collect { resources.file(it) }
    }

    protected TestFile file(String path) {
        new TestFile(project.file(path))
    }

    protected def collectFingerprints(FingerprintingStrategy strategy) {
        strategy.collectSnapshots(roots)
        Map<File, String> fingerprints = [:]
        strategy.collectSnapshots(roots).each { path, normalizedFingerprint ->
            String normalizedPath
            if (normalizedFingerprint instanceof IgnoredPathFingerprint) {
                normalizedPath = IGNORED
            } else {
                normalizedPath = normalizedFingerprint.normalizedPath
            }
            fingerprints.put(new File(path), normalizedPath)
        }
        return fingerprints

    }
}
