package io.github.peshke.launchio;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class DockSlotsTest {
    @Test public void threeDockAppsInFourColumnsLeaveOneSlotEmpty() {
        assertEquals(1, DockSlots.empty(3, 4, 20, false));
    }

    @Test public void fillDockKeepsTheOldBehaviour() {
        assertEquals(0, DockSlots.empty(3, 4, 20, true));
    }

    @Test public void aFullDockNeedsNoEmptySlot() {
        assertEquals(0, DockSlots.empty(4, 4, 20, false));
        assertEquals(0, DockSlots.empty(6, 6, 20, false));
    }

    @Test public void noInstalledDockAppFillsTheFirstRow() {
        assertEquals(0, DockSlots.empty(0, 4, 20, false));
    }

    @Test public void nothingAfterTheDockNeedsNoEmptySlot() {
        assertEquals(0, DockSlots.empty(2, 4, 2, false));
    }

    @Test public void inputsTakeTheDocksLastSlotsAndNeverTheGrid() {
        assertEquals(2, DockSlots.inputsAt(2, 4, 20, 2, true));    // after two apps, in slots 3 and 4
        assertEquals(1, DockSlots.inputsAt(2, 4, 1, 1, true));     // one app installed: right after it
        assertEquals(0, DockSlots.inputsAt(4, 4, 20, 4, true));    // all four slots
        assertEquals(0, DockSlots.inputsAt(6, 4, 20, 4, true));    // more inputs than slots: still inside the dock
        assertEquals(1, DockSlots.inputsAt(2, 4, 20, 1, false));   // no fill: after the docked apps
    }

    @Test public void rowsSkipHiddenOnesBothWays() {
        boolean[] shown = {true, false, true, false};
        assertEquals(2, DockSlots.shownRow(shown, 1, 1));
        assertEquals(0, DockSlots.shownRow(shown, 1, -1));
        assertEquals(-1, DockSlots.shownRow(shown, 3, 1));
        assertEquals(2, DockSlots.shownRow(shown, 3, -1));
    }

    @Test public void focusOnAnEmptySlotGoesToTheLastDockApp() {
        assertEquals(2, DockSlots.focusable(3, 3, 1));
        assertEquals(1, DockSlots.focusable(2, 2, 2));
        assertEquals(1, DockSlots.focusable(3, 2, 2));
    }

    @Test public void focusOnATileStaysPut() {
        assertEquals(0, DockSlots.focusable(0, 3, 1));
        assertEquals(2, DockSlots.focusable(2, 3, 1));
        assertEquals(4, DockSlots.focusable(4, 3, 1));
        assertEquals(3, DockSlots.focusable(3, 4, 0));
    }
}
