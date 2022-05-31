package org.tomlj;

public class TomlDuplicatedKeyException extends TomlParseError {

    @Override
    public synchronized Throwable fillInStackTrace() {
        return null;
    }

    TomlDuplicatedKeyException(TomlParseError cause) {
        super(cause.getMessage(), cause.position(), cause);
    }
}
