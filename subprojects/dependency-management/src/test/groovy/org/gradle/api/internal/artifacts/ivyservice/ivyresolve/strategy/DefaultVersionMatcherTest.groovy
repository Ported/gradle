/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy

import spock.lang.Specification

class DefaultVersionMatcherTest extends Specification {
    VersionMatcher matcher = new DefaultVersionMatcher()

    def "creates version range selector" () {
        expect:
        matcher.createSelector(selector) instanceof VersionRangeSelector
        
        where:
        selector << [
            "[1.0,2.0]",
            "[1.0,2.0[",
            "]1.0,2.0]",
            "]1.0,2.0[",
            "[1.0,)",
            "]1.0,)",
            "(,2.0]",
            "(,2.0[",
        ]
    }
    
    def "creates sub version selector" () {
        expect:
        matcher.createSelector(selector) instanceof SubVersionSelector

        where:
        selector << [
            "1+",
            "1.2.3+"
        ]
    }

    def "creates latest version selector" () {
        expect:
        matcher.createSelector(selector) instanceof LatestVersionSelector

        where:
        selector << [
            "latest.integration",
            "latest.foo",
            "latest.123"
        ]
    }

    def "creates exact version selector as default" () {
        expect:
        matcher.createSelector(selector) instanceof ExactVersionSelector

        where:
        selector << [
            "1.0",
            "!@#%",
            "1",
            "1.+.3",
            "[1",
            "[]",
            "[1,2,3]",
        ]
    }
}
