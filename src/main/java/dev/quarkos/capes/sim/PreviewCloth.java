package dev.quarkos.capes.sim;

/** A GUI pose independent of the world player's motion and simulation. */
public final class PreviewCloth {
    private PreviewCloth() { }

    public static WaveCloth.State state(WaveCloth.Settings settings) {
        return state(settings, WaveCloth.Motion.still());
    }

    public static WaveCloth.State state(WaveCloth.Settings settings, WaveCloth.Motion motion) {
        WaveCloth.State state = new WaveCloth.State();
        WaveCloth.run(state, settings, motion, 20);
        return state;
    }
}
