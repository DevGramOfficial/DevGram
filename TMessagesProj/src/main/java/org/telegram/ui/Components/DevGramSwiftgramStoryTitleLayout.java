package org.telegram.ui.Components;

/** Exact StoryPeerListTitleLayout geometry from Swiftgram; arguments are logical points. */
public final class DevGramSwiftgramStoryTitleLayout {
    public static float origin(float containerWidth, float contentWidth, float minX, float maxX) {
        float lower = minX + 4, upper = maxX - 4;
        float centered = (containerWidth - contentWidth) * .5f;
        float balanced = (lower + upper - contentWidth) * .5f;
        float closest = Math.min(centered - lower, upper - centered - contentWidth);
        float x = Math.max(0, Math.min(1, 1 - closest / 20));
        float fraction = x * x * (3 - 2 * x);
        return centered + (balanced - centered) * fraction;
    }
    public static boolean showsText(float width, float minX, float maxX) {
        return width <= maxX - minX - 8;
    }
    private DevGramSwiftgramStoryTitleLayout() {}
}
