/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.execution

import org.gradle.api.BuildCancelledException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.execution.taskpath.ResolvedTaskPath
import org.gradle.initialization.BuildCancellationToken
import spock.lang.Specification

class TaskPathProjectEvaluatorTest extends Specification {
    private cancellationToken = Mock(BuildCancellationToken)
    private project = Mock(ProjectInternal)
    private evaluator = new TaskPathProjectEvaluator(cancellationToken)

    def "project configuration fails when cancelled"() {
        given:
        cancellationToken.cancellationRequested >> true

        when:
        evaluator.configure(project)

        then:
        BuildCancelledException e = thrown()

        and:
        0 * project._
    }

    def "project hierarchy configuration fails when cancelled"() {
        def child1 = Mock(ProjectInternal)
        def child2 = Mock(ProjectInternal)
        def subprojects = [child1, child2]

        given:
        project.subprojects >> subprojects
        cancellationToken.cancellationRequested >>> [false, false, true]

        when:
        evaluator.configureHierarchy(project)

        then:
        BuildCancelledException e = thrown()

        and:
        1 * project.evaluate()
        1 * child1.evaluate()
        0 * child2._
    }

    def "evaluates project when task path is qualified"() {
        def path = Mock(ResolvedTaskPath)
        def fooProject = Mock(ProjectInternal)

        when:
        evaluator.configureForPath(path)

        then:
        1 * path.qualified >> true
        1 * path.project >> fooProject
        1 * fooProject.evaluate()
        0 * fooProject._
    }

    def "evaluates all projects when task path is not qualified"() {
        def path = Mock(ResolvedTaskPath)
        def subprojects = [Mock(ProjectInternal), Mock(ProjectInternal)]

        when:
        evaluator.configureForPath(path)

        then:
        1 * path.project >> project
        1 * path.qualified >> false

        and:
        1 * project.evaluate()
        1 * project.subprojects >> subprojects
        1 * subprojects[0].evaluate()
        1 * subprojects[1].evaluate()
        0 * project._
    }
}
