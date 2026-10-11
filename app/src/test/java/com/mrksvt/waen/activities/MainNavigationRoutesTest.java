package com.mrksvt.waen.activities;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class MainNavigationRoutesTest {

    private final MainNavigationRoutes routes = new MainNavigationRoutes(
            901, 104, 707, 202, 503, 306, 805, 408);

    @Test
    public void menuIdsSelectStableSearchPositions() {
        assertEquals(0, routes.pageForMenuId(901));
        assertEquals(1, routes.pageForMenuId(104));
        assertEquals(2, routes.pageForMenuId(707));
        assertEquals(3, routes.pageForMenuId(202));
        assertEquals(4, routes.pageForMenuId(306));
        assertEquals(5, routes.pageForMenuId(805));
        assertEquals(6, routes.pageForMenuId(408));
        assertEquals(7, MainNavigationRoutes.PAGE_COUNT);
    }

    @Test
    public void recordingSelectsItsOwnPageOnEveryRequest() {
        for (int i = 0; i < 10; i++) {
            assertEquals(6, routes.pageForMenuId(408));
            assertEquals(5, routes.pageForMenuId(805));
            assertNotEquals(routes.pageForMenuId(408), routes.pageForMenuId(805));
        }
    }

    @Test
    public void sidebarPagesSelectMoreAsBottomParent() {
        assertEquals(503, routes.bottomMenuIdForPage(4));
        assertEquals(503, routes.bottomMenuIdForPage(5));
    }

    @Test
    public void bottomPagesSelectTheirOwnMenuIds() {
        assertEquals(901, routes.bottomMenuIdForPage(0));
        assertEquals(104, routes.bottomMenuIdForPage(1));
        assertEquals(707, routes.bottomMenuIdForPage(2));
        assertEquals(202, routes.bottomMenuIdForPage(3));
    }

    @Test
    public void moreOnlyTogglesSidebarAndTtsHasNoBottomParent() {
        assertEquals(MainNavigationRoutes.NO_PAGE, routes.pageForMenuId(503));
        assertEquals(MainNavigationRoutes.NO_MENU_ID, routes.bottomMenuIdForPage(6));
    }

    @Test
    public void unknownIdsAndPagesHaveNoDestination() {
        for (int id : new int[]{0, -1, 5, Integer.MAX_VALUE}) {
            assertEquals(MainNavigationRoutes.NO_PAGE, routes.pageForMenuId(id));
        }
        for (int page : new int[]{-1, 7, Integer.MAX_VALUE}) {
            assertEquals(MainNavigationRoutes.NO_MENU_ID, routes.bottomMenuIdForPage(page));
        }
    }

    @Test
    public void routesDoNotDependOnIdOrderingOrOtherMenuSelections() {
        MainNavigationRoutes reordered = new MainNavigationRoutes(
                408, 805, 306, 503, 202, 707, 104, 901);
        assertEquals(5, reordered.pageForMenuId(104));
        assertEquals(4, reordered.pageForMenuId(707));
        assertEquals(6, reordered.pageForMenuId(901));
        assertEquals(202, reordered.bottomMenuIdForPage(5));
        assertEquals(5, routes.pageForMenuId(805));
        assertEquals(503, routes.bottomMenuIdForPage(5));
    }
}
