package com.mrksvt.waen.activities;

import java.util.HashMap;
import java.util.Map;

/** Stable pager positions shared with search; menu IDs are supplied by the UI. */
public final class MainNavigationRoutes {

    public static final int POSITION_GENERAL = 0;
    public static final int POSITION_PRIVACY = 1;
    public static final int POSITION_HOME = 2;
    public static final int POSITION_MEDIA = 3;
    public static final int POSITION_CUSTOMIZATION = 4;
    public static final int POSITION_RECORDINGS = 5;
    public static final int POSITION_TTS = 6;
    public static final int PAGE_COUNT = 7;
    public static final int NO_PAGE = -1;
    public static final int NO_MENU_ID = 0;

    private final Map<Integer, Integer> pagesByMenuId;
    private final int generalId;
    private final int privacyId;
    private final int homeId;
    private final int mediaId;
    private final int moreId;
    private final int customizationId;
    private final int recordingsId;

    public MainNavigationRoutes(int generalId, int privacyId, int homeId, int mediaId,
                                int moreId, int customizationId, int recordingsId, int ttsId) {
        this.generalId = generalId;
        this.privacyId = privacyId;
        this.homeId = homeId;
        this.mediaId = mediaId;
        this.moreId = moreId;
        this.customizationId = customizationId;
        this.recordingsId = recordingsId;
        pagesByMenuId = new HashMap<>();
        pagesByMenuId.put(generalId, POSITION_GENERAL);
        pagesByMenuId.put(privacyId, POSITION_PRIVACY);
        pagesByMenuId.put(homeId, POSITION_HOME);
        pagesByMenuId.put(mediaId, POSITION_MEDIA);
        pagesByMenuId.put(customizationId, POSITION_CUSTOMIZATION);
        pagesByMenuId.put(recordingsId, POSITION_RECORDINGS);
        pagesByMenuId.put(ttsId, POSITION_TTS);
    }

    /** Lainnya toggles the sidebar, so it has no pager destination. */
    public int pageForMenuId(int menuId) {
        return pagesByMenuId.getOrDefault(menuId, NO_PAGE);
    }

    /** TTS is opened from the header and has no selected bottom menu item. */
    public int bottomMenuIdForPage(int position) {
        return switch (position) {
            case POSITION_GENERAL -> generalId;
            case POSITION_PRIVACY -> privacyId;
            case POSITION_HOME -> homeId;
            case POSITION_MEDIA -> mediaId;
            case POSITION_CUSTOMIZATION, POSITION_RECORDINGS -> moreId;
            default -> NO_MENU_ID;
        };
    }

    /** Sidebar selection for a page, or NO_MENU_ID when the page is not a sidebar entry. */
    public int sidebarMenuIdForPage(int position) {
        return switch (position) {
            case POSITION_CUSTOMIZATION -> customizationId;
            case POSITION_RECORDINGS -> recordingsId;
            default -> NO_MENU_ID;
        };
    }
}
