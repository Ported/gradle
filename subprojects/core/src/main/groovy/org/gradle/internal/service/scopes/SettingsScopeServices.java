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

package org.gradle.internal.service.scopes;

import org.gradle.api.internal.DependencyInjectingInstantiator;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.file.BaseDirFileResolver;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.plugins.*;
import org.gradle.api.plugins.AppliedPlugins;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.initialization.DefaultProjectDescriptorRegistry;
import org.gradle.initialization.ProjectDescriptorRegistry;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;

public class SettingsScopeServices extends DefaultServiceRegistry {
    private final SettingsInternal settings;

    public SettingsScopeServices(ServiceRegistry parent, final SettingsInternal settings) {
        super(parent);
        this.settings = settings;
    }

    protected FileResolver createFileResolver() {
        return new BaseDirFileResolver(get(FileSystem.class), settings.getSettingsDir());
    }

    protected PluginRegistry createPluginRegistry(PluginRegistry parentRegistry) {
        return parentRegistry.createChild(settings.getClassLoaderScope(), new DependencyInjectingInstantiator(this), get(ModelRuleSourceDetector.class));
    }

    protected PluginContainer createPluginContainer() {
        return new DefaultPluginContainer<SettingsInternal>(get(PluginRegistry.class), settings, get(ModelRuleSourceDetector.class));
    }

    protected DefaultAppliedPluginContainer createPluginApplicationHandler() {
        return new DefaultAppliedPluginContainer(settings, get(PluginRegistry.class), get(ModelRuleSourceDetector.class));
    }

    protected AppliedPlugins createAppliedPlugins() {
        return new DefaultAppliedPlugins(get(AppliedPluginContainer.class), get(PluginRegistry.class));
    }

    protected ProjectDescriptorRegistry createProjectDescriptorRegistry() {
        return new DefaultProjectDescriptorRegistry();
    }
}
