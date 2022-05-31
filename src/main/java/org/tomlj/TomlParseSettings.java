package org.tomlj;

public class TomlParseSettings {
    TomlVersion tomlVersion;
    boolean throwParseException;

    static TomlParseSettings DEFAULT = new TomlParseSettings(TomlVersion.LATEST, false);

    public TomlParseSettings(TomlVersion tomlVersion, boolean throwParseException) {
        this.tomlVersion = tomlVersion;
        this.throwParseException = throwParseException;
    }
}
