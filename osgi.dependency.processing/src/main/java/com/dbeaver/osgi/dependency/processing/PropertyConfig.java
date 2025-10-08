package com.dbeaver.osgi.dependency.processing;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * PropertyConfig
 * Record for additional properties configuration
 * Example:
 * [
 * {"uids": ["config_uid1", "config_uid2"], "type": "cli", "properties": {"password": "mypassword", "user": "myuser"}},
 * ]
 *
 * @param uids list of product uids (to apply the properties to)
 * @param name name of the config set
 * @param type type of the properties (env or cli)
 * @param properties map of properties
 */
public record PropertyConfig(
    List<String> uids,
    String name,
    Type type,
    Map<String, String> properties
) {
    public enum Type {
        @SerializedName("env")
        ENV,
        @SerializedName("cli")
        CLI
    }
}
