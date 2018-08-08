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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

/**
 * https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-dependencies
 */
class BomSupportPluginsSmokeTest extends AbstractSmokeTest {
    static bomVersion = "2.0.4.RELEASE"
    static bom = "'org.springframework.boot:spring-boot-dependencies:${bomVersion}'"
    // This comes from the BOM
    static springVersion = "5.0.8.RELEASE"

    @Unroll
    def 'bom support is provided by #bomSupportProvider'() {
        given:
        def springVersion = springVersion
        def bomVersion = bomVersion

        def settingsFile = testProjectDir.newFile('settings.gradle')
        settingsFile << """
            rootProject.name = 'springbootproject'
        """
        FeaturePreviewsFixture.enableImprovedPomSupport(settingsFile)
        def buildScript = """
            plugins {
                id "java"
                ${dependencyManagementPlugin}
            }
            ${jcenterRepository()}

            ${bomDeclaration}

            dependencies {
                implementation "org.springframework.boot:spring-boot"
                implementation "org.springframework.boot:spring-boot-autoconfigure"
            
                testImplementation "junit:junit"
                testImplementation "org.springframework:spring-test"
                testImplementation "org.springframework.boot:spring-boot-test"
                testImplementation "org.springframework.boot:spring-boot-test-autoconfigure"
            }
        """
        def resolve = new ResolveTestFixture(new TestFile(buildFile), 'testCompileClasspath')
        resolve.prepare(buildScript)

        when:
        runner('checkDep').build()

        then:
        def junitDeps = {
            module("org.hamcrest:hamcrest-core:1.3").byReason(reason3)
        }
        def springCoreDeps = {
            module("org.springframework:spring-jcl:${springVersion}")
        }
        def springExpressionDeps = {
            module("org.springframework:spring-core:${springVersion}")
        }
        def springBeansDeps = {
            module("org.springframework:spring-core:${springVersion}")
        }
        def springAopDeps = {
            module("org.springframework:spring-beans:${springVersion}")
            module("org.springframework:spring-core:${springVersion}")
        }
        def springContextDeps = {
            module("org.springframework:spring-aop:${springVersion}", directBomDependency ? {} : springAopDeps).byReason(reason3)
            module("org.springframework:spring-beans:${springVersion}", directBomDependency ? {} : springBeansDeps).byReason(reason3)
            module("org.springframework:spring-core:${springVersion}",)
            module("org.springframework:spring-expression:${springVersion}", directBomDependency ? {} : springExpressionDeps).byReason(reason3)
        }
        def springTestDeps = {
            module("org.springframework:spring-aop:${springVersion}")
            module("org.springframework:spring-beans:${springVersion}")
            module("org.springframework:spring-context:${springVersion}")
            module("org.springframework:spring-core:${springVersion}")
            module("junit:junit:4.12")
            module("org.hamcrest:hamcrest-core:1.3")
        }
        def springBootDeps = {
            module("org.springframework:spring-core:${springVersion}", directBomDependency ? {} : springCoreDeps).byReason(reason3)
            module("org.springframework:spring-context:${springVersion}", directBomDependency ? {} : springContextDeps).byReason(reason3)
            module("org.springframework:spring-test:${springVersion}")
            module("junit:junit:4.12")
        }
        def springBootAutoconfigureDeps = {
            module("org.springframework.boot:spring-boot:$bomVersion")
        }
        def springBootTestDeps = {
            module("org.springframework.boot:spring-boot:$bomVersion")
            module("org.springframework:spring-test:${springVersion}")
            module("junit:junit:4.12")
            module("org.hamcrest:hamcrest-core:1.3")
        }
        def springBootTestAutoconfigureDeps = {
            module("org.springframework.boot:spring-boot-test:$bomVersion")
            module("org.springframework.boot:spring-boot-autoconfigure:$bomVersion")
            module("org.springframework:spring-test:${springVersion}")
        }

        resolve.expectDefaultConfiguration('compile')
        resolve.expectGraph {
            root(':', ':springbootproject:') {
                if (directBomDependency) {
                    module("org.springframework.boot:spring-boot-dependencies:$bomVersion") {
                        module("org.springframework:spring-core:${springVersion}", springCoreDeps)
                        module("org.springframework:spring-aop:${springVersion}", springAopDeps)
                        module("org.springframework:spring-beans:${springVersion}", springBeansDeps)
                        module("org.springframework:spring-context:${springVersion}", springContextDeps)
                        module("org.springframework:spring-expression:${springVersion}", springExpressionDeps)
                        module("org.springframework:spring-test:${springVersion}")
                        module("org.springframework:spring-jcl:${springVersion}")
                        module("org.springframework.boot:spring-boot:$bomVersion")
                        module("org.springframework.boot:spring-boot-test:$bomVersion")
                        module("org.springframework.boot:spring-boot-autoconfigure:$bomVersion")
                        module("org.springframework.boot:spring-boot-test-autoconfigure:$bomVersion")
                        module("junit:junit:4.12")
                        module("org.hamcrest:hamcrest-core:1.3")
                    }.noArtifacts()
                }
                edge("org.springframework.boot:spring-boot-test-autoconfigure", "org.springframework.boot:spring-boot-test-autoconfigure:$bomVersion", springBootTestAutoconfigureDeps).byReason(reason1)
                edge("org.springframework.boot:spring-boot-test", "org.springframework.boot:spring-boot-test:$bomVersion", springBootTestDeps).byReason(reason2)
                edge("org.springframework.boot:spring-boot-autoconfigure", "org.springframework.boot:spring-boot-autoconfigure:$bomVersion", springBootAutoconfigureDeps).byReason(reason2)
                edge("org.springframework.boot:spring-boot", "org.springframework.boot:spring-boot:$bomVersion", springBootDeps).byReason(reason2)
                edge("org.springframework:spring-test", "org.springframework:spring-test:${springVersion}", springTestDeps).byReason(reason2)
                edge("junit:junit", "junit:junit:4.12", junitDeps).byReason(reason2)
            }
        }

        where:
        bomSupportProvider                    | directBomDependency | reason1            | reason2            | reason3            | bomDeclaration                                        | dependencyManagementPlugin
        "gradle"                              | true                | "requested"        | "requested"        | "requested"        | "dependencies { implementation $bom }"                | ""
        "nebula recommender plugin"           | false               | "requested"        | "requested"        | "requested"        | "dependencyRecommendations { mavenBom module: $bom }" | "id 'nebula.dependency-recommender' version '${TestedVersions.nebulaDependencyRecommender}'"
        "spring dependency management plugin" | false               | "selected by rule" | "selected by rule" | "selected by rule" | "dependencyManagement { imports { mavenBom $bom } }"  | "id 'io.spring.dependency-management' version '${TestedVersions.springDependencyManagement}'"
    }
}
