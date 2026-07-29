package org.oryxos.core.port;

public interface Sandbox {
    void enforce(Action action) throws DeniedException;

    record Action(String type, String target) {
    }

    final class DeniedException extends RuntimeException {
        public DeniedException(String message) {
            super(message);
        }
    }
}
