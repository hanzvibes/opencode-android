package dev.bidzz.opencode.ui.onboarding

import java.io.File

/**
 * Seeds a tiny sample project into the workspace so the empty-state
 * starters make sense on a first run.
 *
 * Three small files, nothing precious: a README with things to ask the
 * agent, a `main.py` to explain and change, and a TODO list of first
 * prompts. Existing files are never overwritten — seeding into a
 * workspace that already has a README leaves it alone.
 *
 * Returns the workspace directory on success, null when nothing could be
 * written (e.g. the directory is not writable).
 */
object SampleProject {

    fun seed(workspace: File): File? {
        return try {
            workspace.mkdirs()
            writeIfAbsent(File(workspace, "README.md"), README)
            writeIfAbsent(File(workspace, "main.py"), MAIN_PY)
            writeIfAbsent(File(workspace, "TODO.md"), TODO)
            workspace
        } catch (_: Exception) {
            null
        }
    }

    private fun writeIfAbsent(file: File, content: String) {
        if (file.exists()) return
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private const val README = """# Sample Project

A tiny project to try the agent on. Nothing here is precious — ask the
agent to explain it, change it, or break it.

Try asking:
- "Explain what main.py does"
- "Add a function that greets me by name"
- "Write a test for the greeting"
"""

    /** Three double-quotes, for the docstring in [MAIN_PY]: a raw string
     * cannot contain `"""` itself, so it is interpolated in. */
    private const val DQ3 = "\"\"\""

    private const val MAIN_PY = """${DQ3}Sample project: a greeting you can ask the agent to change.${DQ3}


def greet(name: str) -> str:
    return f"Hello, {name}!"


def main() -> None:
    print(greet("world"))


if __name__ == "__main__":
    main()
"""

    private const val TODO = """# TODO

- [ ] Ask the agent to explain main.py
- [ ] Ask it to add a `--name` argument
- [ ] Ask it to write a test for `greet`
"""
}
