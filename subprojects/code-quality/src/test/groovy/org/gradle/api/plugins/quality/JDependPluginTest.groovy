/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality

import static org.gradle.util.Matchers.*
import static org.hamcrest.Matchers.*

import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.util.HelperUtil
import org.gradle.api.tasks.SourceSet

import spock.lang.Specification

import static spock.util.matcher.HamcrestSupport.that
import org.gradle.api.plugins.ReportingBasePlugin

class JDependPluginTest extends Specification {
    Project project = HelperUtil.createRootProject()

    def setup() {
        project.plugins.apply(JDependPlugin)
    }

    def "applies reporting-base plugin"() {
        expect:
        project.plugins.hasPlugin(ReportingBasePlugin)
    }

    def "configures jdepend configuration"() {
        def config = project.configurations.findByName("jdepend")

        expect:
        config != null
        !config.visible
        config.transitive
        config.description == 'The JDepend libraries to be used for this project.'
    }

    def "configures jdepend extension"() {
        expect:
        JDependExtension extension = project.extensions.jdepend
        extension.reportsDir == project.file("build/reports/jdepend")
        !extension.ignoreFailures
    }

    def "configures jdepend task for each source set"() {
        project.plugins.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        expect:
        configuresJDependTask("jdependMain", project.sourceSets.main)
        configuresJDependTask("jdependTest", project.sourceSets.test)
        configuresJDependTask("jdependOther", project.sourceSets.other)
    }

    private void configuresJDependTask(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof JDepend
        task.with {
            assert description == "Run JDepend analysis for ${sourceSet.name} classes"
            assert jdependClasspath == project.configurations.jdepend
            assert classesDir == sourceSet.output.classesDir
            assert reportFile == project.file("build/reports/jdepend/${sourceSet.name}.xml")
            assert ignoreFailures == false
        }
    }

    def "configures any additional JDepend tasks"() {
        def task = project.tasks.add("jdependCustom", JDepend)

        expect:
        task.description == null
        task.classesDir == null
        task.jdependClasspath == project.configurations.jdepend
        task.reportFile == project.file("build/reports/jdepend/custom.xml")
        task.ignoreFailures == false
    }

    def "adds jdepend tasks to check lifecycle task"() {
        project.plugins.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        expect:
        that(project.tasks['check'], dependsOn(hasItems("jdependMain", "jdependTest", "jdependOther")))
    }

    def "can customize settings via extension"() {
        project.plugins.apply(JavaBasePlugin)
        project.sourceSets {
            main
            test
            other
        }

        project.jdepend {
            sourceSets = [project.sourceSets.main]
            reportsDir = project.file("jdepend-reports")
            ignoreFailures = true
        }

        expect:
        hasCustomizedSettings("jdependMain", project.sourceSets.main)
        hasCustomizedSettings("jdependTest", project.sourceSets.test)
        hasCustomizedSettings("jdependOther", project.sourceSets.other)
        that(project.check, dependsOn(hasItem('jdependMain')))
        that(project.check, dependsOn(not(hasItems('jdependTest', 'jdependOther'))))
    }

    def "can customize any additional JDepend tasks via extension"() {
        def task = project.tasks.add("jdependCustom", JDepend)
        project.jdepend {
            reportsDir = project.file("jdepend-reports")
            ignoreFailures = true
        }

        expect:
        task.description == null
        task.classesDir == null
        task.jdependClasspath == project.configurations.jdepend
        task.reportFile == project.file("jdepend-reports/custom.xml")
        task.ignoreFailures == true
    }

    private void hasCustomizedSettings(String taskName, SourceSet sourceSet) {
        def task = project.tasks.findByName(taskName)
        assert task instanceof JDepend
        task.with {
            assert description == "Run JDepend analysis for ${sourceSet.name} classes"
            assert jdependClasspath == project.configurations.jdepend
            assert classesDir == sourceSet.output.classesDir
            assert reportFile == project.file("jdepend-reports/${sourceSet.name}.xml")
            assert ignoreFailures == true
        }
    }
}
