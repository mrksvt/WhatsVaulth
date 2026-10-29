package com.mrksvt.waen.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.waseemsabir.betterypermissionhelper.BatteryPermissionHelper;
import com.mrksvt.waen.App;
import com.mrksvt.waen.R;
import com.mrksvt.waen.ReleaseInfo;
import com.mrksvt.waen.UpdateChecker;
import com.mrksvt.waen.UpdateInfoStore;
import com.mrksvt.waen.activities.base.BaseActivity;
import com.mrksvt.waen.adapter.MainPagerAdapter;
import com.mrksvt.waen.adapter.ReleasePopupAdapter;
import com.mrksvt.waen.databinding.ActivityMainBinding;
import com.mrksvt.waen.ui.fragments.GeneralFragment;
import com.mrksvt.waen.ui.fragments.HomeFragment;
import com.mrksvt.waen.ui.fragments.base.BasePreferenceFragment;
import com.mrksvt.waen.utils.FilePicker;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MainActivity extends BaseActivity {

    private ActivityMainBinding binding;
    private static final String STATE_PAGE = "main_page";
    private static final String STATE_SIDEBAR = "main_sidebar_open";
    private static final int VISIBLE_RELEASE_ROWS = 4;
    private static final int RELEASE_ROW_HEIGHT_DP = 60;
    private static final int POPUP_WIDTH_DP = 320;
    private static final int POPUP_SCREEN_MARGIN_DP = 16;
    private static final int POPUP_ELEVATION_DP = 8;
    private MainNavigationRoutes navigationRoutes;
    private MainSidebarController sidebar;
    private OnBackPressedCallback sidebarBackCallback;
    private ViewPager2.OnPageChangeCallback pageChangeCallback;
    private BatteryPermissionHelper batteryPermissionHelper = BatteryPermissionHelper.Companion.getInstance();
    private String pendingScrollToPreference = null;
    private int pendingScrollToFragment = -1;
    private String pendingParentKey = null;
    private View notificationActionView;
    private boolean updateCheckStarted;
    private PopupWindow releasePopup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        App.changeLanguage(this);
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        MainPagerAdapter pagerAdapter = new MainPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);

        binding.viewPager.setPageTransformer(new DepthPageTransformer());

        navigationRoutes = new MainNavigationRoutes(
                R.id.navigation_chat, R.id.navigation_privacy, R.id.navigation_home,
                R.id.navigation_media, R.id.navigation_more, R.id.navigation_colors,
                R.id.navigation_recordings, R.id.menu_tts);
        // Extra pages are opened explicitly; swiping must not cross sidebar/TTS routes.
        binding.viewPager.setUserInputEnabled(false);
        sidebarBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                sidebar.close();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, sidebarBackCallback);
        sidebar = new MainSidebarController(binding.mainSidebarOverlay, binding.mainSidebar,
                binding.mainSidebarScrim, binding.viewPager, () -> {
                    sidebarBackCallback.setEnabled(sidebar.isShowing());
                    syncNavigationSelection(binding.viewPager.getCurrentItem());
                });
        binding.navView.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.navigation_more) {
                sidebar.toggle();
                return false;
            }
            int position = navigationRoutes.pageForMenuId(item.getItemId());
            if (position == MainNavigationRoutes.NO_PAGE) return false;
            navigateToPage(position);
            return true;
        });
        binding.navView.setOnItemReselectedListener(item -> {
            if (item.getItemId() == R.id.navigation_more) sidebar.toggle();
        });
        binding.mainSidebar.setNavigationItemSelectedListener(item -> {
            if (item.getItemId() == R.id.navigation_screen_recorder) {
                sidebar.closeImmediately();
                navigateToScreenRecorder();
                return true;
            }
            int position = navigationRoutes.pageForMenuId(item.getItemId());
            if (position == MainNavigationRoutes.NO_PAGE) return false;
            navigateToPage(position);
            return true;
        });

        pageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                syncNavigationSelection(position);
                consumePendingScroll(position);
            }
        };
        binding.viewPager.registerOnPageChangeCallback(pageChangeCallback);
        int initialPage = savedInstanceState == null ? MainPagerAdapter.POSITION_HOME
                : savedInstanceState.getInt(STATE_PAGE, MainPagerAdapter.POSITION_HOME);
        if (initialPage < 0 || initialPage >= MainNavigationRoutes.PAGE_COUNT) {
            initialPage = MainPagerAdapter.POSITION_HOME;
        }
        binding.viewPager.setCurrentItem(initialPage, false);
        boolean restoreSidebar = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_SIDEBAR, false);
        if (restoreSidebar) sidebar.restoreOpen();
        syncNavigationSelection(binding.viewPager.getCurrentItem());
        createMainDir();
        FilePicker.registerFilePicker(this);
        
        // Handle incoming navigation from search
        handleIncomingIntent(getIntent());
    }

    private void createMainDir() {
        var nomedia = new File(App.getWaEnhancerFolder(), ".nomedia");
        if (nomedia.exists()) {
            nomedia.delete();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncomingIntent(intent);
    }
    
    private void navigateToPage(int position) {
        if (position < 0 || position >= MainNavigationRoutes.PAGE_COUNT) return;
        sidebar.closeImmediately();
        boolean samePage = binding.viewPager.getCurrentItem() == position;
        binding.viewPager.setCurrentItem(position, false);
        syncNavigationSelection(position);
        if (samePage) consumePendingScroll(position);
    }

    /**
     * Screen recorder is an Activity, not a pager destination, so it bypasses
     * MainNavigationRoutes entirely (adding a position would shift search's
     * stored indices).
     */
    private void navigateToScreenRecorder() {
        try {
            startActivity(new Intent(this, ScreenRecorderActivity.class));
        } catch (Exception e) {
            Toast.makeText(this, R.string.main_sidebar_screen_recorder, Toast.LENGTH_SHORT).show();
        }
    }

    private void syncNavigationSelection(int position) {
        int menuId = sidebar.isOpenRequested() ? R.id.navigation_more
                : navigationRoutes.bottomMenuIdForPage(position);
        if (menuId != MainNavigationRoutes.NO_MENU_ID) {
            MenuItem item = binding.navView.getMenu().findItem(menuId);
            if (item != null) item.setChecked(true);
        }
        syncSidebarSelection(position);
    }

    // NavigationView.setCheckedItem(id) is a no-op when the id is absent, and the sidebar menu is
    // single-checkable, so both cases must be driven through the menu items themselves.
    private void syncSidebarSelection(int position) {
        int sidebarMenuId = navigationRoutes.sidebarMenuIdForPage(position);
        Menu menu = binding.mainSidebar.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            item.setChecked(item.getItemId() == sidebarMenuId);
        }
    }

    private void consumePendingScroll(int position) {
        if (pendingScrollToFragment != position || pendingScrollToPreference == null) return;
        String scrollKey = pendingScrollToPreference;
        String parentKey = pendingParentKey;
        pendingScrollToPreference = null;
        pendingScrollToFragment = -1;
        pendingParentKey = null;
        binding.viewPager.postDelayed(() -> {
            if (!isDestroyed() && binding.viewPager.getCurrentItem() == position) {
                scrollToPreferenceInCurrentFragment(scrollKey, parentKey);
            }
        }, 300);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        int position = intent.getIntExtra("navigate_to_fragment", -1);
        if (position < 0 || position >= MainNavigationRoutes.PAGE_COUNT) return;
        pendingScrollToPreference = intent.getStringExtra("scroll_to_preference");
        pendingScrollToFragment = position;
        pendingParentKey = intent.getStringExtra("parent_preference");
        navigateToPage(position);
        intent.removeExtra("navigate_to_fragment");
        intent.removeExtra("scroll_to_preference");
        intent.removeExtra("parent_preference");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_PAGE, binding.viewPager.getCurrentItem());
        outState.putBoolean(STATE_SIDEBAR, sidebar.isOpenRequested());
    }

    @Override
    protected void onStop() {
        dismissReleasePopup();
        sidebar.reset();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        dismissReleasePopup();
        binding.viewPager.unregisterOnPageChangeCallback(pageChangeCallback);
        sidebar.dispose();
        super.onDestroy();
    }

    private void scrollToPreferenceInCurrentFragment(String preferenceKey, String parentKey) {
        // Get the current fragment from the ViewPager
        int currentItem = binding.viewPager.getCurrentItem();
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("f" + currentItem);
        
        if (fragment == null) return;
        
        // Handle different fragment types
        if (fragment instanceof GeneralFragment || fragment instanceof HomeFragment) {
            // These fragments have child fragments
            if (parentKey != null && !parentKey.isEmpty()) {
                // Navigate to sub-fragment first, then scroll
                navigateToSubFragmentAndScroll(fragment, parentKey, preferenceKey);
            } else {
                // Direct scroll in current child fragment
                scrollInChildFragment(fragment, preferenceKey);
            }
        } else if (fragment instanceof BasePreferenceFragment) {
            // Direct preference fragments (no nesting)
            ((BasePreferenceFragment) fragment).scrollToPreference(preferenceKey);
        }
    }
    
    private void navigateToSubFragmentAndScroll(Fragment parentFragment, String parentKey, String childPreferenceKey) {
        // Directly instantiate the sub-fragment
        Fragment subFragment = null;
        
        switch (parentKey) {
            case "general_home":
                subFragment = new GeneralFragment.HomeGeneralPreference();
                break;
            case "homescreen":
                subFragment = new GeneralFragment.HomeScreenGeneralPreference();
                break;
            case "conversation":
                subFragment = new GeneralFragment.ConversationGeneralPreference();
                break;
        }
        
        if (subFragment != null && parentFragment.getView() != null) {
            final Fragment finalSubFragment = subFragment;
            // Replace the current child fragment
            parentFragment.getChildFragmentManager().beginTransaction()
                .replace(R.id.frag_container, subFragment)
                .commitNow();
            
            // Wait for fragment to be ready, then scroll
            parentFragment.getView().postDelayed(() -> {
                if (finalSubFragment instanceof BasePreferenceFragment) {
                    ((BasePreferenceFragment) finalSubFragment).scrollToPreference(childPreferenceKey);
                }
            }, 400);
        }
    }
    
    private void scrollInChildFragment(Fragment parentFragment, String preferenceKey) {
        Fragment childFragment = parentFragment.getChildFragmentManager().findFragmentById(R.id.frag_container);
        if (childFragment instanceof BasePreferenceFragment) {
            ((BasePreferenceFragment) childFragment).scrollToPreference(preferenceKey);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.header_menu, menu);
        var powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            menu.findItem(R.id.batteryoptimization).setVisible(false);
        }
        MenuItem notificationItem = menu.findItem(R.id.menu_notification);
        if (notificationItem != null) {
            notificationActionView = notificationItem.getActionView();
            if (notificationActionView != null) {
                notificationActionView.setOnClickListener(v -> showReleaseMenu(v));
                refreshNotificationBadge();
            }
        }
        if (!updateCheckStarted) {
            updateCheckStarted = true;
            startUpdateCheck();
        }
        return true;
    }

    @SuppressLint("BatteryLife")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_search) {
            var options = ActivityOptionsCompat.makeCustomAnimation(
                    this, R.anim.slide_in_right, R.anim.slide_out_left);
            startActivity(new Intent(this, SearchActivity.class), options.toBundle());
            return true;
        } else if (item.getItemId() == R.id.menu_about) {
            var options = ActivityOptionsCompat.makeCustomAnimation(
                    this, R.anim.slide_in_right, R.anim.slide_out_left);
            startActivity(new Intent(this, AboutActivity.class), options.toBundle());
            return true;
        } else if (item.getItemId() == R.id.menu_notification) {
            if (notificationActionView != null) {
                showReleaseMenu(notificationActionView);
            } else {
                openChangelog();
            }
            return true;
        } else if (item.getItemId() == R.id.batteryoptimization) {
            if (batteryPermissionHelper.isBatterySaverPermissionAvailable(this, true)) {
                batteryPermissionHelper.getPermission(this, true, true);
            } else {
                var intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 0);
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void openChangelog() {
        var options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left);
        startActivity(new Intent(this, ChangelogActivity.class), options.toBundle());
    }

    /**
     * Release list anchored to the header bell. Height fits four rows; the
     * rest is reached by scrolling inside the popup.
     * ponytail: no pagination - the list is capped by the API page (20 releases).
     */
    private void showReleaseMenu(View anchor) {
        List<ReleaseInfo> releases = UpdateInfoStore.getReleases(this);
        if (releases.isEmpty()) {
            openChangelog();
            return;
        }
        dismissReleasePopup();

        View content = LayoutInflater.from(this).inflate(R.layout.popup_release_menu, null);
        RecyclerView list = content.findViewById(R.id.release_list);
        TextView markAllRead = content.findViewById(R.id.btn_mark_all_read);

        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new ReleasePopupAdapter(releases, UpdateInfoStore.getReadTags(this),
                release -> {
                    UpdateInfoStore.markRead(this, release.tagName);
                    dismissReleasePopup();
                    refreshNotificationBadge();
                    openChangelogFor(release);
                }));
        markAllRead.setOnClickListener(v -> {
            UpdateInfoStore.markAllRead(this);
            dismissReleasePopup();
            refreshNotificationBadge();
        });

        int rows = Math.min(releases.size(), VISIBLE_RELEASE_ROWS);
        ViewGroup.LayoutParams params = list.getLayoutParams();
        params.height = Math.round(getResources().getDisplayMetrics().density * RELEASE_ROW_HEIGHT_DP) * rows;
        list.setLayoutParams(params);

        int width = Math.min(Math.round(getResources().getDisplayMetrics().density * POPUP_WIDTH_DP),
                Math.round(getResources().getDisplayMetrics().widthPixels
                        - getResources().getDisplayMetrics().density * POPUP_SCREEN_MARGIN_DP));
        releasePopup = new PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        releasePopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        releasePopup.setOutsideTouchable(true);
        releasePopup.setElevation(Math.round(
                getResources().getDisplayMetrics().density * POPUP_ELEVATION_DP));
        releasePopup.setFocusable(true);
        releasePopup.showAsDropDown(anchor, anchor.getWidth() - width,
                Math.round(getResources().getDisplayMetrics().density * 4));
    }

    private void dismissReleasePopup() {
        if (releasePopup != null && releasePopup.isShowing()) {
            releasePopup.dismiss();
        }
        releasePopup = null;
    }

    private void openChangelogFor(ReleaseInfo release) {
        var options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left);
        Intent intent = new Intent(this, ChangelogActivity.class)
                .putExtra(ChangelogActivity.EXTRA_TITLE, release.displayName())
                .putExtra(ChangelogActivity.EXTRA_HASH, release.hash)
                .putExtra(ChangelogActivity.EXTRA_CHANGELOG, release.changelog)
                .putExtra(ChangelogActivity.EXTRA_RELEASE_URL, release.releaseUrl)
                .putExtra(ChangelogActivity.EXTRA_PUBLISHED_AT, release.publishedAt)
                .putExtra(ChangelogActivity.EXTRA_APK_URL, release.apkUrl)
                .putExtra(ChangelogActivity.EXTRA_APK_SIZE, release.apkSize);
        startActivity(intent, options.toBundle());
    }

    private void startUpdateCheck() {
        CompletableFuture.runAsync(new UpdateChecker(
                this, hasUpdate -> {
                    runOnUiThread(this::refreshNotificationBadge);
                }));
    }

    private void refreshNotificationBadge() {
        if (notificationActionView == null) return;
        TextView badge = notificationActionView.findViewById(R.id.notification_badge);
        if (badge == null) return;
        int count = UpdateInfoStore.unreadCount(this);
        if (count > 0) {
            badge.setText(UpdateInfoStore.unreadLabel(this));
            badge.setContentDescription(getString(
                    R.string.notification_unread_content_description, count));
            badge.setVisibility(View.VISIBLE);
        } else {
            badge.setVisibility(View.GONE);
        }
    }

    public static boolean isXposedEnabled() {
        return false;
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return super.onSupportNavigateUp();
    }

    private static class DepthPageTransformer implements ViewPager2.PageTransformer {
        private static final float MIN_SCALE = 0.85f;

        @Override
        public void transformPage(@NonNull android.view.View page, float position) {
            int pageWidth = page.getWidth();

            if (position < -1) {
                page.setAlpha(0f);
            } else if (position <= 0) {
                page.setAlpha(1f);
                page.setTranslationX(0f);
                page.setTranslationZ(0f);
                page.setScaleX(1f);
                page.setScaleY(1f);
            } else if (position <= 1) {
                page.setAlpha(1 - position);
                page.setTranslationX(pageWidth * -position);
                page.setTranslationZ(-1f);
                float scaleFactor = MIN_SCALE + (1 - MIN_SCALE) * (1 - Math.abs(position));
                page.setScaleX(scaleFactor);
                page.setScaleY(scaleFactor);
            } else {
                page.setAlpha(0f);
            }
        }
    }
}
