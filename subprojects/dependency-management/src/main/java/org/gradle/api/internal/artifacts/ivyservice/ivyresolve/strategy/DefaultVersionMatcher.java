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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy;

public class DefaultVersionMatcher implements VersionMatcher {
    public VersionSelector createSelector(String selectorString) {
        if (VersionRangeSelector.ALL_RANGE.matcher(selectorString).matches()) {
            return new VersionRangeSelector(selectorString);
        }

        if (selectorString.endsWith("+")) {
            return new SubVersionSelector(selectorString);
        }

        if (selectorString.startsWith("latest.")) {
            return new LatestVersionSelector(selectorString);
        }

        return new ExactVersionSelector(selectorString);
    }

    public int compare(String selector, String candidate) {
        return createSelector(selector).compare(selector, candidate);
    }
}
