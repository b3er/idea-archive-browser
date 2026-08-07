package com.github.b3er.idea.plugins.arc.browser.base;

import com.intellij.openapi.vfs.impl.ArchiveHandler;

import java.util.Collections;
import java.util.Map;

/**
 * Bridges ArchiveHandler's erased entry-map hook without linking plugin bytecode to its internal EntryInfo type.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class EntryInfoFreeArchiveHandler extends ArchiveHandler {
    protected EntryInfoFreeArchiveHandler(String path) {
        super(path);
    }

    @Override

    protected final Map createEntriesMap() {
        return Collections.emptyMap();
    }
}
