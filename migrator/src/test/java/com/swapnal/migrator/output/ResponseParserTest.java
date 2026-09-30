package com.swapnal.migrator.output;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseParserTest {

    private final ResponseParser parser = new ResponseParser();

    @Test
    void extractsFilesAndNotes() {
        String response = """
                Here you go.

                ```ts
                // file: pages/LoginPage.ts
                export class LoginPage {}
                ```

                ```typescript
                // file: tests/login.spec.ts
                import { test } from '@playwright/test';
                test('a', async () => {});
                ```

                Notes:
                - Dropped the implicit wait.
                * Used toContainText.
                """;

        ParsedResponse parsed = parser.parse(response);

        assertEquals(List.of("pages/LoginPage.ts", "tests/login.spec.ts"),
                parsed.files().stream().map(GeneratedFile::path).toList());
        assertEquals("export class LoginPage {}\n", parsed.file("pages/LoginPage.ts").orElseThrow().content());
        assertEquals(List.of("Dropped the implicit wait.", "Used toContainText."), parsed.notes());
    }

    @Test
    void codeBlocksWithoutFileHeaderAreIgnored() {
        ParsedResponse parsed = parser.parse("```ts\nconst x = 1;\n```\n");

        assertTrue(parsed.files().isEmpty());
        assertTrue(parsed.notes().isEmpty());
    }

    @Test
    void bulletsInsideCodeAreNotTreatedAsNotes() {
        String response = """
                ```ts
                // file: tests/a.spec.ts
                // Notes:
                // - not a note
                ```
                **Notes:**
                - real note
                """;

        assertEquals(List.of("real note"), parser.parse(response).notes());
    }
}
