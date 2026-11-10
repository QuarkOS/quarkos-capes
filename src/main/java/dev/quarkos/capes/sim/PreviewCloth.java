package dev.quarkos.capes.sim;

/** A settled GUI pose, independent of the world player's motion and saved simulation. */
public final class PreviewCloth {
    private PreviewCloth() { }

    public static WaveCloth.State state(WaveCloth.Settings settings) {
        WaveCloth.State state = new WaveCloth.State();
        WaveCloth.run(state, settings, WaveCloth.Motion.still(), 20);
        return state;
    }
}
