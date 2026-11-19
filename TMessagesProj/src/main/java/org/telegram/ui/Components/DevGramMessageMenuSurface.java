package org.telegram.ui.Components;

/** Common lifecycle only; each appearance owns its own presentation. */
public interface DevGramMessageMenuSurface {
    void closeAnimated();
    boolean isClosing();
    void release();
}
