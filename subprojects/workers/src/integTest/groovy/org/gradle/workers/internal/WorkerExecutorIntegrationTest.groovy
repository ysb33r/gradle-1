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

package org.gradle.workers.internal

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.timeout.IntegrationTestTimeout
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.junit.Rule
import spock.lang.Unroll

@IntegrationTestTimeout(120)
@Unroll
class WorkerExecutorIntegrationTest extends AbstractWorkerExecutorIntegrationTest {

    @Rule
    public final BlockingHttpServer blockingServer = new BlockingHttpServer()

    def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

    def "can create and use a worker runnable defined in buildSrc in #isolationMode"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        when:
        succeeds("runInWorker")

        then:
        assertRunnableExecuted("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can create and use a worker runnable defined in build script in #isolationMode"() {
        withRunnableClassInBuildScript()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        when:
        succeeds("runInWorker")

        then:
        assertRunnableExecuted("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can create and use a worker runnable defined in an external jar in #isolationMode"() {
        def runnableJarName = "runnable.jar"
        withRunnableClassInExternalJar(file(runnableJarName))

        buildFile << """
            buildscript {
                dependencies {
                    classpath files("$runnableJarName")
                }
            }

            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        when:
        succeeds("runInWorker")

        then:
        assertRunnableExecuted("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "re-uses an existing idle worker daemon"() {
        executer.withWorkerDaemonsExpirationDisabled()
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
            }

            task reuseDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                dependsOn runInDaemon
            }
        """

        when:
        succeeds("reuseDaemon")

        then:
        assertSameDaemonWasUsed("runInDaemon", "reuseDaemon")
    }

    def "starts a new worker daemon when existing worker daemons are incompatible"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask)

            task startNewDaemon(type: WorkerTask) {
                dependsOn runInDaemon
                isolationMode = IsolationMode.PROCESS

                // Force a new daemon to be used
                additionalForkOptions = {
                    it.systemProperty("foo", "bar")
                }
            }
        """

        when:
        succeeds("startNewDaemon")

        then:
        assertDifferentDaemonsWereUsed("runInDaemon", "startNewDaemon")
    }

    def "starts a new worker daemon when there are no idle compatible worker daemons available"() {
        blockingServer.start()
        blockingServer.expectConcurrent("runInDaemon", "startNewDaemon")

        withRunnableClassInBuildSrc()
        withBlockingRunnableClassInBuildSrc("http://localhost:${blockingServer.port}")

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                runnableClass = BlockingRunnable.class
            }

            task startNewDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                runnableClass = BlockingRunnable.class
            }

            task runAllDaemons {
                dependsOn runInDaemon, startNewDaemon
            }
        """

        when:
        args("--parallel")
        succeeds("runAllDaemons")

        then:
        assertDifferentDaemonsWereUsed("runInDaemon", "startNewDaemon")
    }

    def "re-uses an existing compatible worker daemon when a different runnable is executed"() {
        executer.withWorkerDaemonsExpirationDisabled()
        withRunnableClassInBuildSrc()
        withAlternateRunnableClassInBuildSrc()

        buildFile << """
            task runInDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
            }

            task reuseDaemon(type: WorkerTask) {
                isolationMode = IsolationMode.PROCESS
                runnableClass = AlternateRunnable.class
                dependsOn runInDaemon
            }
        """

        when:
        succeeds("reuseDaemon")

        then:
        assertSameDaemonWasUsed("runInDaemon", "reuseDaemon")
    }

    def "throws if worker used from a thread with no current build operation in #isolationMode"() {
        given:
        withRunnableClassInBuildSrc()

        and:
        buildFile << """
            class WorkerTaskUsingCustomThreads extends WorkerTask {
                @TaskAction
                void executeTask() {
                    def thrown = null
                    def customThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                workerExecutor.submit(runnableClass) { config ->
                                    config.isolationMode = $isolationMode
                                    if (isolationMode == IsolationMode.PROCESS) {
                                        forkOptions.maxHeapSize = "64m"
                                    }
                                    config.forkOptions(additionalForkOptions)
                                    config.classpath(additionalClasspath)
                                    config.params = [ list.collect { it as String }, new File(outputFileDirPath), foo ]
                                }.get()
                            } catch(Exception ex) {
                                thrown = ex
                            }
                        }
                    })
                    customThread.start()
                    customThread.join()
                    if(thrown) {
                        throw thrown
                    }
                }
            }

            task runInWorker(type: WorkerTaskUsingCustomThreads)
        """.stripIndent()

        when:
        fails 'runInWorker'

        then:
        failure.assertHasCause 'An attempt was made to submit work from a thread not managed by Gradle.  Work may only be submitted from a Gradle-managed thread.'

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can set a custom display name for work items in #isolationMode"() {
        withRunnableClassInBuildSrc()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
                displayName = "Test Work"
            }
        """.stripIndent()

        when:
        succeeds("runInWorker")

        then:
        buildOperations.hasOperation("Test Work")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can use a parameter that references classes in other packages in #isolationMode"() {
        withRunnableClassInBuildSrc()
        withParameterClassReferencingClassInAnotherPackage()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                isolationMode = $isolationMode
            }
        """

        expect:
        succeeds("runInWorker")

        where:
        isolationMode << ISOLATION_MODES
    }

    def "can set isolation mode using fork mode"() {
        executer.withWorkerDaemonsExpirationDisabled()
        withRunnableClassInBuildScript()

        buildFile << """
            task runInWorker(type: WorkerTask) {
                def daemonCount = 0
                forkMode = ForkMode.ALWAYS
                
                doFirst {
                    daemonCount = services.get(org.gradle.workers.internal.WorkerDaemonClientsManager.class).allClients.size()
                }
                
                doLast {
                    assert services.get(org.gradle.workers.internal.WorkerDaemonClientsManager.class).allClients.size() > daemonCount
                }
            }
        """

        expect:
        succeeds("runInWorker")
    }

    def "classloader is not isolated when using IsolationMode.NONE"() {
        withRunnableClassInBuildScript()

        buildFile << """
            class MutableItem {
                static String value = "foo"
            }
            
            class MutatingRunnable extends TestRunnable {
                final String value
                
                @Inject
                public MutatingRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                    this.value = files[0]
                }
                
                public void run() {
                    MutableItem.value = value
                }
            }
            
            task mutateValue(type: WorkerTask) {
                list = [ "bar" ]
                isolationMode = IsolationMode.NONE
                runnableClass = MutatingRunnable.class
            } 
            
            task verifyNotIsolated {
                dependsOn mutateValue
                doLast {
                    assert MutableItem.value == "bar"
                }
            }
        """

        expect:
        succeeds "verifyNotIsolated"
    }

    void withParameterClassReferencingClassInAnotherPackage() {
        file("buildSrc/src/main/java/org/gradle/another/Bar.java").text = """
            package org.gradle.another;
            
            import java.io.Serializable;
            
            public class Bar implements Serializable { }
        """

        file("buildSrc/src/main/java/org/gradle/other/Foo.java").text = """
            package org.gradle.other;

            import java.io.Serializable;
            import org.gradle.another.Bar;

            public class Foo implements Serializable { 
                Bar bar = new Bar();
            }
        """
    }

    String getBlockingRunnableThatCreatesFiles(String url) {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.net.URL;
            import javax.inject.Inject;

            public class BlockingRunnable extends TestRunnable {
                @Inject
                public BlockingRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                }

                public void run() {
                    super.run();
                    try {
                        new URL("$url/" + outputDir.getName()).openConnection().getHeaderField("RESPONSE");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """
    }

    String getAlternateRunnable() {
        return """
            import java.io.File;
            import java.util.List;
            import org.gradle.other.Foo;
            import java.net.URL;
            import javax.inject.Inject;

            public class AlternateRunnable extends TestRunnable {
                @Inject
                public AlternateRunnable(List<String> files, File outputDir, Foo foo) {
                    super(files, outputDir, foo);
                }
            }
        """
    }

    void withBlockingRunnableClassInBuildSrc(String url) {
        file("buildSrc/src/main/java/org/gradle/test/BlockingRunnable.java") << """
            package org.gradle.test;

            ${getBlockingRunnableThatCreatesFiles(url)}
        """

        addImportToBuildScript("org.gradle.test.BlockingRunnable")
    }

    void withAlternateRunnableClassInBuildSrc() {
        file("buildSrc/src/main/java/org/gradle/test/AlternateRunnable.java") << """
            package org.gradle.test;

            $alternateRunnable
        """

        addImportToBuildScript("org.gradle.test.AlternateRunnable")
    }
}
