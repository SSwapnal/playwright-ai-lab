# Walkthrough

This guide takes you from a clean clone to a full migration run on a Mac. Every command below was run
on macOS, and the output excerpts are real. Where yours can differ (timings, timestamps, versions), the
text says so.

The steps, in order:

1. Check the prerequisites
2. Clone and set up
3. Run the legacy Selenium suite (the input)
4. Build the migrator
5. Run the migration in mock mode
6. Run the converted Playwright suite, with the HTML report and trace viewer
7. Read the report and the conversation
8. Find where each stage lives in the code
9. Run the unit tests
10. Switch to a real model
11. Troubleshooting

---

## 1. Prerequisites

| Tool | Needed for | Check with | Version used here |
|---|---|---|---|
| JDK 17 or newer (21 recommended) | building and running the migrator, the legacy suite | `java -version` | `openjdk version "21.0.12.1"` |
| Maven 3.9+ | Java builds | `mvn -v` | `Apache Maven 3.9.16` |
| Node.js 20+ | Playwright | `node -v` | `v26.5.0` |
| npm | Playwright install | `npm -v` | `11.17.0` |
| Google Chrome | only for the legacy Selenium suite | open Chrome > About | any recent version |
| git | cloning | `git --version` | any |

Install anything that is missing with Homebrew:

```bash
brew install openjdk@21 maven node
```

Homebrew's `openjdk@21` is "keg-only", which means macOS will not find it on its own. Point your shell
at it (add these two lines to `~/.zshrc` so every new terminal has them):

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
```

Then check that `java -version` and `mvn -v` both report Java 21:

```
$ java -version
openjdk version "21.0.12.1" 2026-08-18
$ mvn -v
Apache Maven 3.9.16 (...)
...
Java version: 21.0.12.1, ...
```

If `java -version` prints *"Unable to locate a Java Runtime"*, the exports above are missing (see
[Troubleshooting](#11-troubleshooting)).

## 2. Clone and set up

```bash
git clone <repo-url> playwright-ai-lab
cd playwright-ai-lab
```

Install the Playwright project's dependencies and the Chromium browser it uses:

```bash
cd playwright-output
npm ci
npx playwright install chromium
npx playwright --version      # Version 1.63.0
cd ..
```

`npm ci` installs the exact versions in `package-lock.json`. `playwright install chromium` downloads a
browser build of about 95 MB into `~/Library/Caches/ms-playwright`, which only has to happen once.

## 3. Run the legacy Selenium suite (optional)

This is the input: six Selenium 4 + JUnit 5 tests against https://the-internet.herokuapp.com. Running it
first shows you what is being migrated. It needs Chrome installed. Selenium Manager downloads a
matching chromedriver automatically.

```bash
cd legacy-selenium-suite
mvn test
cd ..
```

Expected ending:

```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

You will probably see warnings like *"Unable to find version of CDP to use for 154..."*. They are
harmless. Selenium is only saying it has no DevTools bindings for your exact Chrome version, and these
tests don't use DevTools.

Spend two minutes reading `src/test/java/com/swapnal/legacy/tests/`. Notice the implicit wait in
`BaseTest`, the `WebDriverWait` in `DynamicLoadingTest`, `Select` and `findElements` in
`FormControlsTest`, and the `LoginPage` page object. These are exactly the things the migration has to
translate.

## 4. Build the migrator

```bash
cd migrator
mvn package
```

This compiles the code, runs the unit tests (see step 9), and produces a single runnable jar,
`target/migrator.jar`. Expected ending:

```
[INFO] Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
...
[INFO] BUILD SUCCESS
```

Check that it runs:

```bash
java -jar target/migrator.jar --help
```

## 5. Run the migration in mock mode

Mock mode needs no API key. The model's answers come from files in
`migrator/src/main/resources/mock-responses/`, but everything else is real: the files are written, and
Playwright really runs them against the live site.

To prove the specs are regenerated rather than already there, delete the committed ones first:

```bash
rm -f ../playwright-output/tests/*.spec.ts ../playwright-output/pages/*.ts
LLM_PROVIDER=mock java -jar target/migrator.jar
```

Real output (takes about 20 seconds):

```
LLM: mock (canned responses)
Migrating 3 test class(es): DynamicLoadingTest, FormControlsTest, LoginTest
== DynamicLoadingTest -> tests/dynamic-loading.spec.ts
   attempt 1: 1/1 passed
   result: PASSED after 1 attempt(s)
== FormControlsTest -> tests/form-controls.spec.ts
   attempt 1: 3/3 passed
   result: PASSED after 1 attempt(s)
== LoginTest -> tests/login.spec.ts + pages/LoginPage.ts
   attempt 1: 0/2 passed - 2 failure(s)
   attempt 2: 2/2 passed
   result: PASSED after 2 attempt(s)

Done: 3/3 passing (2 first attempt, 1 after fixes), 0 failed, 0 not run, 0 errors
Report: .../migrator/target/migration-report/migration-report.md
```

### What happened with LoginTest

The Java test checks that the flash message *contains* the expected text:

```java
assertTrue(page.flashMessage().contains("You logged into a secure area!"));
```

The first (canned) conversion translated that as an *exact* match:

```ts
await expect(loginPage.flash).toHaveText('You logged into a secure area!');
```

On the real page, the flash `<div>` also contains whitespace and a "×" close button, so both tests
fail. Attempt 1 takes about 12 seconds because the assertion keeps retrying until the 10-second
`expect` timeout. The migrator then sends this real Playwright error back to the model:

```
### LoginTest > valid login shows secure area
[failed] Error: expect(locator).toHaveText(expected) failed

Locator: locator('#flash')
Timeout: 10000ms
- Expected  - 1
+ Received  + 4

- You logged into a secure area!
+
+             You logged into a secure area!
+             ×
```

The second response corrects the translation to `toContainText(...)`, which is what the Java code
meant, and both tests pass. It fixes the translation instead of deleting the assertion, which is
what the fix prompt asks for.

Useful variations:

```bash
LLM_PROVIDER=mock java -jar target/migrator.jar -t LoginTest        # one class only
LLM_PROVIDER=mock java -jar target/migrator.jar --no-run            # convert, don't execute
LLM_PROVIDER=mock java -jar target/migrator.jar --max-attempts 1    # no second chance: LoginTest ends FAILED
```

The command exits with code 0 only if every class passed (or was converted with `--no-run`), so it can
gate a CI job.

## 6. Run the converted Playwright suite

```bash
cd ../playwright-output
npm run typecheck            # tsc --noEmit: no output means no type errors
npx playwright test
```

```
Running 6 tests using 6 workers
  ✓  ... › DynamicLoadingTest › hidden element appears after loading (1.3s)
  ✓  ... › FormControlsTest › checkboxes can be toggled (1.3s)
  ...
  6 passed (1.9s)
```

Timings vary with your network. The six Selenium tests run sequentially in one JVM, while Playwright
runs one worker per test file in parallel, each in its own isolated browser context.

### HTML report and trace viewer

The local config uses the plain `list` reporter and keeps traces only for failures. To get both a
report and traces for a passing run:

```bash
npx playwright test --trace on --reporter=html
npx playwright show-report
```

`show-report` opens the HTML report in your browser. Click a test, then the trace entry, to open the
**trace viewer**. You can also open a trace directly:

```bash
npx playwright show-trace test-results/login-LoginTest-invalid-password-shows-error-chromium/trace.zip
```

The trace viewer shows every action with DOM snapshots before and after, the network requests, console
output, and how long each auto-wait took. Selenium has no built-in equivalent.

Other handy commands:

```bash
npx playwright test --headed                 # watch the browser
npx playwright test --ui                     # interactive UI mode: pick tests, time-travel through steps
npx playwright test tests/login.spec.ts      # one file
npx playwright test -g "invalid password"    # by title
BASE_URL=http://localhost:5000 npx playwright test   # point at another environment
```

`test-results/` and `playwright-report/` are git-ignored.

## 7. Read the report and the conversation

Everything is under `migrator/target/migration-report/`:

- `migration-report.md` is the human-readable report. It has a summary table, then for each class the
  attempts (with the first line of each failure), the Selenium patterns found in the Java source and
  whether they are gone from the TypeScript, and the model's notes. A copy from a real run is in
  [sample-migration-report.md](sample-migration-report.md).
- `migration-report.json` holds the same data for tools and dashboards.
- `conversations/<TestClass>.md` contains the exact system prompt, the user prompt with all Java
  sources, every model answer and every fix request. Read `LoginTest.md` to see what a model really
  receives. A copy is in [sample-conversation-login.md](sample-conversation-login.md).

## 8. Where each stage lives in the code

All paths are under `migrator/src/main/java/com/swapnal/migrator/`.

| Stage | File | What it does |
|---|---|---|
| Entry point | `MigratorCli.java` | Parses options (picocli), picks the LLM client, wires the pipeline, writes the report, sets the exit code. |
| Collect sources | `source/SourceCollector.java` | Finds classes with `@Test`, then follows imports and same-package type names to pull in page objects (transitively) and the base class. Uses regexes, not a full Java parser. |
| Build the prompt | `prompt/PromptBuilder.java` | `SYSTEM_PROMPT` holds the nine conversion rules and the output format. `conversionPrompt` lists the target files and embeds the Java sources. `fixPrompt` turns a failed run into the follow-up message. |
| Talk to the model | `llm/LlmClient.java`, `llm/AnthropicLlmClient.java`, `llm/MockLlmClient.java`, `llm/LlmClientFactory.java` | One small interface with two implementations. The Anthropic client posts to `/v1/messages` with `java.net.http` and retries 429/5xx with backoff. The mock client reads `mock-responses/<Class>/attempt-<n>.md`. The factory chooses from environment variables. |
| Parse and write | `output/ResponseParser.java`, `output/SpecWriter.java`, `output/TargetPaths.java` | The parser extracts code blocks that start with `// file: <path>` plus the "Notes:" bullets. The writer only accepts `tests/*.spec.ts` and `pages/*.ts`. `TargetPaths` turns `DynamicLoadingTest` into `tests/dynamic-loading.spec.ts`. |
| Run Playwright | `runner/PlaywrightRunner.java`, `runner/PlaywrightJsonParser.java` | Runs `npx playwright test <spec> --reporter=json --retries=0` as a child process and turns the JSON into pass/fail counts and error messages. File-level errors, such as a syntax error, become failures too. |
| Fix loop | `core/MigrationEngine.java` | The heart of the tool. For each class it prompts, parses, writes and runs, then either stops or appends the error to the same conversation and tries again, up to `--max-attempts`. |
| Pattern check | `analysis/SeleniumPattern.java`, `analysis/PatternScanner.java` | Detects Selenium idioms in the Java code. It also looks for their bad Playwright equivalents (`waitForTimeout`, `expect(await x.textContent())`, and so on) in the output. |
| Report | `report/MigrationReport.java`, `report/ReportWriter.java` | Builds the summary counts and writes the markdown and JSON. |

The Playwright side is `playwright-output/playwright.config.ts`. It holds `baseURL`, the 10-second
`expect` timeout (the dynamic-loading page can take about 5 seconds), and traces kept on failure.

## 9. Run the unit tests

```bash
cd migrator
mvn test
```

```
[INFO] Tests run: 7, ... in com.swapnal.migrator.llm.MockLlmClientTest
[INFO] Tests run: 9, ... in com.swapnal.migrator.llm.AnthropicLlmClientTest
[INFO] Tests run: 9, ... in com.swapnal.migrator.core.MigrationEngineTest
...
[INFO] Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
```

These tests need no network, browser or API key:

- `AnthropicLlmClientTest` starts a tiny local HTTP server (`com.sun.net.httpserver`) that pretends to be
  the API. It returns 429s, 529s, 400s and refusals, so the retry logic is tested without real calls.
- `MigrationEngineTest` drives the fix loop with the mock client and a scripted fake runner. The fake
  runner fails first and passes second, or always fails, so no browser is involved.
- `PlaywrightJsonParserTest` parses JSON files captured from real Playwright runs
  (`src/test/resources/playwright/`).
- The input for most tests is a small fake Selenium suite in `src/test/resources/fixture-suite/`.

Run one class with `mvn test -Dtest=MigrationEngineTest`.

## 10. Switch to a real model

```bash
export ANTHROPIC_API_KEY=sk-ant-...      # keep it in your shell, never in a file in the repo
cd migrator
java -jar target/migrator.jar            # provider becomes "anthropic" automatically when a key is set
```

The first line then reads `LLM: anthropic/claude-sonnet-5-5`. Options:

- `LLM_MODEL=claude-opus-5-5` uses a different model.
- `LLM_PROVIDER=mock` forces mock mode even when a key is set.
- `ANTHROPIC_BASE_URL=...` sends requests through a proxy.

With a real model the results are not fixed. A class may pass first time, need two attempts, or fail
after three. That is the point of running the specs. Always read the generated specs and the
conversation before trusting them. A good exercise is to compare `conversations/LoginTest.md` from a
real run with the mock one.

If you set `LLM_PROVIDER=anthropic` without a key, the tool stops immediately:

```
LLM_PROVIDER=anthropic but ANTHROPIC_API_KEY is not set. Export the key or use LLM_PROVIDER=mock.
```

## 11. Troubleshooting

**`java -version` says "Unable to locate a Java Runtime", but `mvn -v` works.**
On macOS, `/usr/bin/java` is a stub that only works when a JDK is registered with the system. Homebrew's
`openjdk@21` is not registered. Maven works because it finds Homebrew's JDK by itself. Export
`JAVA_HOME` and `PATH` as shown in step 1.

**`mvn -v` shows a different Java version (for example 27) than you expect.**
Maven uses `JAVA_HOME` if it is set, and otherwise whatever Homebrew's default `openjdk` is. The project
targets Java 17 bytecode, so newer JDKs build it too. To stay consistent with CI, export the Java 21
`JAVA_HOME`.

**Selenium prints "Unable to find version of CDP to use for ..."**
This is harmless (see step 3). The tests still pass.

**The migrator says "Source directory not found" or "Not a Playwright project (no playwright.config.ts)".**
Run it from the `migrator/` folder. The defaults `../playwright-output` and
`../legacy-selenium-suite/src/test/java` are relative paths. Alternatively, pass `--output` and `--source`.

**Every attempt fails with "Could not start Playwright" or "No tests found".**
Run `npm ci` in `playwright-output/` first. The migrator calls `npx playwright`, which needs the local
install. If the browser is missing, the error says so. Run `npx playwright install chromium`.

**A failing assertion takes 10+ seconds before it is reported.**
That is by design. Web-first assertions retry until the `expect` timeout (10 seconds in this project's
config). It is the same mechanism that makes the dynamic-loading test work without an explicit wait.

**Tests fail with timeouts on every page.**
The demo site is a free public service and is sometimes slow or down. Open
https://the-internet.herokuapp.com in a browser. If it is down, the migrator will report failures that
have nothing to do with the conversion.

**"No mock response for XyzTest".**
You added a new legacy test class, but mock mode only has canned answers for the three original
classes. Add `migrator/src/main/resources/mock-responses/XyzTest/attempt-1.md`, then rebuild the jar
(`mvn package`), because resources are packaged into the jar. Or use a real model.

**A test passes locally but the CI mock job fails.**
CI deletes the committed specs and regenerates them. If you edited a spec by hand, the change is lost
in CI. Change the canned response (or the migrator) instead.
