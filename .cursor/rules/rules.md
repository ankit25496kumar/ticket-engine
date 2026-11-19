# Cursor AI Rules – Prompt History Management

## Objective
Maintain a complete and chronological history of every prompt provided by the user during interactions with Cursor AI.

## Mandatory Rules

1. **Record Every User Prompt**
   - Every prompt provided by the user must be recorded in `prompt-history.md`.
   - Do not skip any prompt, regardless of its length, complexity, or purpose.
   - Include prompts related to coding, debugging, refactoring, code reviews, architecture, documentation, database queries, testing, and general development tasks.

2. **Preserve Original Prompts**
   - Store the exact original prompt provided by the user.
   - Do not rewrite, summarize, correct, or modify the original prompt.
   - Preserve code snippets, SQL queries, error messages, formatting, and technical details.

3. **Maintain Chronological Order**
   - Append every new prompt to the end of `prompt-history.md`.
   - Never overwrite or delete previously recorded prompts.
   - Maintain the chronological order of prompts.

4. **Prompt History Format**

   Use the following Markdown structure:

   ## Prompt #001
   - **Date:** YYYY-MM-DD HH:mm
   - **Category:** Coding / Debugging / Refactoring / Architecture / Database / Documentation / Other
   - **Context:** Brief description of the task.

   **Original Prompt:**
   ```text
   Exact user prompt goes here.
   ```

5. **Update History Before Execution**
   - Record the user's prompt in `prompt-history.md` before starting the requested implementation or modification.
   - Ensure the history file is saved successfully before proceeding.
   - Do not consider a prompt recorded unless it has actually been written to the file.

6. **Multiple Prompts**
   - Treat every separate user message as a new prompt.
   - If the user provides multiple instructions in one message, preserve the complete message as a single prompt entry.
   - Follow-up instructions, corrections, and additional requirements must also be recorded separately.

7. **File Management**
   - Create `prompt-history.md` in the project root if it does not exist.
   - If the file already exists, append new entries without modifying existing history.
   - Do not create duplicate entries for the same prompt.
   - Do not clear the history when the project is updated or restarted.

8. **Execution Transparency**
   - After recording a prompt, proceed with the requested task.
   - If the history file cannot be updated, inform the user instead of claiming that the prompt was recorded.

## Important
These rules are mandatory for every interaction with Cursor AI in this project.

The `prompt-history.md` file is intended to serve as a permanent, chronological record of user instructions and project evolution.