package com.swapnal.migrator.output;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts files from fenced code blocks that start with a {@code // file: <path>} comment, and the
 * bullet points that follow a {@code Notes:} line.
 */
public class ResponseParser {

    private static final Pattern FILE_BLOCK = Pattern.compile(
            "```[a-zA-Z]*[ \\t]*\\r?\\n[ \\t]*//\\s*file:\\s*(\\S+)[ \\t]*\\r?\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern NOTES = Pattern.compile("(?im)^\\s*\\**notes:?\\**\\s*$");

    public ParsedResponse parse(String response) {
        List<GeneratedFile> files = new ArrayList<>();
        Matcher m = FILE_BLOCK.matcher(response);
        while (m.find()) {
            String content = m.group(2).stripTrailing() + "\n";
            files.add(new GeneratedFile(m.group(1).replace('\\', '/'), content));
        }
        return new ParsedResponse(files, notes(FILE_BLOCK.matcher(response).replaceAll("")));
    }

    private static List<String> notes(String textOutsideCode) {
        Matcher m = NOTES.matcher(textOutsideCode);
        if (!m.find()) {
            return List.of();
        }
        List<String> notes = new ArrayList<>();
        for (String line : textOutsideCode.substring(m.end()).split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                notes.add(trimmed.substring(2).strip());
            }
        }
        return notes;
    }
}
