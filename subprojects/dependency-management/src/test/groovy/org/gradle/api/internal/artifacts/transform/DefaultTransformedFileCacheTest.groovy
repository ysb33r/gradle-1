/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata
import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot
import org.gradle.cache.AsyncCacheAccess
import org.gradle.cache.CacheDecorator
import org.gradle.cache.CrossProcessCacheAccess
import org.gradle.cache.MultiProcessSafePersistentIndexedCache
import org.gradle.cache.internal.CacheScopeMapping
import org.gradle.cache.internal.DefaultCacheRepository
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resource.local.FileAccessTimeJournal
import org.gradle.internal.util.BiFunction
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.InMemoryCacheFactory
import org.gradle.util.UsesNativeServices
import org.junit.Rule

@UsesNativeServices
class DefaultTransformedFileCacheTest extends ConcurrentSpec {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def transformsStoreDirectory = tmpDir.file("output")
    def artifactCacheMetaData = Mock(ArtifactCacheMetadata)
    def scopeMapping = Stub(CacheScopeMapping)
    def cacheRepo = new DefaultCacheRepository(scopeMapping, new InMemoryCacheFactory())
    def decorator = Stub(InMemoryCacheDecoratorFactory) {
        decorator(_, _) >> new CacheDecorator() {
            @Override
            <K, V> MultiProcessSafePersistentIndexedCache<K, V> decorate(String cacheId, String cacheName, MultiProcessSafePersistentIndexedCache<K, V> persistentCache, CrossProcessCacheAccess crossProcessCacheAccess, AsyncCacheAccess asyncCacheAccess) {
                return persistentCache
            }
        }
    }
    def snapshotter = Mock(FileSystemSnapshotter)
    def fileAccessTimeJournal = Mock(FileAccessTimeJournal)
    DefaultTransformedFileCache cache

    def setup() {
        scopeMapping.getBaseDirectory(_, _, _) >> tmpDir.testDirectory
        scopeMapping.getRootDirectory(_) >> tmpDir.testDirectory
        artifactCacheMetaData.transformsStoreDirectory >> transformsStoreDirectory
        cache = createCache()
    }

    private DefaultTransformedFileCache createCache() {
        new DefaultTransformedFileCache(artifactCacheMetaData, cacheRepo, decorator, snapshotter, fileAccessTimeJournal)
    }

    def "reuses result for given inputs and transform"() {
        def transform = Mock(BiFunction)
        def inputFile = tmpDir.file("a")

        when:
        def result = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        result*.name == ["a.1"]

        and:
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))
        1 * transform.apply(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        1 * fileAccessTimeJournal.setLastAccessTime(_, _)
        0 * snapshotter._
        0 * transform._

        when:
        def result2 = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        result2 == result

        and:
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))
        0 * fileAccessTimeJournal.setLastAccessTime(_, _)
        0 * snapshotter._
        0 * transform._
    }

    def "does not contain result before transform ran"() {
        given:
        def inputFile = tmpDir.file("a")
        def hash = HashCode.fromInt(123)
        _ * snapshotter.snapshot(_) >> snapshot(hash)

        expect:
        !cache.contains(inputFile, hash)
    }

    def "contains result after transform ran once"() {
        given:
        def transform = Stub(BiFunction)
        def inputFile = tmpDir.file("a")
        def hash = HashCode.fromInt(123)
        _ * snapshotter.snapshot(_) >> snapshot(hash)
        _ * transform.apply(_, _) >>  { File file, File dir -> [new TestFile(dir, file.getName()).touch()] }

        when:
        cache.getResult(inputFile, hash, transform)

        then:
        cache.contains(inputFile, hash)
    }

    def "does not contain result if a different transform ran"() {
        given:
        def transform = Stub(BiFunction)
        def inputFile = tmpDir.file("a")
        def hash = HashCode.fromInt(123)
        def otherHash = HashCode.fromInt(456)
        _ * snapshotter.snapshot(_) >> snapshot(hash)
        _ * transform.apply(_, _) >>  { File file, File dir -> [file] }

        when:
        cache.getResult(inputFile, hash, transform)

        then:
        !cache.contains(inputFile, otherHash)
    }

    def "reuses result when transform returns its input file"() {
        def transform = Mock(BiFunction)
        def inputFile = tmpDir.file("a").createFile()

        when:
        def result = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        result == [inputFile]

        and:
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))
        1 * transform.apply(inputFile, _) >>  { File file, File dir -> [file] }
        0 * snapshotter._
        0 * transform._

        when:
        def result2 = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        result2 == result

        and:
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))
        0 * transform._
        0 * snapshotter._
        0 * fileAccessTimeJournal._
    }

    def "applies transform once when requested concurrently by multiple threads"() {
        def transform = Mock(BiFunction)
        def inputFile = tmpDir.file("a")

        when:
        def result1
        def result2
        def result3
        def result4
        async {
            start {
                result1 = cache.getResult(inputFile, HashCode.fromInt(123), transform)
            }
            start {
                result2 = cache.getResult(inputFile, HashCode.fromInt(123), transform)
            }
            start {
                result3 = cache.getResult(inputFile, HashCode.fromInt(123), transform)
            }
            start {
                result4 = cache.getResult(inputFile, HashCode.fromInt(123), transform)
            }
        }

        then:
        result1*.name == ["a.1"]
        result2 == result1
        result3 == result1
        result4 == result1

        and:
        4 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))
        1 * transform.apply(inputFile, _) >> { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        1 * fileAccessTimeJournal.setLastAccessTime(_, _)
        0 * transform._
    }

    def "multiple threads can transform files concurrently"() {
        when:
        async {
            start {
                cache.getResult(new File("a"), HashCode.fromInt(123)) { file, outDir ->
                    instant.a
                    thread.blockUntil.b
                    instant.a_done
                    [new TestFile(outDir, file.getName()).touch()]
                }
            }
            start {
                cache.getResult(new File("b"), HashCode.fromInt(345)) { file, outDir ->
                    instant.b
                    thread.blockUntil.a
                    instant.b_done
                    [new TestFile(outDir, file.getName()).touch()]
                }
            }
        }

        then:
        instant.a_done > instant.b
        instant.b_done > instant.a

        and:
        2 * snapshotter.snapshot(_) >>> [snapshot(HashCode.fromInt(234)), snapshot(HashCode.fromInt(456))]
        2 * fileAccessTimeJournal.setLastAccessTime(_, _)
    }

    def "does not reuse result when transform inputs are different"() {
        def transform1 = Mock(BiFunction)
        def transform2 = Mock(BiFunction)
        def inputFile = tmpDir.file("a")

        given:
        _ * transform1.apply(inputFile, _) >> { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        _ * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))

        cache.getResult(inputFile, HashCode.fromInt(123), transform1)

        when:
        def result = cache.getResult(inputFile, HashCode.fromInt(234), transform2)

        then:
        result*.name == ["a.2"]

        and:
        1 * transform2.apply(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.2"); r.text = "result"; [r] }
        0 * transform1._
        0 * transform2._
        1 * fileAccessTimeJournal.setLastAccessTime(_, _)

        when:
        def result2 = cache.getResult(inputFile, HashCode.fromInt(123), transform1)
        def result3 = cache.getResult(inputFile, HashCode.fromInt(234), transform2)

        then:
        result2*.name == ["a.1"]
        result3 == result

        and:
        0 * transform1._
        0 * transform2._
        0 * fileAccessTimeJournal._
    }

    def "does not reuse result when transform input files have different content"() {
        def transform1 = Mock(BiFunction)
        def transform2 = Mock(BiFunction)
        def inputFile = tmpDir.file("a")

        given:
        _ * transform1.apply(inputFile, _) >> { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        _ * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(234))

        cache.getResult(inputFile, HashCode.fromInt(123), transform1)

        when:
        def result = cache.getResult(inputFile, HashCode.fromInt(345), transform2)

        then:
        result*.name == ["a.2"]

        and:
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform2.apply(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.2"); r.text = "result"; [r] }
        0 * transform1._
        0 * transform2._
        1 * fileAccessTimeJournal.setLastAccessTime(_, _)

        when:
        def result2 = cache.getResult(inputFile, HashCode.fromInt(123), transform1)
        def result3 = cache.getResult(inputFile, HashCode.fromInt(345), transform2)

        then:
        result2*.name == ["a.1"]
        result3 == result

        and:
        2 * snapshotter.snapshot(inputFile) >>> [snapshot(HashCode.fromInt(234)), snapshot(HashCode.fromInt(456))]
        0 * fileAccessTimeJournal.setLastAccessTime(_, _)
        0 * transform1._
        0 * transform2._
    }

    def "runs transform when previous execution failed and cleans up directory"() {
        def transform = Mock(BiFunction)
        def failure = new RuntimeException()
        def inputFile = tmpDir.file("a")

        when:
        cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        def e = thrown(RuntimeException)
        e.is(failure)

        and:
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform.apply(inputFile, _) >>  { File file, File dir ->
            dir.mkdirs()
            new File(dir, "delete-me").text = "broken"
            throw failure
        }
        0 * transform._

        when:
        def result = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        result*.name == ["a.1"]

        and:
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform.apply(inputFile, _) >>  { File file, File dir ->
            assert dir.list().length == 0
            def r = new File(dir, "a.1")
            r.text = "result"
            [r]
        }
        1 * fileAccessTimeJournal.setLastAccessTime(_, _)
        0 * transform._
    }

    def "runs transform when output has been removed"() {
        def transform = Mock(BiFunction)
        def inputFile = tmpDir.file("a")

        given:
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform.apply(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }

        def result = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        when:
        def cache = createCache()
        result.first().delete()
        def result2 = cache.getResult(inputFile, HashCode.fromInt(123), transform)

        then:
        result2 == result

        and:
        1 * snapshotter.snapshot(inputFile) >> snapshot(HashCode.fromInt(456))
        1 * transform.apply(inputFile, _) >>  { File file, File dir -> def r = new File(dir, "a.1"); r.text = "result"; [r] }
        1 * fileAccessTimeJournal.setLastAccessTime(_, _)
        0 * transform._
    }

    def "stopping the cache cleans up old entries and preserves new ones"() {
        given:
        snapshotter.snapshot(_) >> snapshot(HashCode.fromInt(42))
        def filesDir = transformsStoreDirectory.file(CacheLayout.TRANSFORMS_STORE.getKey())
        def file1 = filesDir.file("some.jar", "bac62a0ac6ce00ff016f869e695d5522").createFile("1.txt")
        def file2 = filesDir.file("another.jar", "a89660597ff12d9e0a0397e055e80006").createFile("2.txt")

        when:
        cache.stop()

        then:
        1 * fileAccessTimeJournal.getLastAccessTime(file1.parentFile) >> 0
        1 * fileAccessTimeJournal.getLastAccessTime(file2.parentFile) >> System.currentTimeMillis()

        and:
        !file1.exists()
        file2.exists()
    }

    def snapshot(HashCode hashCode) {
        return new PhysicalFileSnapshot("/path/to/some.txt", "some.txt", hashCode, 0)
    }
}
