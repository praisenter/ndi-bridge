package org.praisenter.ndibridge;

/** Program and preview tally state. */
public final class NdiTally {
    private final boolean onProgram;
    private final boolean onPreview;

    public NdiTally(boolean onProgram, boolean onPreview) {
        this.onProgram = onProgram;
        this.onPreview = onPreview;
    }

    public boolean isOnProgram() { return onProgram; }
    public boolean isOnPreview() { return onPreview; }
}