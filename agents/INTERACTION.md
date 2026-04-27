# Interaction Preferences

This document defines how to distinguish between user **Inquiries** and **Directives**.

## 1. Inquiry (Research & Analysis Only)

An **Inquiry** is a request for information, analysis, or advice. When the user makes an Inquiry, the agent must **only** perform research and provide a report or proposal. **No files should be modified.**

**Markers for Inquiries:**
- Starts with "How do I...", "What is...", "Can you explain...", "Is it possible to..."
- Asks for a code review or architectural advice.
- Includes the explicit prefix `[ASK]`.

**Agent Response:**
- Perform research using read-only tools (search, file reads).
- Propose a strategy or provide the requested information.
- Stop and wait for a Directive before implementing any changes.

## 2. Directive (Action & Implementation)

A **Directive** is an explicit instruction to perform a task, fix a bug, or implement a feature. When the user issues a Directive, the agent must proceed through the **Research → Strategy → Execution** lifecycle.

**Markers for Directives:**
- Starts with imperative verbs: "Fix...", "Implement...", "Refactor...", "Add...", "Create..."
- Explicitly states "Do this," "Go ahead," or "Apply the changes."
- Includes the explicit prefix `[DO]`.

**Agent Response:**
- Follow the standard Research → Strategy → Execution workflow.
- Implement the changes and verify them with tests.
- Only clarify if the request is critically underspecified.

## 3. Ambiguity Resolution

If the intent is unclear, the agent must use `AskUserQuestion` to clarify whether the request is an Inquiry or a Directive before taking any action that modifies the workspace.
