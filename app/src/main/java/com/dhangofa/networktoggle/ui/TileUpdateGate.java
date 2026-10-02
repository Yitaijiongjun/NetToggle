package com.dhangofa.networktoggle.ui;

/** Main-thread gate for visible, changed tile presentations. */
public final class TileUpdateGate {
    private boolean listening;
    private boolean collapsing;
    private String lastPresentation;

    public void startListening() {
        listening = true;
        collapsing = false;
        lastPresentation = null;
    }

    public void stopListening() {
        listening = false;
        collapsing = false;
    }

    public void beginCollapse() { collapsing = true; }
    public void cancelCollapse() { collapsing = false; }

    public boolean accept(String presentation) {
        if (!listening || collapsing || presentation.equals(lastPresentation)) return false;
        lastPresentation = presentation;
        return true;
    }
}
