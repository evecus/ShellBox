package com.termux.terminal;

/**
 * Minimal client interface for TerminalEmulator (SSH bridge version).
 * Removed TerminalSession parameter references - callbacks are sessionless.
 */
public interface TerminalSessionClient {

    void onTextChanged();

    void onTitleChanged(String newTitle);

    void onCopyTextToClipboard(String text);

    void onPasteTextFromClipboard();

    void onBell();

    void onColorsChanged();

    void onTerminalCursorStateChange(boolean state);

    /** Return cursor style (TERMINAL_CURSOR_STYLE_BLOCK etc.) or null to use default block. */
    Integer getTerminalCursorStyle();

    void logError(String tag, String message);
    void logWarn(String tag, String message);
    void logInfo(String tag, String message);
    void logDebug(String tag, String message);
    void logVerbose(String tag, String message);
}
