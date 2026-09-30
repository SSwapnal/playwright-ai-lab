package com.swapnal.migrator.output;

import java.util.List;
import java.util.Optional;

/** Files and free-text notes extracted from a model response. */
public record ParsedResponse(List<GeneratedFile> files, List<String> notes) {

    public ParsedResponse {
        files = List.copyOf(files);
        notes = List.copyOf(notes);
    }

    public Optional<GeneratedFile> file(String path) {
        return files.stream().filter(f -> f.path().equals(path)).findFirst();
    }
}
