package com.reveila.api;

/**
 * Base interface for all Reveila plugins.
 * Plugins are dynamically loaded and must implement this interface to be recognized by the system.
 */
public interface IReveilaPlugin {
    
    /**
     * Called when the plugin is initialized.
     */
    void onInitialize();

    /**
     * Returns the name of the plugin.
     * @return The plugin name.
     */
    String getName();

    /**
     * Returns the version of the plugin.
     * @return The plugin version.
     */
    String getVersion();
}
