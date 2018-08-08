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

package org.gradle.api.internal.changedetection.state.mirror

import org.apache.tools.ant.DirectoryScanner
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.tasks.util.PatternSet
import org.gradle.internal.MutableBoolean
import org.gradle.internal.hash.TestFileHasher
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

@UsesNativeServices
class MirrorUpdatingDirectoryWalkerTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def fileHasher = new TestFileHasher()
    def walker = new MirrorUpdatingDirectoryWalker(fileHasher, TestFiles.fileSystem(), new StringInterner())

    def "basic directory walking works"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def rootTextFile = rootDir.file("a.txt").createFile()
        def nestedTextFile = rootDir.file("a/b/c.txt").createFile()
        def nestedSiblingTextFile = rootDir.file("a/c/c.txt").createFile()
        def notTextFile = rootDir.file("a/b/c.html").createFile()
        def excludedFile = rootDir.file("subdir1/a/b/c.html").createFile()
        def notUnderRoot = tmpDir.createDir("root2").file("a.txt").createFile()
        def doesNotExist = rootDir.file("b.txt")

        def patterns = new PatternSet()
        patterns.include("**/*.txt")
        patterns.exclude("subdir1/**")

        def visited = []
        def relativePaths = []

        def actuallyFiltered = new MutableBoolean(false)
        when:
        def root = walkDir(rootDir, null, walker, actuallyFiltered)
        root.accept(new RelativePathTrackingVisitor() {
            @Override
            void visit(String absolutePath, Deque<String> relativePath) {
                visited << absolutePath
                relativePaths << relativePath.join("/")
            }
        })

        then:
        ! actuallyFiltered.get()
        visited.contains(rootTextFile.absolutePath)
        visited.contains(nestedTextFile.absolutePath)
        visited.contains(nestedSiblingTextFile.absolutePath)
        visited.contains(notTextFile.absolutePath)
        visited.contains(excludedFile.absolutePath)
        !visited.contains(notUnderRoot.absolutePath)
        !visited.contains(doesNotExist.absolutePath)
        relativePaths as Set == (['root'] + [
            'a',
            'a/b', 'a/b/c.txt',
            'a/c', 'a/c/c.txt', 'a/b/c.html',
            'subdir1', 'subdir1/a', 'subdir1/a/b', 'subdir1/a/b/c.html',
            'a.txt'
        ].collect { 'root/' + it }) as Set
    }

    def "filtering works"() {
        given:
        def rootDir = tmpDir.createDir("root")
        def rootTextFile = rootDir.file("a.txt").createFile()
        def nestedTextFile = rootDir.file("a/b/c.txt").createFile()
        def nestedSiblingTextFile = rootDir.file("a/c/c.txt").createFile()
        def notTextFile = rootDir.file("a/b/c.html").createFile()
        def excludedFile = rootDir.file("subdir1/a/b/c.html").createFile()
        def notUnderRoot = tmpDir.createDir("root2").file("a.txt").createFile()
        def doesNotExist = rootDir.file("b.txt")

        def patterns = new PatternSet()
        patterns.include("**/*.txt")
        patterns.exclude("subdir1/**")

        def visited = []
        def relativePaths = []

        def actuallyFiltered = new MutableBoolean(false)
        when:
        def root = walkDir(rootDir, patterns, walker, actuallyFiltered)
        root.accept(new RelativePathTrackingVisitor() {
            @Override
            void visit(String absolutePath, Deque<String> relativePath) {
                visited << absolutePath
                relativePaths << relativePath.join("/")
            }
        })

        then:
        actuallyFiltered.get()
        visited.contains(rootTextFile.absolutePath)
        visited.contains(nestedTextFile.absolutePath)
        visited.contains(nestedSiblingTextFile.absolutePath)
        !visited.contains(notTextFile.absolutePath)
        !visited.contains(excludedFile.absolutePath)
        !visited.contains(notUnderRoot.absolutePath)
        !visited.contains(doesNotExist.absolutePath)
        relativePaths as Set == [
            'root',
            'root/a',
            'root/a/b', 'root/a/b/c.txt',
            'root/a/c', 'root/a/c/c.txt',
            'root/a.txt'
        ] as Set
    }

    def "default excludes are correctly parsed"() {
        def defaultExcludes = new MirrorUpdatingDirectoryWalker.DefaultExcludes(DirectoryScanner.getDefaultExcludes())

        expect:
        DirectoryScanner.getDefaultExcludes() as Set == ['**/%*%', '**/.git/**', '**/SCCS', '**/.bzr', '**/.hg/**', '**/.bzrignore', '**/.git', '**/SCCS/**', '**/.hg', '**/.#*', '**/vssver.scc', '**/.bzr/**', '**/._*', '**/#*#', '**/*~', '**/CVS', '**/.hgtags', '**/.svn/**', '**/.hgignore', '**/.svn', '**/.gitignore', '**/.gitmodules', '**/.hgsubstate', '**/.gitattributes', '**/CVS/**', '**/.hgsub', '**/.DS_Store', '**/.cvsignore'] as Set

        ['%some%', 'SCCS', '.bzr', '.bzrignore', '.git', '.hg', '.#anything', '.#', 'vssver.scc', '._something', '#anyt#', '##', 'temporary~', '~'].each {
            assert defaultExcludes.excludeFile(it)
        }

        ['.git', '.hg', 'CVS'].each {
            assert defaultExcludes.excludeDir(it)
        }

        !defaultExcludes.excludeDir('#some#')
        !defaultExcludes.excludeDir('.cvsignore')

        !defaultExcludes.excludeFile('.svnsomething')
        !defaultExcludes.excludeFile('#some')
    }

    private static FileSystemSnapshot walkDir(File dir, PatternSet patterns, MirrorUpdatingDirectoryWalker walker, MutableBoolean actuallyFiltered) {
        walker.walkDir(dir.absolutePath, patterns, actuallyFiltered)
    }
}

abstract class RelativePathTrackingVisitor implements PhysicalSnapshotVisitor {
    private Deque<String> relativePath = new ArrayDeque<String>()

    @Override
    boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
        relativePath.addLast(directorySnapshot.name)
        visit(directorySnapshot.absolutePath, relativePath)
        return true
    }

    @Override
    void visit(PhysicalSnapshot fileSnapshot) {
        relativePath.addLast(fileSnapshot.name)
        visit(fileSnapshot.absolutePath, relativePath)
        relativePath.removeLast()
    }

    @Override
    void postVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
        relativePath.removeLast()
    }

    abstract void visit(String absolutePath, Deque<String> relativePath)
}
