package org.telegram.ui.Components;

/** Damped spring solution using Swiftgram UIKitRuntimeUtils' original parameters. */
public final class DevGramSwiftgramSpring {
    public static float context(float fraction) { return solve(fraction, 5, 900, 104, .78); }
    public static float screen(float fraction) {
        double omega = 2 * Math.PI / .3;
        return solve(fraction, 1, omega * omega, 2 * omega, .4);
    }
    public static float releasedScreen(float fraction) {
        double omega = 2 * Math.PI / .3;
        return solve(fraction, 1, omega * omega, 2 * .85 * omega, .4);
    }
    public static float navigation(float fraction) { return solve(fraction, 1, 555.027, 47.118, .4); }

    public static float solve(float fraction, double mass, double stiffness, double damping, double seconds) {
        if (fraction <= 0) return 0;
        if (fraction >= 1) return 1;
        double t = fraction * seconds;
        double alpha = damping / (2 * mass), omega = Math.sqrt(stiffness / mass);
        double delta = omega * omega - alpha * alpha;
        if (Math.abs(delta) < .001) return (float) (1 - Math.exp(-alpha * t) * (1 + alpha * t));
        if (delta > 0) {
            double frequency = Math.sqrt(delta);
            return (float) (1 - Math.exp(-alpha * t) * (Math.cos(frequency * t) + alpha / frequency * Math.sin(frequency * t)));
        }
        double root = Math.sqrt(-delta), r1 = -alpha + root, r2 = -alpha - root;
        return (float) (1 - (r2 * Math.exp(r1 * t) - r1 * Math.exp(r2 * t)) / (r2 - r1));
    }

    private DevGramSwiftgramSpring() {}
}
