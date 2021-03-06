/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal.plugins;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.plugins.PluginCollection;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.api.specs.Spec;
import org.gradle.internal.UncheckedException;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DefaultPluginContainer<T extends PluginAwareInternal> extends DefaultPluginCollection<Plugin> implements PluginContainer {
    private PluginRegistry pluginRegistry;

    private final LoadingCache<PluginIdLookupCacheKey, Boolean> idLookupCache;

    private final T pluginAware;
    private final List<PluginApplicationAction> pluginApplicationActions;
    private final ModelRuleSourceDetector modelRuleSourceDetector;

    public DefaultPluginContainer(PluginRegistry pluginRegistry, T pluginAware, ModelRuleSourceDetector modelRuleSourceDetector) {
        this(pluginRegistry, pluginAware, Collections.<PluginApplicationAction>emptyList(), modelRuleSourceDetector);
    }

    public DefaultPluginContainer(PluginRegistry pluginRegistry, T pluginAware, List<PluginApplicationAction> pluginApplicationActions, ModelRuleSourceDetector modelRuleSourceDetector) {
        super(Plugin.class);
        this.pluginRegistry = pluginRegistry;
        this.pluginAware = pluginAware;
        this.pluginApplicationActions = pluginApplicationActions;
        idLookupCache = CacheBuilder.newBuilder().build(new PluginIdLookupCacheLoader(pluginRegistry));
        this.modelRuleSourceDetector = modelRuleSourceDetector;
    }

    public Plugin apply(String id) {
        return addPluginInternal(getPluginTypeForId(id));
    }

    public <P extends Plugin> P apply(Class<P> type) {
        return addPluginInternal(type);
    }

    public boolean hasPlugin(String id) {
        return findPlugin(id) != null;
    }

    public boolean hasPlugin(Class<? extends Plugin> type) {
        return findPlugin(type) != null;
    }

    public Plugin findPlugin(String id) {
        try {
            return findPlugin(getPluginTypeForId(id));
        } catch (UnknownPluginException e) {
            return null;
        }
    }

    public <P extends Plugin> P findPlugin(Class<P> type) {
        for (Plugin plugin : this) {
            if (plugin.getClass().equals(type)) {
                return type.cast(plugin);
            }
        }
        return null;
    }

    private <P extends Plugin<?>> P addPluginInternal(Class<P> type) {
        if (findPlugin(type) == null) {
            Plugin plugin = providePlugin(type);
            add(plugin);
            for (PluginApplicationAction onApplyAction : pluginApplicationActions) {
                onApplyAction.execute(new PluginApplication(plugin, pluginAware));
            }
        }
        return type.cast(findPlugin(type));
    }

    public Plugin getPlugin(String id) {
        Plugin plugin = findPlugin(id);
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with id " + id + " has not been used.");
        }
        return plugin;
    }

    public Plugin getAt(String id) throws UnknownPluginException {
        return getPlugin(id);
    }

    public <P extends Plugin> P getAt(Class<P> type) throws UnknownPluginException {
        return getPlugin(type);
    }

    public <P extends Plugin> P getPlugin(Class<P> type) throws UnknownPluginException {
        Plugin plugin = findPlugin(type);
        if (plugin == null) {
            throw new UnknownPluginException("Plugin with type " + type + " has not been used.");
        }
        return type.cast(plugin);
    }

    public void withId(final String pluginId, Action<? super Plugin> action) {
        try {
            Class<?> typeForId = pluginRegistry.getTypeForId(pluginId);
            if (!Plugin.class.isAssignableFrom(typeForId) && modelRuleSourceDetector.hasModelSources(typeForId)) {
                String message = String.format("The type for id '%s' (class: '%s') is a rule source and not a plugin. Use AppliedPlugins.withPlugin() to perform an action if a rule source is applied.", pluginId, typeForId.getName());
                throw new IllegalArgumentException(message);
            }
        } catch (UnknownPluginException e) {
            //just continue if it's impossible to see if the id points to a plain rule source
        }
        matching(new Spec<Plugin>() {
            public boolean isSatisfiedBy(Plugin element) {
                try {
                    return idLookupCache.get(new PluginIdLookupCacheKey(element.getClass(), pluginId));
                } catch (ExecutionException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }).all(action);
    }

    protected Class<? extends Plugin> getPluginTypeForId(String id) {
        return pluginRegistry.getPluginTypeForId(id);
    }

    private Plugin<T> providePlugin(Class<? extends Plugin<?>> type) {
        @SuppressWarnings("unchecked") Plugin<T> plugin = (Plugin<T>) pluginRegistry.loadPlugin(type);
        plugin.apply(pluginAware);
        return plugin;
    }

    @Override
    public <S extends Plugin> PluginCollection<S> withType(Class<S> type) {
        if (!Plugin.class.isAssignableFrom(type) && modelRuleSourceDetector.hasModelSources(type)) {
            String message = String.format("'%s' is a rule source and not a plugin. Use AppliedPlugins.withPlugin() to perform an action if a rule source is applied.", type.getName());
            throw new IllegalArgumentException(message);
        }
        return super.withType(type);
    }
}
