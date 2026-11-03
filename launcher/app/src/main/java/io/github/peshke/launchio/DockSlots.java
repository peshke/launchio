package io.github.peshke.launchio;

/**
 * The dock is the first row of the grid. dock_apps decides what is in it: a slot it leaves free stays
 * empty instead of taking the first grid app (#162) when fill_dock is false; by default they fill.
 */
final class DockSlots {
    private DockSlots() { }

    /**
     * Empty slots to put after the {@code docked} dock apps found. None when the dock is full, when no
     * dock app is installed (the first row fills so Home still has a dock) or when nothing follows.
     */
    static int empty(int docked, int dockSize, int tiles, boolean fill) {
        if (fill || docked <= 0 || docked >= dockSize || tiles <= docked) return 0;
        return dockSize - docked;
    }

    /**
     * Where n input tiles go: the dock's last slots, never past it (an input never lands in the grid). With fill,
     * after however many apps there are, else after the docked apps; apps from that point on move down a slot.
     */
    static int inputsAt(int n, int dockSize, int tiles, int docked, boolean fill) {
        return Math.max(0, Math.min(fill ? tiles : docked, dockSize - Math.min(n, dockSize)));
    }

    /** The next shown row from index in this direction (step 1 or -1), or -1 when there is none. */
    static int shownRow(boolean[] shown, int from, int step) {
        for (int i = from; i >= 0 && i < shown.length; i += step) if (shown[i]) return i;
        return -1;
    }

    /** A position focus can land on: an empty slot sends it to the last dock app. */
    static int focusable(int position, int docked, int empty) {
        return empty > 0 && position >= docked && position < docked + empty ? docked - 1 : position;
    }
}
