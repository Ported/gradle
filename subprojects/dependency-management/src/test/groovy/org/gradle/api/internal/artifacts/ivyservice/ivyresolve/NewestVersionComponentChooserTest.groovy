/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve

import org.gradle.api.artifacts.ComponentMetadata
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector
import org.gradle.internal.rules.ClosureBackedRuleAction
import org.gradle.internal.rules.SpecRuleAction
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher
import org.gradle.internal.component.model.DependencyMetaData
import org.gradle.internal.component.model.ComponentResolveMetaData
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData
import org.gradle.internal.resolve.result.DefaultBuildableModuleComponentMetaDataResolveResult
import org.gradle.internal.resolve.result.ModuleVersionListing
import spock.lang.Specification

class NewestVersionComponentChooserTest extends Specification {
    def versionMatcher = Mock(VersionMatcher)
    def latestStrategy = Mock(LatestStrategy)
    def componentSelectionRules = Mock(ComponentSelectionRulesInternal)

    def chooser = new NewestVersionComponentChooser(latestStrategy, versionMatcher, componentSelectionRules)

    def "uses version matcher to determine if selector can select multiple components"() {
        def selector = Mock(ModuleVersionSelector)
        when:
        1 * selector.version >> "foo"
        versionMatcher.createSelector("foo") >> {
            Stub(VersionSelector) {
                isDynamic() >> false
            }
        }

        then:
        !chooser.canSelectMultipleComponents(selector)

        when:
        1 * selector.version >> "bar"
        versionMatcher.createSelector("bar") >> {
            Stub(VersionSelector) {
                isDynamic() >> true
            }
        }

        then:
        chooser.canSelectMultipleComponents(selector)
    }

    def "chooses latest version for component meta data"() {
        def one = Stub(ComponentResolveMetaData) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }
        def two = Stub(ComponentResolveMetaData) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.1")
        }

        when:
        1 * latestStrategy.compare({it.version == "1.0"} as Versioned, {it.version == "1.1"} as Versioned) >> -1
        0 * componentSelectionRules.apply(_,_)

        then:
        chooser.choose(one, two) == two

        when:
        1 * latestStrategy.compare({it.version == "1.0"} as Versioned, {it.version == "1.1"} as Versioned) >> 1
        0 * componentSelectionRules.apply(_,_)

        then:
        chooser.choose(one, two) == one
    }

    def "chooses non-generated descriptor over generated"() {
        def one = Mock(ComponentResolveMetaData) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.0")
        }
        def two = Mock(ComponentResolveMetaData) {
            getId() >> DefaultModuleVersionIdentifier.newId("group", "name", "1.1")
        }

        when:
        1 * latestStrategy.compare({it.version == "1.0"} as Versioned, {it.version == "1.1"} as Versioned) >> 0
        1 * one.generated >> true
        1 * two.generated >> false
        0 * componentSelectionRules.apply(_,_)

        then:
        chooser.choose(one, two) == two

        when:
        1 * latestStrategy.compare({it.version == "1.0"} as Versioned, {it.version == "1.1"} as Versioned) >> 0
        1 * one.generated >> false
        0 * componentSelectionRules.apply(_,_)

        then:
        chooser.choose(one, two) == one
    }

    def "chooses newest matching version without requiring metadata"() {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "1.+")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        1 * versionMatcher.createSelector("1.+") >> {
            Stub(VersionSelector) {
                requiresMetadata() >> false
                accept("2.0") >> false
                accept("1.3") >> true
            }
        }
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        1 * componentSelectionRules.rules >> []
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
    }

    def "chooses newest matching version requiring metadata"() {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "latest.milestone")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        _ * dependency.withRequestedVersion(_) >> Stub(DependencyMetaData)
        _ * repo.resolveComponentMetaData(_, _, _) >> { moduleVersionDep, candidateId, DefaultBuildableModuleComponentMetaDataResolveResult result ->
            result.resolved(Stub(MutableModuleComponentResolveMetaData) {
                getId() >> { DefaultModuleVersionIdentifier.newId(candidateId) }
            })
        }
        1 * versionMatcher.createSelector("latest.milestone") >> {
            Stub(VersionSelector) {
                requiresMetadata() >> true
                accept({ComponentMetadata md -> md.id.version == "2.0"}) >> false
                accept({ComponentMetadata md -> md.id.version == "1.3"}) >> true
            }
        }
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        1 * componentSelectionRules.rules >> []
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == DefaultModuleComponentIdentifier.newId("group", "name", "1.3")
    }

    def "rejects dynamic version by rule without metadata" () {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "1.+")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        1 * versionMatcher.createSelector("1.+") >> {
            Stub(VersionSelector) {
                requiresMetadata() >> false
                matchesUniqueVersion() >> false
                accept(_) >> { String version ->
                    // 1.+ should return 1.3 according to this version matcher
                    switch(version) {
                        case "2.0": return false
                        case "1.3": return true
                        case "1.2": return true
                        default: return false
                    }
                }
            }
        }
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        1 * componentSelectionRules.rules >> rules({ComponentSelection selection ->
            if (selection.candidate.version == '1.3') {
                selection.reject("rejected")
            }
        })
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == DefaultModuleComponentIdentifier.newId("group", "name", "1.2")
    }

    def "rejects dynamic version by rule with metadata" () {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "latest.release")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        _ * dependency.withRequestedVersion(_) >> Stub(DependencyMetaData)
        _ * repo.resolveComponentMetaData(_, _, _) >> { moduleVersionDep, candidateId, DefaultBuildableModuleComponentMetaDataResolveResult result ->
            result.resolved(Stub(MutableModuleComponentResolveMetaData) {
                getComponentId() >> { candidateId }
            })
        }
        1 * versionMatcher.createSelector("latest.release") >> {
            Stub(VersionSelector) {
                requiresMetadata() >> true
                matchesUniqueVersion() >> true
                accept(_) >> { ComponentMetadata metadata ->
                    // latest.integration should return 1.3 according to this version matcher
                    switch(metadata.id.version) {
                        case "2.0": return false
                        case "1.3": return true
                        case "1.2": return true
                        default: return false
                    }
                }
            }
        }
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        1 * componentSelectionRules.rules >> rules({ComponentSelection selection ->
            if (selection.candidate.version == '1.3') {
                selection.reject("rejected")
            }
        })
        0 * _

        then:
        // Since 1.3 is "latest.release" but it's rejected by rule, we should fail to resolve
        chooser.choose(listing, dependency, repo) == null

    }

    def "rejects static version by selection rule" () {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "1.3")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        1 * versionMatcher.createSelector("1.3") >> {
            Stub(VersionSelector) {
                requiresMetadata() >> false
                matchesUniqueVersion() >> true
                accept("2.0") >> false
                accept("1.3") >> true
            }
        }
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        1 * componentSelectionRules.rules >> rules({ ComponentSelection cs ->
            cs.reject("reason")
        })
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == null
    }

    def "returns null when no versions match without metadata"() {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "1.3")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        1 * versionMatcher.createSelector("1.3") >> {
            Stub(VersionSelector) {
                requiresMetadata() >> false
                accept(_) >> false
            }
        }
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        1 * componentSelectionRules.rules >> []
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == null
    }

    def "returns null when no versions are chosen with metadata"() {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "latest.release")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        _ * dependency.withRequestedVersion(_) >> Stub(DependencyMetaData)
        _ * repo.resolveComponentMetaData(_, _, _) >> { moduleVersionDep, candidateId, DefaultBuildableModuleComponentMetaDataResolveResult result ->
            result.resolved(Stub(MutableModuleComponentResolveMetaData) {
                getComponentId() >> { candidateId }
            })
        }
        1 * versionMatcher.createSelector("latest.release") >> {
            Stub(VersionSelector) {
                requiresMetadata() >> true
                accept(_) >> false
            }
        }
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        1 * componentSelectionRules.rules >> []
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == null
    }

    def "returns null when all matching versions match are rejected by rule"() {
        given:
        def selector = new DefaultModuleVersionSelector("group", "name", "latest.integration")
        def listing = Mock(ModuleVersionListing)
        def dependency = Mock(DependencyMetaData)
        def repo = Mock(ModuleComponentRepositoryAccess)
        def versions = [new VersionInfo("1.2"), new VersionInfo("1.3"), new VersionInfo("2.0")]

        when:
        _ * dependency.getRequested() >> selector
        1 * versionMatcher.createSelector("latest.integration") >> {
            Stub(VersionSelector) {
                requiresMetadata() >> false
                matchesUniqueVersion() >> true
                accept(_) >> true
            }
        }
        1 * listing.versions >> (versions as Set)
        1 * latestStrategy.sort(_) >> versions
        1 * componentSelectionRules.rules >> rules({ ComponentSelection selection ->
            selection.reject("Rejecting everything")
        })
        0 * _

        then:
        chooser.choose(listing, dependency, repo) == null
    }

    def rules(Closure closure) {
        return [
            new SpecRuleAction<ComponentSelection>(
                    new ClosureBackedRuleAction<ComponentSelection>(ComponentSelection, closure),
                    Specs.<ComponentSelection>satisfyAll()
            )
        ]
    }
}
