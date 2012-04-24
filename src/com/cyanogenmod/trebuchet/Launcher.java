/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cyanogenmod.trebuchet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SearchManager;
import android.app.StatusBarManager;
import android.app.UiModeManager;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Advanceable;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.common.Search;
import com.cyanogenmod.trebuchet.R;
import com.cyanogenmod.trebuchet.DropTarget.DragObject;
import com.cyanogenmod.trebuchet.preference.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Default launcher application.
 */
public final class Launcher extends Activity
        implements View.OnClickListener, OnLongClickListener, LauncherModel.Callbacks,
                   View.OnTouchListener {
    static final String TAG = "Launcher";
    static final boolean LOGD = false;

    static final boolean PROFILE_STARTUP = false;
    static final boolean DEBUG_WIDGETS = false;

    private static final int MENU_GROUP_WALLPAPER = 1;
    private static final int MENU_GROUP_MARKET = MENU_GROUP_WALLPAPER + 1;
    private static final int MENU_GROUP_SETTINGS = MENU_GROUP_MARKET + 1;
    private static final int MENU_WALLPAPER_SETTINGS = Menu.FIRST + 1;
    private static final int MENU_MANAGE_APPS = MENU_WALLPAPER_SETTINGS + 1;
    private static final int MENU_MARKET = MENU_MANAGE_APPS + 1;
    private static final int MENU_PREFERENCES = MENU_MARKET + 1;
    private static final int MENU_SYSTEM_SETTINGS = MENU_PREFERENCES + 1;
    private static final int MENU_HELP = MENU_SYSTEM_SETTINGS + 1;

    private static final int REQUEST_CREATE_SHORTCUT = 1;
    private static final int REQUEST_CREATE_APPWIDGET = 5;
    private static final int REQUEST_PICK_APPLICATION = 6;
    private static final int REQUEST_PICK_SHORTCUT = 7;
    private static final int REQUEST_PICK_APPWIDGET = 9;
    private static final int REQUEST_PICK_WALLPAPER = 10;

    static final String EXTRA_SHORTCUT_DUPLICATE = "duplicate";

    static final int MAX_SCREEN_COUNT = 7;
    static final int DEFAULT_SCREEN = 2;

    static final int DIALOG_CREATE_SHORTCUT = 1;
    static final int DIALOG_RENAME_FOLDER = 2;

    private static final String PREFERENCES = "launcher.preferences";

    // Type: int
    private static final String RUNTIME_STATE_CURRENT_SCREEN = "launcher.current_screen";
    // Type: int
    private static final String RUNTIME_STATE = "launcher.state";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CONTAINER = "launcher.add_container";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_SCREEN = "launcher.add_screen";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_X = "launcher.add_cell_x";
    // Type: int
    private static final String RUNTIME_STATE_PENDING_ADD_CELL_Y = "launcher.add_cell_y";
    // Type: boolean
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME = "launcher.rename_folder";
    // Type: long
    private static final String RUNTIME_STATE_PENDING_FOLDER_RENAME_ID =
            "launcher.rename_folder_id";

    private static final String TOOLBAR_ICON_METADATA_NAME =
            "com.cyanogenmod.trebuchet.toolbar_icon";

    /** The different states that Launcher can be in. */
    private enum State { WORKSPACE, APPS_CUSTOMIZE, APPS_CUSTOMIZE_SPRING_LOADED };
    private State mState = State.WORKSPACE;
    private AnimatorSet mStateAnimation;
    private AnimatorSet mDividerAnimator;

    static final int APPWIDGET_HOST_ID = 1024;
    private static final int EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT = 300;
    private static final int EXIT_SPRINGLOADED_MODE_LONG_TIMEOUT = 600;
    private static final int SHOW_CLING_DURATION = 550;
    private static final int DISMISS_CLING_DURATION = 250;

    private static final Object sLock = new Object();
    private static int sScreen = DEFAULT_SCREEN;

    private final BroadcastReceiver mCloseSystemDialogsReceiver
            = new CloseSystemDialogsIntentReceiver();
    private final ContentObserver mWidgetObserver = new AppWidgetResetObserver();

    private LayoutInflater mInflater;

    private Workspace mWorkspace;
    private View mQsbDivider;
    private View mDockDivider;
    private View mDockDividerTwo;
    private DragLayer mDragLayer;
    private DragController mDragController;

    private AppWidgetManager mAppWidgetManager;
    private LauncherAppWidgetHost mAppWidgetHost;

    private ItemInfo mPendingAddInfo = new ItemInfo();
    private int[] mTmpAddItemCellCoordinates = new int[2];

    private FolderInfo mFolderInfo;

    private Hotseat mHotseat;
    private Hotseat mHotseatTwo;
    private RelativeLayout mAllAppsBar;

    private SearchDropTargetBar mSearchDropTargetBar;
    private AppsCustomizeTabHost mAppsCustomizeTabHost;
    private AppsCustomizePagedView mAppsCustomizeContent;
    private boolean mAutoAdvanceRunning = false;

    private Bundle mSavedState;

    private SpannableStringBuilder mDefaultKeySsb = null;

    private boolean mWorkspaceLoading = true;

    private boolean mPaused = true;
    private boolean mRestoring;
    private boolean mWaitingForResult;
    private boolean mOnResumeNeedsLoad;

    private Bundle mSavedInstanceState;

    private LauncherModel mModel;
    private IconCache mIconCache;
    private boolean mUserPresent = true;
    private boolean mVisible = false;
    private boolean mAttached = false;

    private static LocaleConfiguration sLocaleConfiguration = null;

    private static HashMap<Long, FolderInfo> sFolders = new HashMap<Long, FolderInfo>();

    private Intent mAppMarketIntent = null;

    // Related to the auto-advancing of widgets
    private final int ADVANCE_MSG = 1;
    private final int mAdvanceInterval = 20000;
    private final int mAdvanceStagger = 250;
    private long mAutoAdvanceSentTime;
    private long mAutoAdvanceTimeLeft = -1;
    private HashMap<View, AppWidgetProviderInfo> mWidgetsToAdvance =
        new HashMap<View, AppWidgetProviderInfo>();

    // Determines how long to wait after a rotation before restoring the screen orientation to
    // match the sensor state.
    private final int mRestoreScreenOrientationDelay = 500;

    // External icons saved in case of resource changes, orientation, etc.
    private static Drawable.ConstantState[] sGlobalSearchIcon = new Drawable.ConstantState[2];
    private static Drawable.ConstantState[] sVoiceSearchIcon = new Drawable.ConstantState[2];
    private static Drawable.ConstantState[] sAppMarketIcon = new Drawable.ConstantState[2];

    static final ArrayList<String> sDumpLogs = new ArrayList<String>();


    private BubbleTextView mWaitingForResume;

    // Preferences
    private boolean mShowSearchBar;
    private boolean mShowDockDivider;
    private boolean mShowDockDividerTwo;
    private boolean mHideIconLabels;
    private boolean mAutoRotate;
    private boolean mShowWallpaper;
    private int mAllAppsCorner;
    private int mSearchCorner;
    private boolean mCenterAllApps;
    private boolean mAllAppsTop;
    private boolean mAllAppsLeft;
    private boolean mSearchTop;
    private boolean mShowHotseat;
    private boolean mShowAllAppsBar;
    private boolean mHidePageControls;
    private boolean mCombinedBar;
    private boolean mSmallerIcons;
    private boolean mShowButtonDividers;
    private int mHomescreenDoubleTap;
    private int mHomescreenSwipeUp;
    private int mHomescreenSwipeDown;
    private int mDrawerSwipeUp;
    private int mDrawerSwipeDown;
    private int mAppBarLongClick;
    private int mActionButtonOne;
    private int mActionButtonTwo;
    private int mActionButtonThree;
    private int mActionButtonFour;
    private int mActionButtonFive;
    private int mActionButtonSix;
    private int mActionButtonSeven;
    private int mActionButtonEight;
    private boolean mStatusBarDisabled = false;

    private ImageView mCustomButtonOne;
    private ImageView mCustomButtonTwo;
    private ImageView mCustomButtonThree;
    private ImageView mCustomButtonFour;
    private ImageView mCustomButtonFive;
    private ImageView mCustomButtonSix;
    private ImageView mCustomButtonSeven;
    private ImageView mCustomButtonEight;

    private StatusBarManager mStatusBarManager;

    private Runnable mBuildLayersRunnable = new Runnable() {
        public void run() {
            if (mWorkspace != null) {
                mWorkspace.buildPageHardwareLayers();
            }
        }
    };

    private static ArrayList<PendingAddArguments> sPendingAddList
            = new ArrayList<PendingAddArguments>();

    private static class PendingAddArguments {
        int requestCode;
        Intent intent;
        long container;
        int screen;
        int cellX;
        int cellY;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LauncherApplication app = ((LauncherApplication)getApplication());
        mModel = app.setLauncher(this);
        mIconCache = app.getIconCache();
        mDragController = new DragController(this);
        mInflater = getLayoutInflater();

        mAppWidgetManager = AppWidgetManager.getInstance(this);
        mAppWidgetHost = new LauncherAppWidgetHost(this, APPWIDGET_HOST_ID);
        mAppWidgetHost.startListening();

        // Preferences
        mShowSearchBar = PreferencesProvider.Interface.Homescreen.getShowSearchBar(this);
        mShowDockDivider = PreferencesProvider.Interface.Homescreen.Indicator
                .getShowDockDivider(this);
        mShowDockDividerTwo = PreferencesProvider.Interface.Homescreen.Indicator
                .getShowDockDividerTwo(this);
        mHideIconLabels = PreferencesProvider.Interface.Homescreen.getHideIconLabels(this);
        mAutoRotate = PreferencesProvider.Interface.General.getAutoRotate(this, getResources()
                .getBoolean(R.bool.config_defaultAutoRotate));
        mShowWallpaper = PreferencesProvider.Interface.Drawer.Background
                .getBackgroundShowWallpaper(this);
        mCenterAllApps = PreferencesProvider.Interface.Tablet.getCenterAllAppsWorkspace(this);
        mAllAppsCorner = PreferencesProvider.Interface.Tablet.getAllAppsBarCorner(this);
        mSearchCorner = PreferencesProvider.Interface.Tablet.getSearchBarCorner(this);
        mShowHotseat = PreferencesProvider.Interface.Dock.getShowHotseat(this);
        mShowAllAppsBar = PreferencesProvider.Interface.Tablet.getShowAllAppsWorkspace(this);
        mHidePageControls = PreferencesProvider.Interface.Tablet.getHidePageControls(this);
        mCombinedBar = PreferencesProvider.Interface.Tablet.getCombinedBar(this);
        mSmallerIcons = PreferencesProvider.Interface.Tablet.getSmallerIcons(this);
        mShowButtonDividers = PreferencesProvider.Interface.Tablet.getButtonDividers(this);
        mHomescreenDoubleTap = PreferencesProvider.Interface.Gestures.getHomescreenDoubleTap(this);
        mHomescreenSwipeUp = PreferencesProvider.Interface.Gestures.getHomescreenSwipeUp(this);
        mHomescreenSwipeDown = PreferencesProvider.Interface.Gestures.getHomescreenSwipeDown(this);
        mDrawerSwipeUp = PreferencesProvider.Interface.Gestures.getDrawerSwipeUp(this);
        mDrawerSwipeDown = PreferencesProvider.Interface.Gestures.getDrawerSwipeDown(this);
        mAppBarLongClick = PreferencesProvider.Interface.Gestures.getAppBarLongClick(this);
        mActionButtonOne = PreferencesProvider.Interface.Tablet.getActionButtonOne(this);
        mActionButtonTwo = PreferencesProvider.Interface.Tablet.getActionButtonTwo(this);
        mActionButtonThree = PreferencesProvider.Interface.Tablet.getActionButtonThree(this);
        mActionButtonFour = PreferencesProvider.Interface.Tablet.getActionButtonFour(this);
        mActionButtonFive = PreferencesProvider.Interface.Tablet.getActionButtonFive(this);
        mActionButtonSix = PreferencesProvider.Interface.Tablet.getActionButtonSix(this);
        mActionButtonSeven = PreferencesProvider.Interface.Tablet.getActionButtonSeven(this);
        mActionButtonEight = PreferencesProvider.Interface.Tablet.getActionButtonEight(this);

        // Combine all apps and search bar and hide search bar if they are on the same corner
        if ((mAllAppsCorner == mSearchCorner) && mShowSearchBar &&
                LauncherApplication.isScreenLarge()) {
            SharedPreferences prefs =
                    getSharedPreferences(PreferencesProvider.PREFERENCES_KEY, Context.MODE_PRIVATE);
            prefs.edit().putBoolean("ui_tablet_workspace_combined_bar", true).commit();
            prefs.edit().putBoolean("ui_homescreen_general_search", false).commit();
            mCombinedBar = true;
            mShowSearchBar = false;
        }

        // All apps and search positions
        mAllAppsLeft = mAllAppsCorner > 1 ? true : false;
        mAllAppsTop = mAllAppsCorner == 0 || mAllAppsCorner == 3;
        mSearchTop = mSearchCorner == 0 || mSearchCorner == 3;

        if (PROFILE_STARTUP) {
            android.os.Debug.startMethodTracing(
                    Environment.getExternalStorageDirectory() + "/launcher");
        }

        checkForLocaleChange();
        setContentView(R.layout.launcher);
        setupViews();
        showFirstRunWorkspaceCling();

        registerContentObservers();

        lockAllApps();

        mSavedState = savedInstanceState;
        restoreState(mSavedState);

        // Update customization drawer _after_ restoring the states
        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.onPackagesUpdated();
        }

        if (PROFILE_STARTUP) {
            android.os.Debug.stopMethodTracing();
        }

        if (!mRestoring) {
            mModel.startLoader(this, true);
        }

        if (!mModel.isAllAppsLoaded()) {
            ViewGroup appsCustomizeContentParent =
                    (ViewGroup) ((View) mAppsCustomizeContent).getParent();
            mInflater.inflate(R.layout.apps_customize_progressbar, appsCustomizeContentParent);
        }

        // For handling default keys
        mDefaultKeySsb = new SpannableStringBuilder();
        Selection.setSelection(mDefaultKeySsb, 0);

        IntentFilter filter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(mCloseSystemDialogsReceiver, filter);

        boolean searchVisible = false;
        boolean voiceVisible = false;
        // If we have a saved version of these external icons, we load them up immediately
        int coi = getCurrentOrientationIndexForGlobalIcons();
        if (sGlobalSearchIcon[coi] == null || sVoiceSearchIcon[coi] == null ||
                sAppMarketIcon[coi] == null) {
            updateAppMarketIcon();
            searchVisible = updateGlobalSearchIcon();
            voiceVisible = updateVoiceSearchIcon(searchVisible);
        }
        if (sGlobalSearchIcon[coi] != null) {
             updateGlobalSearchIcon(sGlobalSearchIcon[coi]);
             searchVisible = true;
        }
        if (sVoiceSearchIcon[coi] != null) {
            updateVoiceSearchIcon(sVoiceSearchIcon[coi]);
            voiceVisible = true;
        }
        if (sAppMarketIcon[coi] != null) {
            updateAppMarketIcon(sAppMarketIcon[coi]);
        }
        mSearchDropTargetBar.onSearchPackagesChanged(searchVisible, voiceVisible);

        syncOrientation();
    }

    private void syncOrientation() {
        final UiModeManager uiModeManager =
                (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (mAutoRotate || uiModeManager.getCurrentModeType() !=
                Configuration.UI_MODE_TYPE_NORMAL) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        }
    }

    private void checkForLocaleChange() {
        if (sLocaleConfiguration == null) {
            new AsyncTask<Void, Void, LocaleConfiguration>() {
                @Override
                protected LocaleConfiguration doInBackground(Void... unused) {
                    LocaleConfiguration localeConfiguration = new LocaleConfiguration();
                    readConfiguration(Launcher.this, localeConfiguration);
                    return localeConfiguration;
                }

                @Override
                protected void onPostExecute(LocaleConfiguration result) {
                    sLocaleConfiguration = result;
                    checkForLocaleChange();  // recursive, but now with a locale configuration
                }
            }.execute();
            return;
        }

        final Configuration configuration = getResources().getConfiguration();

        final String previousLocale = sLocaleConfiguration.locale;
        final String locale = configuration.locale.toString();

        final int previousMcc = sLocaleConfiguration.mcc;
        final int mcc = configuration.mcc;

        final int previousMnc = sLocaleConfiguration.mnc;
        final int mnc = configuration.mnc;

        boolean localeChanged =
                !locale.equals(previousLocale) || mcc != previousMcc || mnc != previousMnc;

        if (localeChanged) {
            sLocaleConfiguration.locale = locale;
            sLocaleConfiguration.mcc = mcc;
            sLocaleConfiguration.mnc = mnc;

            mIconCache.flush();

            final LocaleConfiguration localeConfiguration = sLocaleConfiguration;
            new Thread("WriteLocaleConfiguration") {
                @Override
                public void run() {
                    writeConfiguration(Launcher.this, localeConfiguration);
                }
            }.start();
        }
    }

    private static class LocaleConfiguration {
        public String locale;
        public int mcc = -1;
        public int mnc = -1;
    }

    private static void readConfiguration(Context context, LocaleConfiguration configuration) {
        DataInputStream in = null;
        try {
            in = new DataInputStream(context.openFileInput(PREFERENCES));
            configuration.locale = in.readUTF();
            configuration.mcc = in.readInt();
            configuration.mnc = in.readInt();
        } catch (FileNotFoundException e) {
            // Ignore
        } catch (IOException e) {
            // Ignore
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    private static void writeConfiguration(Context context, LocaleConfiguration configuration) {
        DataOutputStream out = null;
        try {
            out = new DataOutputStream(context.openFileOutput(PREFERENCES, MODE_PRIVATE));
            out.writeUTF(configuration.locale);
            out.writeInt(configuration.mcc);
            out.writeInt(configuration.mnc);
            out.flush();
        } catch (FileNotFoundException e) {
            // Ignore
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            context.getFileStreamPath(PREFERENCES).delete();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
    }

    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    static int getScreen() {
        synchronized (sLock) {
            return sScreen;
        }
    }

    static void setScreen(int screen) {
        synchronized (sLock) {
            sScreen = screen;
        }
    }

    /**
     * Returns whether we should delay spring loaded mode -- for shortcuts and widgets that have
     * a configuration step, this allows the proper animations to run after other transitions.
     */
    private boolean completeAdd(PendingAddArguments args) {
        boolean result = false;
        switch (args.requestCode) {
            case REQUEST_PICK_APPLICATION:
                completeAddApplication(args.intent, args.container, args.screen, args.cellX,
                        args.cellY);
                break;
            case REQUEST_PICK_SHORTCUT:
                processShortcut(args.intent);
                break;
            case REQUEST_CREATE_SHORTCUT:
                completeAddShortcut(args.intent, args.container, args.screen, args.cellX,
                        args.cellY);
                result = true;
                break;
            case REQUEST_PICK_APPWIDGET:
                addAppWidgetFromPick(args.intent);
                break;
            case REQUEST_CREATE_APPWIDGET:
                int appWidgetId = args.intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                completeAddAppWidget(appWidgetId, args.container, args.screen);
                result = true;
                break;
            case REQUEST_PICK_WALLPAPER:
                // We just wanted the activity result here so we can clear mWaitingForResult
                break;
        }
        // In any situation where we have a multi-step drop, we should reset the add info only after
        // we complete the drop
        resetAddInfo();
        return result;
    }

    @Override
    protected void onActivityResult(final int requestCode, int resultCode, final Intent data) {
        boolean delayExitSpringLoadedMode = false;
        mWaitingForResult = false;

        // The pattern used here is that a user PICKs a specific application,
        // which, depending on the target, might need to CREATE the actual target.

        // For example, the user would PICK_SHORTCUT for "Music playlist", and we
        // launch over to the Music app to actually CREATE_SHORTCUT.
        if (resultCode == RESULT_OK && mPendingAddInfo.container != ItemInfo.NO_ID) {
            final PendingAddArguments args = new PendingAddArguments();
            args.requestCode = requestCode;
            args.intent = data;
            args.container = mPendingAddInfo.container;
            args.screen = mPendingAddInfo.screen;
            args.cellX = mPendingAddInfo.cellX;
            args.cellY = mPendingAddInfo.cellY;

            // If the loader is still running, defer the add until it is done.
            if (isWorkspaceLocked()) {
                sPendingAddList.add(args);
            } else {
                delayExitSpringLoadedMode = completeAdd(args);
            }
        } else if ((requestCode == REQUEST_PICK_APPWIDGET ||
                requestCode == REQUEST_CREATE_APPWIDGET) && resultCode == RESULT_CANCELED) {
            if (data != null) {
                // Clean up the appWidgetId if we canceled
                int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                if (appWidgetId != -1) {
                    mAppWidgetHost.deleteAppWidgetId(appWidgetId);
                }
            }
        }

        // Exit spring loaded mode if necessary after cancelling the configuration of a widget
        exitSpringLoadedDragModeDelayed((resultCode != RESULT_CANCELED), delayExitSpringLoadedMode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPaused = false;
        // Restart launcher when preferences are changed
        if (preferencesChanged()) {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
        if (mRestoring || mOnResumeNeedsLoad) {
            mWorkspaceLoading = true;
            mModel.startLoader(this, true);
            mRestoring = false;
            mOnResumeNeedsLoad = false;
        }
        if (mWaitingForResume != null) {
            mWaitingForResume.setStayPressed(false);
        }
        // When we resume Launcher, a different Activity might be responsible for the app
        // market intent, so refresh the icon
        updateAppMarketIcon();
        mAppsCustomizeTabHost.onResume();
        if (!mWorkspaceLoading) {
            final ViewTreeObserver observer = mWorkspace.getViewTreeObserver();
            final Workspace workspace = mWorkspace;
            // We want to let Launcher draw itself at least once before we force it to build
            // layers on all the workspace pages, so that transitioning to Launcher from other
            // apps is nice and speedy. Usually the first call to preDraw doesn't correspond to
            // a true draw so we wait until the second preDraw call to be safe
            observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                boolean mFirstTime = true;
                public boolean onPreDraw() {
                    if (mFirstTime) {
                        mFirstTime = false;
                    } else {
                        // We delay the layer building a bit in order to give
                        // other message processing a time to run.  In particular
                        // this avoids a delay in hiding the IME if it was
                        // currently shown, because doing that may involve
                        // some communication back with the app.
                        workspace.postDelayed(mBuildLayersRunnable, 500);
                        observer.removeOnPreDrawListener(this);
                    }
                    return true;
                }
            });
        }
        clearTypedText();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPaused = true;
        mDragController.cancelDrag();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        // Flag the loader to stop early before switching
        mModel.stopLoader();
        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.surrender();
        }
        return Boolean.TRUE;
    }

    // We can't hide the IME if it was forced open.  So don't bother
    /*
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            final InputMethodManager inputManager = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            inputManager.hideSoftInputFromWindow(lp.token, 0, new android.os.ResultReceiver(new
                        android.os.Handler()) {
                        protected void onReceiveResult(int resultCode, Bundle resultData) {
                            Log.d(TAG, "ResultReceiver got resultCode=" + resultCode);
                        }
                    });
            Log.d(TAG, "called hideSoftInputFromWindow from onWindowFocusChanged");
        }
    }
    */

    private boolean acceptFilter() {
        final InputMethodManager inputManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        return !inputManager.isFullscreenMode();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final int uniChar = event.getUnicodeChar();
        final boolean handled = super.onKeyDown(keyCode, event);
        final boolean isKeyNotWhitespace = uniChar > 0 && !Character.isWhitespace(uniChar);
        if (!handled && acceptFilter() && isKeyNotWhitespace) {
            boolean gotKey = TextKeyListener.getInstance().onKeyDown(mWorkspace, mDefaultKeySsb,
                    keyCode, event);
            if (gotKey && mDefaultKeySsb != null && mDefaultKeySsb.length() > 0) {
                // something usable has been typed - start a search
                // the typed text will be retrieved and cleared by
                // showSearchDialog()
                // If there are multiple keystrokes before the search dialog takes focus,
                // onSearchRequested() will be called for every keystroke,
                // but it is idempotent, so it's fine.
                return onSearchRequested();
            }
        }

        // Eat the long press event so the keyboard doesn't come up.
        if (keyCode == KeyEvent.KEYCODE_MENU && event.isLongPress()) {
            return true;
        }

        return handled;
    }

    private String getTypedText() {
        return mDefaultKeySsb.toString();
    }

    private void clearTypedText() {
        mDefaultKeySsb.clear();
        mDefaultKeySsb.clearSpans();
        Selection.setSelection(mDefaultKeySsb, 0);
    }

    /**
     * Given the integer (ordinal) value of a State enum instance, convert it to a variable of type
     * State
     */
    private static State intToState(int stateOrdinal) {
        State state = State.WORKSPACE;
        final State[] stateValues = State.values();
        for (int i = 0; i < stateValues.length; i++) {
            if (stateValues[i].ordinal() == stateOrdinal) {
                state = stateValues[i];
                break;
            }
        }
        return state;
    }

    /**
     * Restores the previous state, if it exists.
     *
     * @param savedState The previous state.
     */
    private void restoreState(Bundle savedState) {
        if (savedState == null) {
            return;
        }

        State state = intToState(savedState.getInt(RUNTIME_STATE, State.WORKSPACE.ordinal()));
        if (state == State.APPS_CUSTOMIZE) {
            showAllApps(false);
        }

        final int currentScreen = savedState.getInt(RUNTIME_STATE_CURRENT_SCREEN, -1);
        if (currentScreen > -1) {
            mWorkspace.setCurrentPage(currentScreen);
        }

        final long pendingAddContainer =
                savedState.getLong(RUNTIME_STATE_PENDING_ADD_CONTAINER, -1);
        final int pendingAddScreen = savedState.getInt(RUNTIME_STATE_PENDING_ADD_SCREEN, -1);

        if (pendingAddContainer != ItemInfo.NO_ID && pendingAddScreen > -1) {
            mPendingAddInfo.container = pendingAddContainer;
            mPendingAddInfo.screen = pendingAddScreen;
            mPendingAddInfo.cellX = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_X);
            mPendingAddInfo.cellY = savedState.getInt(RUNTIME_STATE_PENDING_ADD_CELL_Y);
            mRestoring = true;
        }

        boolean renameFolder = savedState.getBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, false);
        if (renameFolder) {
            long id = savedState.getLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID);
            mFolderInfo = mModel.getFolderById(this, sFolders, id);
            mRestoring = true;
        }


        // Restore the AppsCustomize tab
        if (mAppsCustomizeTabHost != null) {
            String curTab = savedState.getString("apps_customize_currentTab");
            if (curTab != null) {
                // We set this directly so that there is no delay before the tab is set
                mAppsCustomizeContent.setContentType(
                        mAppsCustomizeTabHost.getContentTypeForTabTag(curTab));
                mAppsCustomizeTabHost.setCurrentTabByTag(curTab);
            }

            int currentIndex = savedState.getInt("apps_customize_currentIndex");
            mAppsCustomizeContent.restore(currentIndex);
        }
    }

    /**
     * Finds all the views we need and configure them properly.
     */
    private void setupViews() {
        final DragController dragController = mDragController;

        mDragLayer = (DragLayer) findViewById(R.id.drag_layer);
        mWorkspace = (Workspace) mDragLayer.findViewById(R.id.workspace);
        mQsbDivider = (ImageView) findViewById(R.id.qsb_divider);
        mDockDivider = (ImageView) findViewById(R.id.dock_divider);
        mDockDividerTwo = (ImageView) findViewById(R.id.dock_divider_two);

        // Setup the drag layer
        mDragLayer.setup(this, dragController);

        // Setup the hotseat
        mHotseat = (Hotseat) findViewById(R.id.hotseat);
        if (mHotseat != null) {
            mHotseat.setup(this);
            mHotseat.resetLayout(true);
        }

        mHotseatTwo = (Hotseat) findViewById(R.id.hotseat_two);
        if (mHotseatTwo != null) {
            mHotseatTwo.setup(this);
            mHotseatTwo.resetLayout(false);
        }

        // Setup the workspace
        mWorkspace.setHapticFeedbackEnabled(false);
        mWorkspace.setOnLongClickListener(this);
        mWorkspace.setup(dragController);
        dragController.addDragListener(mWorkspace);
        if (mHomescreenDoubleTap != 0) {
            mWorkspace.setOnDoubleTapCallback(new Runnable() {
                public void run() {
                    performGesture(mHomescreenDoubleTap, 0);
                }
            });
        }
        if (mHomescreenSwipeUp != 0) {
            mWorkspace.setOnSwipeUpCallback(new Runnable() {
                public void run() {
                    performGesture(mHomescreenSwipeUp, 1);
                }
            });
        }
        if (mHomescreenSwipeDown != 0) {
            mWorkspace.setOnSwipeDownCallback(new Runnable() {
                public void run() {
                    performGesture(mHomescreenSwipeDown, 2);
                }
            });
        }

        // Get the search/delete bar
        mSearchDropTargetBar = (SearchDropTargetBar) mDragLayer.findViewById(R.id.qsb_bar);

        // Hide the search divider if we are hiding search bar
        if (!mShowSearchBar && mQsbDivider != null) {
            mQsbDivider.setVisibility(View.GONE);
        }

        // Hide the dock dividers and set up padding if necessary
        if (!mShowDockDivider && mDockDivider != null) {
            mDockDivider.setVisibility(View.GONE);
            if (mQsbDivider != null) mQsbDivider.setVisibility(View.GONE);
        }
        if (!mShowDockDividerTwo && mDockDividerTwo != null) {
            if (mDockDividerTwo != null) mDockDividerTwo.setVisibility(View.GONE);
        }
        if (mShowDockDivider && ((mShowSearchBar && !mSearchTop) || (!mShowSearchBar &&
                !mAllAppsTop)) && LauncherApplication.isScreenLarge() &&
                LauncherApplication.isScreenLandscape(getApplicationContext())) {
            FrameLayout.LayoutParams dividerMargins = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT);
            dividerMargins.setMargins(0, 0,
                    mSmallerIcons ? getResources().getDimensionPixelSize(R.dimen.divider_margin_sm)
                    : getResources().getDimensionPixelSize(R.dimen.divider_margin),
                    getResources().getDimensionPixelSize(R.dimen.qsb_bar_height));
            dividerMargins.gravity = Gravity.RIGHT;
            mDockDivider.setLayoutParams(dividerMargins);
        }
        if (mShowDockDividerTwo && ((mShowSearchBar && !mSearchTop) || (!mShowSearchBar &&
                !mAllAppsTop)) && LauncherApplication.isScreenLarge() &&
                LauncherApplication.isScreenLandscape(getApplicationContext())) {
            FrameLayout.LayoutParams dividerTwoMargins = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.MATCH_PARENT);
            dividerTwoMargins.setMargins(mSmallerIcons ?
                    getResources().getDimensionPixelSize(R.dimen.divider_margin_sm) :
                    getResources().getDimensionPixelSize(R.dimen.divider_margin), 0, 0,
                    getResources().getDimensionPixelSize(R.dimen.qsb_bar_height));
            dividerTwoMargins.gravity = Gravity.LEFT;
            mDockDividerTwo.setLayoutParams(dividerTwoMargins);
        }
        if ((mShowDockDivider || mShowDockDividerTwo) && !mShowHotseat) hideDockDivider(false);

        // Setup AppsCustomize
        mAppsCustomizeTabHost = (AppsCustomizeTabHost)
                findViewById(R.id.apps_customize_pane);
        if (mShowWallpaper) mAppsCustomizeTabHost.setBackgroundColor(0x00000000);
        mAppsCustomizeContent = (AppsCustomizePagedView)
                mAppsCustomizeTabHost.findViewById(R.id.apps_customize_pane_content);
        mAppsCustomizeContent.setup(this, dragController);
        if (mDrawerSwipeUp != 0) {
            mAppsCustomizeContent.setOnSwipeUpCallback(new Runnable() {
                public void run() {
                    performGesture(mDrawerSwipeUp, 3);
                }
            });
        }
        if (mDrawerSwipeDown != 0) {
            mAppsCustomizeContent.setOnSwipeDownCallback(new Runnable() {
                public void run() {
                    performGesture(mDrawerSwipeDown, 4);
                }
            });
        }

        // Setup the all apps bar
        mAllAppsBar = (RelativeLayout) findViewById(R.id.all_apps_bar);
        if (mAllAppsBar != null) {
            mCustomButtonOne = (ImageView) findViewById(R.id.workspace_custom_button_one);
            mCustomButtonOne.setOnLongClickListener(this);
            mCustomButtonTwo = (ImageView) findViewById(R.id.workspace_custom_button_two);
            mCustomButtonTwo.setOnLongClickListener(this);
            mCustomButtonThree = (ImageView) findViewById(R.id.workspace_custom_button_three);
            mCustomButtonThree.setOnLongClickListener(this);
            mCustomButtonFour = (ImageView) findViewById(R.id.workspace_custom_button_four);
            mCustomButtonFour.setOnLongClickListener(this);
            mCustomButtonFive = (ImageView) findViewById(R.id.workspace_custom_button_five);
            mCustomButtonFive.setOnLongClickListener(this);
            mCustomButtonSix = (ImageView) findViewById(R.id.workspace_custom_button_six);
            mCustomButtonSix.setOnLongClickListener(this);
            mCustomButtonSeven = (ImageView) findViewById(R.id.workspace_custom_button_seven);
            mCustomButtonSeven.setOnLongClickListener(this);
            mCustomButtonEight = (ImageView) findViewById(R.id.workspace_custom_button_eight);
            mCustomButtonEight.setOnLongClickListener(this);
            View buttonDivider = findViewById(R.id.button_divider_all_apps);
            View buttonDividerTwo = findViewById(R.id.button_divider_all_apps_two);
            View buttonDividerThree = findViewById(R.id.button_divider_all_apps_three);
            View buttonDividerFour = findViewById(R.id.button_divider_all_apps_four);
            View buttonDividerFive = findViewById(R.id.button_divider_all_apps_five);
            View buttonDividerSix = findViewById(R.id.button_divider_all_apps_six);
            View buttonDividerSeven = findViewById(R.id.button_divider_all_apps_seven);

            SharedPreferences prefs =
                    getSharedPreferences(PreferencesProvider.PREFERENCES_KEY, Context.MODE_PRIVATE);

            if (mActionButtonOne != 0) {
                String customUriOne = prefs.getString("custom_application_one", "");
                RelativeLayout.LayoutParams buttonOneParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                buttonOneParams.addRule(!mAllAppsLeft ? RelativeLayout.ALIGN_PARENT_RIGHT :
                        RelativeLayout.ALIGN_PARENT_LEFT);
                if (mActionButtonOne == 1) {
                    mCustomButtonOne.setImageResource(R.drawable.ic_home_all_apps_holo_dark);
                    mCustomButtonOne.setPadding(10, 4, 10, 4);
                } else if (mActionButtonOne == 2) {
                    if (mAllAppsTop) {
                        mCustomButtonOne.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_btn : R.drawable.hotseat_close_btn);
                    } else {
                        mCustomButtonOne.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_close_btn : R.drawable.hotseat_btn);
                    }
                } else {
                    try {
                        mCustomButtonOne.setImageDrawable(this.getPackageManager().getActivityIcon(
                                Intent.parseUri(customUriOne, 0)));
                    } catch (Exception e) {
                    }
                }
                mCustomButtonOne.setLayoutParams(buttonOneParams);
                mCustomButtonOne.setVisibility(View.VISIBLE);
            }

            if (mActionButtonTwo != 0) {
                String customUriTwo = prefs.getString("custom_application_two", "");
                RelativeLayout.LayoutParams dividerParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                dividerParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, mCustomButtonOne.getId());
                dividerParams.addRule(RelativeLayout.CENTER_VERTICAL);
                buttonDivider.setLayoutParams(dividerParams);
                if (mActionButtonOne != 0) {
                    buttonDivider.setVisibility(mShowButtonDividers ? View.VISIBLE :
                            View.INVISIBLE);
                }

                RelativeLayout.LayoutParams buttonTwoParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                buttonTwoParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, buttonDivider.getId());
                if (mActionButtonTwo == 1) {
                    mCustomButtonTwo.setImageResource(R.drawable.ic_home_all_apps_holo_dark);
                    mCustomButtonTwo.setPadding(10, 4, 10, 4);
                } else if (mActionButtonTwo == 2) {
                    if (mAllAppsTop) {
                        mCustomButtonTwo.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_btn : R.drawable.hotseat_close_btn);
                    } else {
                        mCustomButtonTwo.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_close_btn : R.drawable.hotseat_btn);
                    }
                } else {
                    try {
                        mCustomButtonTwo.setImageDrawable(this.getPackageManager().getActivityIcon(
                                Intent.parseUri(customUriTwo, 0)));
                    } catch (Exception e) {
                    }
                }
                mCustomButtonTwo.setLayoutParams(buttonTwoParams);
                mCustomButtonTwo.setVisibility(View.VISIBLE);
            }

            if (mActionButtonThree != 0) {
                String customUriThree = prefs.getString("custom_application_three", "");
                RelativeLayout.LayoutParams dividerTwoParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                dividerTwoParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, mCustomButtonTwo.getId());
                dividerTwoParams.addRule(RelativeLayout.CENTER_VERTICAL);
                buttonDividerTwo.setLayoutParams(dividerTwoParams);
                if (mActionButtonTwo != 0 || mActionButtonOne != 0) {
                    buttonDividerTwo.setVisibility(mShowButtonDividers ? View.VISIBLE :
                            View.INVISIBLE);
                }

                RelativeLayout.LayoutParams buttonThreeParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                buttonThreeParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, buttonDividerTwo.getId());
                if (mActionButtonThree == 1) {
                    mCustomButtonThree.setImageResource(R.drawable.ic_home_all_apps_holo_dark);
                    mCustomButtonThree.setPadding(10, 4, 10, 4);
                } else if (mActionButtonThree == 2) {
                    if (mAllAppsTop) {
                        mCustomButtonThree.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_btn : R.drawable.hotseat_close_btn);
                    } else {
                        mCustomButtonThree.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_close_btn : R.drawable.hotseat_btn);
                    }
                } else {
                    try {
                        mCustomButtonThree.setImageDrawable(this.getPackageManager().getActivityIcon(
                                Intent.parseUri(customUriThree, 0)));
                    } catch (Exception e) {
                    }
                }
                mCustomButtonThree.setLayoutParams(buttonThreeParams);
                mCustomButtonThree.setVisibility(View.VISIBLE);
            }

            if (mActionButtonFour != 0) {
                String customUriFour = prefs.getString("custom_application_four", "");
                RelativeLayout.LayoutParams dividerThreeParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                dividerThreeParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, mCustomButtonThree.getId());
                dividerThreeParams.addRule(RelativeLayout.CENTER_VERTICAL);
                buttonDividerThree.setLayoutParams(dividerThreeParams);
                if (mActionButtonThree != 0 || mActionButtonTwo != 0 || mActionButtonOne != 0) {
                    buttonDividerThree.setVisibility(mShowButtonDividers ? View.VISIBLE :
                            View.INVISIBLE);
                }

                RelativeLayout.LayoutParams buttonFourParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                buttonFourParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, buttonDividerThree.getId());
                if (mActionButtonFour == 1) {
                    mCustomButtonFour.setImageResource(R.drawable.ic_home_all_apps_holo_dark);
                    mCustomButtonFour.setPadding(10, 4, 10, 4);
                } else if (mActionButtonFour == 2) {
                    if (mAllAppsTop) {
                        mCustomButtonFour.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_btn : R.drawable.hotseat_close_btn);
                    } else {
                        mCustomButtonFour.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_close_btn : R.drawable.hotseat_btn);
                    }
                } else {
                    try {
                        mCustomButtonFour.setImageDrawable(this.getPackageManager().getActivityIcon(
                                Intent.parseUri(customUriFour, 0)));
                    } catch (Exception e) {
                    }
                }
                mCustomButtonFour.setLayoutParams(buttonFourParams);
                mCustomButtonFour.setVisibility(View.VISIBLE);
            }

            if (mActionButtonFive != 0) {
                String customUriFive = prefs.getString("custom_application_five", "");
                RelativeLayout.LayoutParams dividerFourParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                dividerFourParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, mCustomButtonFour.getId());
                dividerFourParams.addRule(RelativeLayout.CENTER_VERTICAL);
                buttonDividerFour.setLayoutParams(dividerFourParams);
                if (mActionButtonFour != 0 || mActionButtonThree != 0 ||
                        mActionButtonTwo != 0 || mActionButtonOne != 0) {
                    buttonDividerFour.setVisibility(mShowButtonDividers ? View.VISIBLE :
                            View.INVISIBLE);
                }

                RelativeLayout.LayoutParams buttonFiveParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                buttonFiveParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, buttonDividerFour.getId());
                if (mActionButtonFive == 1) {
                    mCustomButtonFive.setImageResource(R.drawable.ic_home_all_apps_holo_dark);
                    mCustomButtonFive.setPadding(10, 4, 10, 4);
                } else if (mActionButtonFive == 2) {
                    if (mAllAppsTop) {
                        mCustomButtonFive.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_btn : R.drawable.hotseat_close_btn);
                    } else {
                        mCustomButtonFive.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_close_btn : R.drawable.hotseat_btn);
                    }
                } else {
                    try {
                        mCustomButtonFive.setImageDrawable(this.getPackageManager().getActivityIcon(
                                Intent.parseUri(customUriFive, 0)));
                    } catch (Exception e) {
                    }
                }
                mCustomButtonFive.setLayoutParams(buttonFiveParams);
                mCustomButtonFive.setVisibility(View.VISIBLE);
            }

            if (mActionButtonSix != 0) {
                String customUriSix = prefs.getString("custom_application_six", "");
                RelativeLayout.LayoutParams dividerFiveParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                dividerFiveParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, mCustomButtonFive.getId());
                dividerFiveParams.addRule(RelativeLayout.CENTER_VERTICAL);
                buttonDividerFive.setLayoutParams(dividerFiveParams);
                if (mActionButtonFive != 0 || mActionButtonFour != 0 || mActionButtonThree != 0 ||
                        mActionButtonTwo != 0 || mActionButtonOne != 0) {
                    buttonDividerFive.setVisibility(mShowButtonDividers ? View.VISIBLE :
                            View.INVISIBLE);
                }

                RelativeLayout.LayoutParams buttonSixParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                buttonSixParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, buttonDividerFive.getId());
                if (mActionButtonSix == 1) {
                    mCustomButtonSix.setImageResource(R.drawable.ic_home_all_apps_holo_dark);
                    mCustomButtonSix.setPadding(10, 4, 10, 4);
                } else if (mActionButtonSix == 2) {
                    if (mAllAppsTop) {
                        mCustomButtonSix.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_btn : R.drawable.hotseat_close_btn);
                    } else {
                        mCustomButtonSix.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_close_btn : R.drawable.hotseat_btn);
                    }
                } else {
                    try {
                        mCustomButtonSix.setImageDrawable(this.getPackageManager().getActivityIcon(
                                Intent.parseUri(customUriSix, 0)));
                    } catch (Exception e) {
                    }
                }
                mCustomButtonSix.setLayoutParams(buttonSixParams);
                mCustomButtonSix.setVisibility(View.VISIBLE);
            }

            if (mActionButtonSeven != 0 || mCombinedBar) {
                String customUriSeven = prefs.getString("custom_application_seven", "");
                RelativeLayout.LayoutParams dividerSixParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                dividerSixParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, mCustomButtonSix.getId());
                dividerSixParams.addRule(RelativeLayout.CENTER_VERTICAL);
                buttonDividerSix.setLayoutParams(dividerSixParams);
                if (mActionButtonSix != 0 || mActionButtonFive != 0 ||
                        mActionButtonFour != 0 || mActionButtonThree != 0 ||
                        mActionButtonTwo != 0 || mActionButtonOne != 0) {
                    buttonDividerSix.setVisibility(mShowButtonDividers ? View.VISIBLE :
                            View.INVISIBLE);
                }

                RelativeLayout.LayoutParams buttonSevenParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                buttonSevenParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, buttonDividerSix.getId());
                if (mCombinedBar) mActionButtonSeven = 4;
                if (mActionButtonSeven == 1) {
                    mCustomButtonSeven.setImageResource(R.drawable.ic_home_all_apps_holo_dark);
                    mCustomButtonSeven.setPadding(10, 4, 10, 4);
                } else if (mActionButtonSeven == 2) {
                    if (mAllAppsTop) {
                        mCustomButtonSeven.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_btn : R.drawable.hotseat_close_btn);
                    } else {
                        mCustomButtonSeven.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_close_btn : R.drawable.hotseat_btn);
                    }
                } else if (mActionButtonSeven == 3) {
                    try {
                        mCustomButtonSeven.setImageDrawable(this.getPackageManager().getActivityIcon(
                                Intent.parseUri(customUriSeven, 0)));
                    } catch (Exception e) {
                    }
                }
                mCustomButtonSeven.setLayoutParams(buttonSevenParams);
                mCustomButtonSeven.setVisibility(View.VISIBLE);
            }

            if (mActionButtonEight != 0 || mCombinedBar) {
                String customUriEight = prefs.getString("custom_application_eight", "");
                RelativeLayout.LayoutParams dividerSevenParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                dividerSevenParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, mCustomButtonSeven.getId());
                dividerSevenParams.addRule(RelativeLayout.CENTER_VERTICAL);
                buttonDividerSeven.setLayoutParams(dividerSevenParams);
                if (mActionButtonSeven != 0 || mActionButtonSix != 0 || mActionButtonFive != 0 ||
                        mActionButtonFour != 0 || mActionButtonThree != 0 ||
                        mActionButtonTwo != 0 || mActionButtonOne != 0) {
                    buttonDividerSeven.setVisibility(mShowButtonDividers ? View.VISIBLE :
                            View.INVISIBLE);
                }

                RelativeLayout.LayoutParams buttonEightParams = new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
                buttonEightParams.addRule(!mAllAppsLeft ? RelativeLayout.LEFT_OF :
                        RelativeLayout.RIGHT_OF, buttonDividerSeven.getId());
                if (mCombinedBar) mActionButtonEight = 5;
                if (mActionButtonEight == 1) {
                    mCustomButtonEight.setImageResource(R.drawable.ic_home_all_apps_holo_dark);
                    mCustomButtonEight.setPadding(10, 4, 10, 4);
                } else if (mActionButtonEight == 2) {
                    if (mAllAppsTop) {
                        mCustomButtonEight.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_btn : R.drawable.hotseat_close_btn);
                    } else {
                        mCustomButtonEight.setImageResource(mShowHotseat ?
                                R.drawable.hotseat_close_btn : R.drawable.hotseat_btn);
                    }
                } else if (mActionButtonEight == 3) {
                    try {
                        mCustomButtonEight.setImageDrawable(this.getPackageManager().getActivityIcon(
                                Intent.parseUri(customUriEight, 0)));
                    } catch (Exception e) {
                    }
                } else if (mActionButtonEight == 5) {
                    mCustomButtonEight.setImageResource(R.drawable.ic_home_voice_search_holo);
                }
                mCustomButtonEight.setLayoutParams(buttonEightParams);
                mCustomButtonEight.setVisibility(View.VISIBLE);
            }

            // Set gravity of all apps bar to bottom
            if (!mAllAppsTop) {
                FrameLayout.LayoutParams allAppsBarParams = new FrameLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        getResources().getDimensionPixelSize(R.dimen.qsb_bar_height));
                allAppsBarParams.gravity = Gravity.BOTTOM;
                mAllAppsBar.setLayoutParams(allAppsBarParams);
            }

            // Center the apps bar
            if (!mShowSearchBar && mCenterAllApps) {
                mAllAppsBar.setGravity(Gravity.CENTER);
            }

            if (!mShowAllAppsBar) mAllAppsBar.setVisibility(View.GONE);
        }

        // Setup the drag controller (drop targets have to be added in reverse order in priority)
        dragController.setDragScoller(mWorkspace);
        dragController.setScrollView(mDragLayer);
        dragController.setMoveTarget(mWorkspace);
        dragController.addDropTarget(mWorkspace);

        // Setup search drop target bar and move to bottom if necessary
        if (mSearchDropTargetBar != null) {
            mSearchDropTargetBar.setup(this, dragController);
            if (((mShowSearchBar && !mSearchTop) || (!mShowSearchBar && !mAllAppsTop))
                    && LauncherApplication.isScreenLarge()) {
                FrameLayout.LayoutParams searchParams = new FrameLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        getResources().getDimensionPixelSize(R.dimen.qsb_bar_height));
                searchParams.gravity = Gravity.BOTTOM;
                mSearchDropTargetBar.setLayoutParams(searchParams);
                if (LauncherApplication.isScreenLandscape(getApplicationContext())) {
                    mWorkspace.setPadding(0, 0, 0, getResources().getDimensionPixelSize(
                            R.dimen.workspace_content_large_only_top_margin));
                }
            }
        }
    }

    /**
     * Creates a view representing a shortcut.
     *
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from R.layout.application.
     */
    View createShortcut(ShortcutInfo info) {
        return createShortcut(R.layout.application,
                (ViewGroup) mWorkspace.getChildAt(mWorkspace.getCurrentPage()), info);
    }

    /**
     * Creates a view representing a shortcut inflated from the specified resource.
     *
     * @param layoutResId The id of the XML layout used to create the shortcut.
     * @param parent The group the shortcut belongs to.
     * @param info The data structure describing the shortcut.
     *
     * @return A View inflated from layoutResId.
     */
    View createShortcut(int layoutResId, ViewGroup parent, ShortcutInfo info) {
        BubbleTextView favorite = (BubbleTextView) mInflater.inflate(layoutResId, parent, false);
        favorite.applyFromShortcutInfo(info, mIconCache);
        if (mHideIconLabels) {
            favorite.setTextVisible(false);
        }
        favorite.setOnClickListener(this);
        return favorite;
    }

    /**
     * Add an application shortcut to the workspace.
     *
     * @param data The intent describing the application.
     * @param cellInfo The position on screen where to create the shortcut.
     */
    void completeAddApplication(Intent data, long container, int screen, int cellX, int cellY) {
        final int[] cellXY = mTmpAddItemCellCoordinates;
        final CellLayout layout = getCellLayout(container, screen);

        // First we check if we already know the exact location where we want to add this item.
        if (cellX >= 0 && cellY >= 0) {
            cellXY[0] = cellX;
            cellXY[1] = cellY;
        } else if (!layout.findCellForSpan(cellXY, 1, 1)) {
            showOutOfSpaceMessage();
            return;
        }

        final ShortcutInfo info = mModel.getShortcutInfo(getPackageManager(), data, this);

        if (info != null) {
            info.setActivity(data.getComponent(), Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            info.container = ItemInfo.NO_ID;
            mWorkspace.addApplicationShortcut(info, layout, container, screen, cellXY[0], cellXY[1],
                    isWorkspaceLocked(), cellX, cellY);
        } else {
            Log.e(TAG, "Couldn't find ActivityInfo for selected application: " + data);
        }
    }

    /**
     * Add a shortcut to the workspace.
     *
     * @param data The intent describing the shortcut.
     * @param cellInfo The position on screen where to create the shortcut.
     */
    private void completeAddShortcut(Intent data, long container, int screen, int cellX,
            int cellY) {
        int[] cellXY = mTmpAddItemCellCoordinates;
        int[] touchXY = mPendingAddInfo.dropPos;
        CellLayout layout = getCellLayout(container, screen);

        boolean foundCellSpan = false;

        ShortcutInfo info = mModel.infoFromShortcutIntent(this, data, null);
        if (info == null) {
            return;
        }
        final View view = createShortcut(info);

        // First we check if we already know the exact location where we want to add this item.
        if (cellX >= 0 && cellY >= 0) {
            cellXY[0] = cellX;
            cellXY[1] = cellY;
            foundCellSpan = true;

            // If appropriate, either create a folder or add to an existing folder
            if (mWorkspace.createUserFolderIfNecessary(view, container, layout, cellXY,
                    true, null,null)) {
                return;
            }
            DragObject dragObject = new DragObject();
            dragObject.dragInfo = info;
            if (mWorkspace.addToExistingFolderIfNecessary(view, layout, cellXY, dragObject, true)) {
                return;
            }
        } else if (touchXY != null) {
            // when dragging and dropping, just find the closest free spot
            int[] result = layout.findNearestVacantArea(touchXY[0], touchXY[1], 1, 1, cellXY);
            foundCellSpan = (result != null);
        } else {
            foundCellSpan = layout.findCellForSpan(cellXY, 1, 1);
        }

        if (!foundCellSpan) {
            showOutOfSpaceMessage();
            return;
        }

        LauncherModel.addItemToDatabase(this, info, container, screen, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            mWorkspace.addInScreen(view, container, screen, cellXY[0], cellXY[1], 1, 1,
                    isWorkspaceLocked());
        }
    }

    Rect getDefaultPaddingForWidget(Context context, ComponentName component, Rect rect) {
        // Public api for widget padding was added in 4.0.3
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return AppWidgetHostView.getDefaultPaddingForWidget(context, component, rect);
        }

        Rect p = new Rect();

        Resources r = getResources();
        p.left = r.getDimensionPixelSize(R.dimen.app_widget_padding_left);
        p.right = r.getDimensionPixelSize(R.dimen.app_widget_padding_right);
        p.top = r.getDimensionPixelSize(R.dimen.app_widget_padding_top);
        p.bottom = r.getDimensionPixelSize(R.dimen.app_widget_padding_bottom);

        return p;
    }

    int[] getSpanForWidget(ComponentName component, int minWidth, int minHeight, int[] spanXY) {
        if (spanXY == null) {
            spanXY = new int[2];
        }

        Rect padding = getDefaultPaddingForWidget(this, component, null);

        // We want to account for the extra amount of padding that we are adding to the widget
        // to ensure that it gets the full amount of space that it has requested
        int requiredWidth = minWidth + padding.left + padding.right;
        int requiredHeight = minHeight + padding.top + padding.bottom;
        return CellLayout.rectToCell(getResources(), requiredWidth, requiredHeight, null);
    }

    int[] getSpanForWidget(AppWidgetProviderInfo info, int[] spanXY) {
        return getSpanForWidget(info.provider, info.minWidth, info.minHeight, spanXY);
    }

    int[] getMinResizeSpanForWidget(AppWidgetProviderInfo info, int[] spanXY) {
        return getSpanForWidget(info.provider, info.minResizeWidth, info.minResizeHeight, spanXY);
    }

    int[] getSpanForWidget(PendingAddWidgetInfo info, int[] spanXY) {
        return getSpanForWidget(info.componentName, info.minWidth, info.minHeight, spanXY);
    }

    /**
     * Add a widget to the workspace.
     *
     * @param appWidgetId The app widget id
     * @param cellInfo The position on screen where to create the widget.
     */
    private void completeAddAppWidget(final int appWidgetId, long container, int screen) {
        AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);

        // Calculate the grid spans needed to fit this widget
        CellLayout layout = getCellLayout(container, screen);

        int[] spanXY = getSpanForWidget(appWidgetInfo, null);

        // Try finding open space on Launcher screen
        // We have saved the position to which the widget was dragged-- this really only matters
        // if we are placing widgets on a "spring-loaded" screen
        int[] cellXY = mTmpAddItemCellCoordinates;
        int[] touchXY = mPendingAddInfo.dropPos;
        boolean foundCellSpan = false;
        if (mPendingAddInfo.cellX >= 0 && mPendingAddInfo.cellY >= 0) {
            cellXY[0] = mPendingAddInfo.cellX;
            cellXY[1] = mPendingAddInfo.cellY;
            foundCellSpan = true;
        } else if (touchXY != null) {
            // when dragging and dropping, just find the closest free spot
            int[] result = layout.findNearestVacantArea(
                    touchXY[0], touchXY[1], spanXY[0], spanXY[1], cellXY);
            foundCellSpan = (result != null);
        } else {
            foundCellSpan = layout.findCellForSpan(cellXY, spanXY[0], spanXY[1]);
        }

        if (!foundCellSpan) {
            if (appWidgetId != -1) {
                // Deleting an app widget ID is a void call but writes to disk before returning
                // to the caller...
                new Thread("deleteAppWidgetId") {
                    public void run() {
                        mAppWidgetHost.deleteAppWidgetId(appWidgetId);
                    }
                }.start();
            }
            showOutOfSpaceMessage();
            return;
        }

        // Build Launcher-specific widget info and save to database
        LauncherAppWidgetInfo launcherInfo = new LauncherAppWidgetInfo(appWidgetId);
        launcherInfo.spanX = spanXY[0];
        launcherInfo.spanY = spanXY[1];

        LauncherModel.addItemToDatabase(this, launcherInfo,
                container, screen, cellXY[0], cellXY[1], false);

        if (!mRestoring) {
            // Perform actual inflation because we're live
            launcherInfo.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);

            launcherInfo.hostView.setAppWidget(appWidgetId, appWidgetInfo);
            launcherInfo.hostView.setTag(launcherInfo);

            mWorkspace.addInScreen(launcherInfo.hostView, container, screen, cellXY[0], cellXY[1],
                    launcherInfo.spanX, launcherInfo.spanY, isWorkspaceLocked());

            addWidgetToAutoAdvanceIfNeeded(launcherInfo.hostView, appWidgetInfo);
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mUserPresent = false;
                mDragLayer.clearAllResizeFrames();
                updateRunning();

                // Reset AllApps to its initial state only if we are not in the middle of
                // processing a multi-step drop
                if (mAppsCustomizeTabHost != null && mPendingAddInfo.container == ItemInfo.NO_ID) {
                    mAppsCustomizeTabHost.reset();
                    showWorkspace(false);
                }
            } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
                mUserPresent = true;
                updateRunning();
            }
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Listen for broadcasts related to user-presence
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(mReceiver, filter);

        mAttached = true;
        mVisible = true;
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mVisible = false;
        mDragLayer.clearAllResizeFrames();

        if (mAttached) {
            unregisterReceiver(mReceiver);
            mAttached = false;
        }
        updateRunning();
    }

    public void onWindowVisibilityChanged(int visibility) {
        mVisible = visibility == View.VISIBLE;
        updateRunning();
    }

    private void sendAdvanceMessage(long delay) {
        mHandler.removeMessages(ADVANCE_MSG);
        Message msg = mHandler.obtainMessage(ADVANCE_MSG);
        mHandler.sendMessageDelayed(msg, delay);
        mAutoAdvanceSentTime = System.currentTimeMillis();
    }

    private void updateRunning() {
        boolean autoAdvanceRunning = mVisible && mUserPresent && !mWidgetsToAdvance.isEmpty();
        if (autoAdvanceRunning != mAutoAdvanceRunning) {
            mAutoAdvanceRunning = autoAdvanceRunning;
            if (autoAdvanceRunning) {
                long delay = mAutoAdvanceTimeLeft == -1 ? mAdvanceInterval : mAutoAdvanceTimeLeft;
                sendAdvanceMessage(delay);
            } else {
                if (!mWidgetsToAdvance.isEmpty()) {
                    mAutoAdvanceTimeLeft = Math.max(0, mAdvanceInterval -
                            (System.currentTimeMillis() - mAutoAdvanceSentTime));
                }
                mHandler.removeMessages(ADVANCE_MSG);
                mHandler.removeMessages(0); // Remove messages sent using postDelayed()
            }
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ADVANCE_MSG) {
                int i = 0;
                for (View key: mWidgetsToAdvance.keySet()) {
                    final View v = key.findViewById(mWidgetsToAdvance.get(key).autoAdvanceViewId);
                    final int delay = mAdvanceStagger * i;
                    if (v instanceof Advanceable) {
                       postDelayed(new Runnable() {
                           public void run() {
                               ((Advanceable) v).advance();
                           }
                       }, delay);
                    }
                    i++;
                }
                sendAdvanceMessage(mAdvanceInterval);
            }
        }
    };

    void addWidgetToAutoAdvanceIfNeeded(View hostView, AppWidgetProviderInfo appWidgetInfo) {
        if (appWidgetInfo == null || appWidgetInfo.autoAdvanceViewId == -1) return;
        View v = hostView.findViewById(appWidgetInfo.autoAdvanceViewId);
        if (v instanceof Advanceable) {
            mWidgetsToAdvance.put(hostView, appWidgetInfo);
            ((Advanceable) v).fyiWillBeAdvancedByHostKThx();
            updateRunning();
        }
    }

    void removeWidgetToAutoAdvance(View hostView) {
        if (mWidgetsToAdvance.containsKey(hostView)) {
            mWidgetsToAdvance.remove(hostView);
            updateRunning();
        }
    }

    public void removeAppWidget(LauncherAppWidgetInfo launcherInfo) {
        removeWidgetToAutoAdvance(launcherInfo.hostView);
        launcherInfo.hostView = null;
    }

    void showOutOfSpaceMessage() {
        Toast.makeText(this, getString(R.string.out_of_space), Toast.LENGTH_SHORT).show();
    }

    public LauncherAppWidgetHost getAppWidgetHost() {
        return mAppWidgetHost;
    }

    public LauncherModel getModel() {
        return mModel;
    }

    void closeSystemDialogs() {
        getWindow().closeAllPanels();

        /**
         * We should remove this code when we remove all the dialog code.
        try {
            dismissDialog(DIALOG_CREATE_SHORTCUT);
            // Unlock the workspace if the dialog was showing
        } catch (Exception e) {
            // An exception is thrown if the dialog is not visible, which is fine
        }

        try {
            dismissDialog(DIALOG_RENAME_FOLDER);
            // Unlock the workspace if the dialog was showing
        } catch (Exception e) {
            // An exception is thrown if the dialog is not visible, which is fine
        }
         */

        // Whatever we were doing is hereby canceled.
        mWaitingForResult = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // Close the menu
        if (Intent.ACTION_MAIN.equals(intent.getAction())) {
            // also will cancel mWaitingForResult.
            closeSystemDialogs();

            boolean alreadyOnHome = ((intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
                        != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

            Folder openFolder = mWorkspace.getOpenFolder();
            // In all these cases, only animate if we're already on home
            mWorkspace.exitWidgetResizeMode();
            if (alreadyOnHome && mState == State.WORKSPACE && !mWorkspace.isTouchActive() &&
                    openFolder == null) {
                mWorkspace.moveToDefaultScreen(true);
            }

            closeFolder();
            exitSpringLoadedDragMode();
            showWorkspace(alreadyOnHome);

            final View v = getWindow().peekDecorView();
            if (v != null && v.getWindowToken() != null) {
                InputMethodManager imm = (InputMethodManager)getSystemService(
                        INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }

            // Reset AllApps to its initial state
            if (!alreadyOnHome && mAppsCustomizeTabHost != null) {
                mAppsCustomizeTabHost.reset();
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // Do not call super here
        mSavedInstanceState = savedInstanceState;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(RUNTIME_STATE_CURRENT_SCREEN, mWorkspace.getCurrentPage());
        super.onSaveInstanceState(outState);

        outState.putInt(RUNTIME_STATE, mState.ordinal());
        // We close any open folder since it will not be re-opened, and we need to make sure
        // this state is reflected.
        closeFolder();

        if (mPendingAddInfo.container != ItemInfo.NO_ID && mPendingAddInfo.screen > -1 &&
                mWaitingForResult) {
            outState.putLong(RUNTIME_STATE_PENDING_ADD_CONTAINER, mPendingAddInfo.container);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_SCREEN, mPendingAddInfo.screen);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_X, mPendingAddInfo.cellX);
            outState.putInt(RUNTIME_STATE_PENDING_ADD_CELL_Y, mPendingAddInfo.cellY);
        }

        if (mFolderInfo != null && mWaitingForResult) {
            outState.putBoolean(RUNTIME_STATE_PENDING_FOLDER_RENAME, true);
            outState.putLong(RUNTIME_STATE_PENDING_FOLDER_RENAME_ID, mFolderInfo.id);
        }

        // Save the current AppsCustomize tab
        if (mAppsCustomizeTabHost != null) {
            String currentTabTag = mAppsCustomizeTabHost.getCurrentTabTag();
            if (currentTabTag != null) {
                outState.putString("apps_customize_currentTab", currentTabTag);
            }
            int currentIndex = mAppsCustomizeContent.getSaveInstanceStateIndex();
            outState.putInt("apps_customize_currentIndex", currentIndex);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Remove all pending runnables
        mHandler.removeMessages(ADVANCE_MSG);
        mHandler.removeMessages(0);
        mWorkspace.removeCallbacks(mBuildLayersRunnable);

        // Stop callbacks from LauncherModel
        LauncherApplication app = ((LauncherApplication) getApplication());
        mModel.stopLoader();
        app.setLauncher(null);

        try {
            mAppWidgetHost.stopListening();
        } catch (NullPointerException ex) {
            Log.w(TAG, "problem while stopping AppWidgetHost during Launcher destruction", ex);
        }
        mAppWidgetHost = null;

        mWidgetsToAdvance.clear();

        TextKeyListener.getInstance().release();


        unbindWorkspaceAndHotseatItems();

        getContentResolver().unregisterContentObserver(mWidgetObserver);
        unregisterReceiver(mCloseSystemDialogsReceiver);

        ((ViewGroup) mWorkspace.getParent()).removeAllViews();
        mWorkspace.removeAllViews();
        mWorkspace = null;
        mDragController = null;

        ValueAnimator.clearAllAnimations();
    }

    public DragController getDragController() {
        return mDragController;
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        if (requestCode >= 0) mWaitingForResult = true;
        super.startActivityForResult(intent, requestCode);
    }

    /**
     * Indicates that we want global search for this activity by setting the globalSearch
     * argument for {@link #startSearch} to true.
     */
    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery,
            Bundle appSearchData, boolean globalSearch) {

        showWorkspace(true);

        if (initialQuery == null) {
            // Use any text typed in the launcher as the initial query
            initialQuery = getTypedText();
        }
        if (appSearchData == null) {
            appSearchData = new Bundle();
            appSearchData.putString(Search.SOURCE, "launcher-search");
        }
        Rect sourceBounds = mSearchDropTargetBar.getSearchBarBounds();

        final SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchManager.startSearch(initialQuery, selectInitialQuery, getComponentName(),
            appSearchData, globalSearch, sourceBounds);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isWorkspaceLocked()) {
            return false;
        }

        super.onCreateOptionsMenu(menu);

        Intent manageApps = new Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS);
        manageApps.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        Intent preferences = new Intent().setClass(this, Preferences.class);
        preferences.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        Intent settings = new Intent(android.provider.Settings.ACTION_SETTINGS);
        settings.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        String helpUrl = getString(R.string.help_url);
        Intent help = new Intent(Intent.ACTION_VIEW, Uri.parse(helpUrl));
        help.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        menu.add(0, MENU_PREFERENCES, 0, R.string.menu_preferences)
            .setIcon(android.R.drawable.ic_menu_preferences)
            .setIntent(preferences)
            .setAlphabeticShortcut('O');
        menu.add(MENU_GROUP_WALLPAPER, MENU_WALLPAPER_SETTINGS, 0, R.string.menu_wallpaper)
            .setIcon(android.R.drawable.ic_menu_gallery)
            .setAlphabeticShortcut('W');
        menu.add(0, MENU_MANAGE_APPS, 0, R.string.menu_manage_apps)
            .setIcon(android.R.drawable.ic_menu_manage)
            .setIntent(manageApps)
            .setAlphabeticShortcut('M');
        menu.add(MENU_GROUP_MARKET, MENU_MARKET, 0, R.string.menu_market)
            .setAlphabeticShortcut('A')
            .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        onClickAppMarketButton(null);
                        return true;
                    }
            });
        menu.add(MENU_GROUP_SETTINGS, MENU_SYSTEM_SETTINGS, 0, R.string.menu_settings)
            .setIcon(android.R.drawable.ic_menu_preferences)
            .setIntent(settings)
            .setAlphabeticShortcut('P');
        if (!helpUrl.isEmpty()) {
            menu.add(0, MENU_HELP, 0, R.string.menu_help)
                .setIcon(android.R.drawable.ic_menu_help)
                .setIntent(help)
                .setAlphabeticShortcut('H');
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        if (mAppsCustomizeTabHost.isTransitioning()) {
            return false;
        }
        boolean allAppsVisible = (mAppsCustomizeTabHost.getVisibility() == View.VISIBLE);
        menu.setGroupVisible(MENU_GROUP_WALLPAPER, !allAppsVisible);
        menu.setGroupVisible(MENU_GROUP_MARKET, !allAppsVisible &&
                !ViewConfiguration.get(this).hasPermanentMenuKey() &&
                mAppMarketIntent != null);
        menu.setGroupVisible(MENU_GROUP_SETTINGS, !allAppsVisible);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_WALLPAPER_SETTINGS:
            startWallpaper();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null, true);
        // Use a custom animation for launching search
        overridePendingTransition(R.anim.fade_in_fast, R.anim.fade_out_fast);
        return true;
    }

    public boolean isWorkspaceLocked() {
        return mWorkspaceLoading || mWaitingForResult;
    }

    private void resetAddInfo() {
        mPendingAddInfo.container = ItemInfo.NO_ID;
        mPendingAddInfo.screen = -1;
        mPendingAddInfo.cellX = mPendingAddInfo.cellY = -1;
        mPendingAddInfo.spanX = mPendingAddInfo.spanY = -1;
        mPendingAddInfo.dropPos = null;
    }

    void addAppWidgetFromPick(Intent data) {
        // TODO: catch bad widget exception when sent
        int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
        // TODO: Is this log message meaningful?
        if (LOGD) Log.d(TAG, "dumping extras content=" + data.getExtras());
        addAppWidgetImpl(appWidgetId, null);
    }

    void addAppWidgetImpl(int appWidgetId, PendingAddWidgetInfo info) {
        AppWidgetProviderInfo appWidget = mAppWidgetManager.getAppWidgetInfo(appWidgetId);

        if (appWidget.configure != null) {
            // Launch over to configure widget, if needed
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            intent.setComponent(appWidget.configure);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            if (info != null) {
                if (info.mimeType != null && !info.mimeType.isEmpty()) {
                    intent.putExtra(
                            InstallWidgetReceiver.EXTRA_APPWIDGET_CONFIGURATION_DATA_MIME_TYPE,
                            info.mimeType);

                    final String mimeType = info.mimeType;
                    final ClipData clipData = (ClipData) info.configurationData;
                    final ClipDescription clipDesc = clipData.getDescription();
                    for (int i = 0; i < clipDesc.getMimeTypeCount(); ++i) {
                        if (clipDesc.getMimeType(i).equals(mimeType)) {
                            final ClipData.Item item = clipData.getItemAt(i);
                            final CharSequence stringData = item.getText();
                            final Uri uriData = item.getUri();
                            final Intent intentData = item.getIntent();
                            final String key =
                                InstallWidgetReceiver.EXTRA_APPWIDGET_CONFIGURATION_DATA;
                            if (uriData != null) {
                                intent.putExtra(key, uriData);
                            } else if (intentData != null) {
                                intent.putExtra(key, intentData);
                            } else if (stringData != null) {
                                intent.putExtra(key, stringData);
                            }
                            break;
                        }
                    }
                }
            }

            startActivityForResultSafely(intent, REQUEST_CREATE_APPWIDGET);
        } else {
            // Otherwise just add it
            completeAddAppWidget(appWidgetId, info.container, info.screen);

            // Exit spring loaded mode if necessary after adding the widget
            exitSpringLoadedDragModeDelayed(true, false);
        }
    }

    /**
     * Process a shortcut drop.
     *
     * @param componentName The name of the component
     * @param screen The screen where it should be added
     * @param cell The cell it should be added to, optional
     * @param position The location on the screen where it was dropped, optional
     */
    void processShortcutFromDrop(ComponentName componentName, long container, int screen,
            int[] cell, int[] loc) {
        resetAddInfo();
        mPendingAddInfo.container = container;
        mPendingAddInfo.screen = screen;
        mPendingAddInfo.dropPos = loc;

        if (cell != null) {
            mPendingAddInfo.cellX = cell[0];
            mPendingAddInfo.cellY = cell[1];
        }

        Intent createShortcutIntent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
        createShortcutIntent.setComponent(componentName);
        processShortcut(createShortcutIntent);
    }

    /**
     * Process a widget drop.
     *
     * @param info The PendingAppWidgetInfo of the widget being added.
     * @param screen The screen where it should be added
     * @param cell The cell it should be added to, optional
     * @param position The location on the screen where it was dropped, optional
     */
    void addAppWidgetFromDrop(PendingAddWidgetInfo info, long container, int screen,
            int[] cell, int[] loc) {
        resetAddInfo();
        mPendingAddInfo.container = info.container = container;
        mPendingAddInfo.screen = info.screen = screen;
        mPendingAddInfo.dropPos = loc;
        if (cell != null) {
            mPendingAddInfo.cellX = cell[0];
            mPendingAddInfo.cellY = cell[1];
        }

        int appWidgetId = getAppWidgetHost().allocateAppWidgetId();
        AppWidgetManager.getInstance(this).bindAppWidgetId(appWidgetId, info.componentName);
        addAppWidgetImpl(appWidgetId, info);
    }

    void processShortcut(Intent intent) {
        // Handle case where user selected "Applications"
        String applicationName = getResources().getString(R.string.group_applications);
        String shortcutName = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (applicationName != null && applicationName.equals(shortcutName)) {
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
            pickIntent.putExtra(Intent.EXTRA_INTENT, mainIntent);
            pickIntent.putExtra(Intent.EXTRA_TITLE, getText(R.string.title_select_application));
            startActivityForResultSafely(pickIntent, REQUEST_PICK_APPLICATION);
        } else {
            startActivityForResultSafely(intent, REQUEST_CREATE_SHORTCUT);
        }
    }

    void processWallpaper(Intent intent) {
        startActivityForResult(intent, REQUEST_PICK_WALLPAPER);
    }

    FolderIcon addFolder(CellLayout layout, long container, final int screen, int cellX,
            int cellY) {
        final FolderInfo folderInfo = new FolderInfo();
        folderInfo.title = getText(R.string.folder_name);

        // Update the model
        LauncherModel.addItemToDatabase(Launcher.this, folderInfo, container, screen, cellX, cellY,
                false);
        sFolders.put(folderInfo.id, folderInfo);

        // Create the view
        FolderIcon newFolder =
            FolderIcon.fromXml(R.layout.folder_icon, this, layout, folderInfo, mIconCache);
        if (mHideIconLabels) {
            newFolder.setTextVisible(false);
        }
        mWorkspace.addInScreen(newFolder, container, screen, cellX, cellY, 1, 1,
                isWorkspaceLocked());
        return newFolder;
    }

    void removeFolder(FolderInfo folder) {
        sFolders.remove(folder.id);
    }

    private void startWallpaper() {
        showWorkspace(true);
        final Intent pickWallpaper = new Intent(Intent.ACTION_SET_WALLPAPER);
        Intent chooser = Intent.createChooser(pickWallpaper,
                getText(R.string.chooser_wallpaper));
        // NOTE: Adds a configure option to the chooser if the wallpaper supports it
        //       Removed in Eclair MR1
//        WallpaperManager wm = (WallpaperManager)
//                getSystemService(Context.WALLPAPER_SERVICE);
//        WallpaperInfo wi = wm.getWallpaperInfo();
//        if (wi != null && wi.getSettingsActivity() != null) {
//            LabeledIntent li = new LabeledIntent(getPackageName(),
//                    R.string.configure_wallpaper, 0);
//            li.setClassName(wi.getPackageName(), wi.getSettingsActivity());
//            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { li });
//        }
        startActivityForResult(chooser, REQUEST_PICK_WALLPAPER);
    }

    /**
     * Registers various content observers. The current implementation registers
     * only a favorites observer to keep track of the favorites applications.
     */
    private void registerContentObservers() {
        ContentResolver resolver = getContentResolver();
        resolver.registerContentObserver(LauncherProvider.CONTENT_APPWIDGET_RESET_URI,
                true, mWidgetObserver);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    if (SystemProperties.getInt("debug.launcher2.dumpstate", 0) != 0) {
                        dumpState();
                        return true;
                    }
                    break;
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (event.getKeyCode()) {
                case KeyEvent.KEYCODE_HOME:
                    return true;
            }
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (mState == State.APPS_CUSTOMIZE) {
            showWorkspace(true);
        } else if (mWorkspace.getOpenFolder() != null) {
            Folder openFolder = mWorkspace.getOpenFolder();
            if (openFolder.isEditingName()) {
                openFolder.dismissEditingName();
            } else {
                closeFolder();
            }
        } else {
            mWorkspace.exitWidgetResizeMode();

            // Back button is a no-op here, but give at least some feedback for the button press
            mWorkspace.showOutlinesTemporarily();
        }
    }

    /**
     * Re-listen when widgets are reset.
     */
    private void onAppWidgetReset() {
        if (mAppWidgetHost != null) {
            mAppWidgetHost.startListening();
        }
    }

    /**
     * Go through the and disconnect any of the callbacks in the drawables and the views or we
     * leak the previous Home screen on orientation change.
     */
    private void unbindWorkspaceAndHotseatItems() {
        if (mModel != null) {
            mModel.unbindWorkspaceItems();
        }
    }

    /**
     * Launches the intent referred by the clicked shortcut.
     *
     * @param v The view representing the clicked shortcut.
     */
    public void onClick(View v) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
        if (v.getWindowToken() == null) {
            return;
        }

        if (mWorkspace.isSwitchingState()) {
            return;
        }

        Object tag = v.getTag();
        if (tag instanceof ShortcutInfo) {
            // Open shortcut
            final Intent intent = ((ShortcutInfo) tag).intent;
            int[] pos = new int[2];
            v.getLocationOnScreen(pos);
            intent.setSourceBounds(new Rect(pos[0], pos[1],
                    pos[0] + v.getWidth(), pos[1] + v.getHeight()));
            boolean success = startActivitySafely(intent, tag);

            if (success && v instanceof BubbleTextView) {
                mWaitingForResume = (BubbleTextView) v;
                mWaitingForResume.setStayPressed(true);
            }
        } else if (tag instanceof FolderInfo) {
            if (v instanceof FolderIcon) {
                FolderIcon fi = (FolderIcon) v;
                handleFolderClick(fi);
            }
        }
    }

    public boolean onTouch(View v, MotionEvent event) {
        // this is an intercepted event being forwarded from mWorkspace;
        // clicking anywhere on the workspace causes the customization drawer to slide down
        showWorkspace(true);
        return false;
    }

    /**
     * Event handler for the search button
     *
     * @param v The view that was clicked.
     */
    public void onClickSearchButton(View v) {
        if (v != null) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        onSearchRequested();
    }

    /**
     * Event handler for the voice button
     *
     * @param v The view that was clicked.
     */
    public void onClickVoiceButton(View v) {
        if (v != null) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

        Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }

    /**
     * Event handler for the "grid" button that appears on the home screen, which
     * enters all apps mode.
     *
     * @param v The view that was clicked.
     */
    public void onClickAllAppsButton(View v) {
        if (v != null) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        showAllApps(true);
    }

    public void onTouchDownAllAppsButton(View v) {
        // Provide the same haptic feedback that the system offers for virtual keys.
        //v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

     /**
     * Event handler for the market button
     *
     * @param v The view that was clicked.
     */
    public void onClickAppMarketButton(View v) {
        if (mAppMarketIntent != null) {
            if (v != null) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            startActivitySafely(mAppMarketIntent, "app market");
        }
    }

     /**
     * Event handler for the hotseat button
     *
     * @param v The view that was clicked.
     */
    public void onClickHotseatButton(ImageView v) {
        if (v != null) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (mHotseat.getVisibility() == View.VISIBLE) {
            hideHotseat(true);
            hideDockDivider(true);
            mShowHotseat = false;
        } else {
            mShowHotseat = true;
            showHotseat(true);
            showDockDivider(true);
        }

        if (mActionButtonOne == 2) {
            v = mCustomButtonOne;
        }
        if (mActionButtonTwo == 2) {
            v = mCustomButtonTwo;
        }
        if (mActionButtonThree == 2) {
            v = mCustomButtonThree;
        }
        if (mActionButtonFour == 2) {
            v = mCustomButtonFour;
        }
        if (mActionButtonFive == 2) {
            v = mCustomButtonFive;
        }
        if (mActionButtonSix == 2) {
            v = mCustomButtonSix;
        }
        if (mActionButtonSeven == 2) {
            v = mCustomButtonSeven;
        }
        if (mActionButtonEight == 2) {
            v = mCustomButtonEight;
        }

        if (mAllAppsTop && v != null) {
            v.setImageResource(mShowHotseat ? R.drawable.hotseat_btn :
                R.drawable.hotseat_close_btn);
        } else if (v != null) {
            v.setImageResource(mShowHotseat ? R.drawable.hotseat_close_btn :
                R.drawable.hotseat_btn);
        }

        SharedPreferences prefs =
                getSharedPreferences(PreferencesProvider.PREFERENCES_KEY, Context.MODE_PRIVATE);
        prefs.edit().putBoolean("ui_homescreen_general_show_hotseat", mShowHotseat).commit();
    }

    public void onClickOverflowMenuButton(View v) {
        final PopupMenu popupMenu = new PopupMenu(this, v);
        final Menu menu = popupMenu.getMenu();
        onCreateOptionsMenu(menu);
        onPrepareOptionsMenu(menu);
        popupMenu.show();
    }

    /**
     * Event handler for the custom button
     *
     * @param v The view that was clicked.
     */
    public void onClickCustomButton(View v) {
        if (v != null) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        SharedPreferences prefs =
                getSharedPreferences(PreferencesProvider.PREFERENCES_KEY, Context.MODE_PRIVATE);
        String shortcutUri = new String();
        if (v.getId() == R.id.workspace_custom_button_one) {
            if (mActionButtonOne == 1) {
                onClickAllAppsButton(v);
            } else if (mActionButtonOne == 2) {
                onClickHotseatButton((ImageView) v);
            } else if (mActionButtonOne == 3) {
                shortcutUri = prefs.getString("custom_application_one", "");
            } else if (mActionButtonOne == 4) {
                onClickSearchButton(v);
            } else if (mActionButtonOne == 5) {
                onClickVoiceButton(v);
            }
        } else if (v.getId() == R.id.workspace_custom_button_two) {
            if (mActionButtonTwo == 1) {
                onClickAllAppsButton(v);
            } else if (mActionButtonTwo == 2) {
                onClickHotseatButton((ImageView) v);
            } else if (mActionButtonTwo == 3) {
                shortcutUri = prefs.getString("custom_application_two", "");
            } else if (mActionButtonTwo == 4) {
                onClickSearchButton(v);
            } else if (mActionButtonTwo == 5) {
                onClickVoiceButton(v);
            }
        } else if (v.getId() == R.id.workspace_custom_button_three) {
            if (mActionButtonThree == 1) {
                onClickAllAppsButton(v);
            } else if (mActionButtonThree == 2) {
                onClickHotseatButton((ImageView) v);
            } else if (mActionButtonThree == 3) {
                shortcutUri = prefs.getString("custom_application_three", "");
            } else if (mActionButtonThree == 4) {
                onClickSearchButton(v);
            } else if (mActionButtonThree == 5) {
                onClickVoiceButton(v);
            }
        } else if (v.getId() == R.id.workspace_custom_button_four) {
            if (mActionButtonFour == 1) {
                onClickAllAppsButton(v);
            } else if (mActionButtonFour == 2) {
                onClickHotseatButton((ImageView) v);
            } else if (mActionButtonFour == 3) {
                shortcutUri = prefs.getString("custom_application_four", "");
            } else if (mActionButtonFour == 4) {
                onClickSearchButton(v);
            } else if (mActionButtonFour == 5) {
                onClickVoiceButton(v);
            }
        } else if (v.getId() == R.id.workspace_custom_button_five) {
            if (mActionButtonFive == 1) {
                onClickAllAppsButton(v);
            } else if (mActionButtonFive == 2) {
                onClickHotseatButton((ImageView) v);
            } else if (mActionButtonFive == 3) {
                shortcutUri = prefs.getString("custom_application_five", "");
            } else if (mActionButtonFive == 4) {
                onClickSearchButton(v);
            } else if (mActionButtonFive == 5) {
                onClickVoiceButton(v);
            }
        } else if (v.getId() == R.id.workspace_custom_button_six) {
            if (mActionButtonSix == 1) {
                onClickAllAppsButton(v);
            } else if (mActionButtonSix == 2) {
                onClickHotseatButton((ImageView) v);
            } else if (mActionButtonSix == 3) {
                shortcutUri = prefs.getString("custom_application_six", "");
            } else if (mActionButtonSix == 4) {
                onClickSearchButton(v);
            } else if (mActionButtonSix == 5) {
                onClickVoiceButton(v);
            }
        } else if (v.getId() == R.id.workspace_custom_button_seven) {
            if (mActionButtonSeven == 1) {
                onClickAllAppsButton(v);
            } else if (mActionButtonSeven == 2) {
                onClickHotseatButton((ImageView) v);
            } else if (mActionButtonSeven == 3) {
                shortcutUri = prefs.getString("custom_application_seven", "");
            } else if (mActionButtonSeven == 4) {
                onClickSearchButton(v);
            } else if (mActionButtonSeven == 5) {
                onClickVoiceButton(v);
            }
        } else if (v.getId() == R.id.workspace_custom_button_eight) {
            if (mActionButtonEight == 1) {
                onClickAllAppsButton(v);
            } else if (mActionButtonEight == 2) {
                onClickHotseatButton((ImageView) v);
            } else if (mActionButtonEight == 3) {
                shortcutUri = prefs.getString("custom_application_eight", "");
            } else if (mActionButtonEight == 4) {
                onClickSearchButton(v);
            } else if (mActionButtonEight == 5) {
                onClickVoiceButton(v);
            }
        }
        try {
            Intent launchIntent = Intent.parseUri(shortcutUri, 0);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        } catch (Exception e) {
        }
    }

    public void onLongClickAppsTab(View v) {
        final PopupMenu popupMenu = new PopupMenu(this, v);
        final Menu menu = popupMenu.getMenu();
        dismissAllAppsSortCling(null);
        popupMenu.inflate(R.menu.apps_tab);
        AppsCustomizeView.SortMode sortMode = mAppsCustomizeContent.getSortMode();
        switch (sortMode) {
            case Title:
                menu.findItem(R.id.apps_sort_title).setChecked(true);
                break;
            case InstallDate:
                menu.findItem(R.id.apps_sort_install_date).setChecked(true);
                break;
        }

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    switch (item.getItemId()) {
                        case R.id.apps_sort_title:
                            mAppsCustomizeContent.setSortMode(AppsCustomizeView.SortMode.Title);
                            break;
                        case R.id.apps_sort_install_date:
                            mAppsCustomizeContent.setSortMode(
                                    AppsCustomizeView.SortMode.InstallDate);
                            break;
                    }
                    return true;
                }
        });

        popupMenu.show();
    }

    void startApplicationDetailsActivity(ComponentName componentName) {
        String packageName = componentName.getPackageName();
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        startActivity(intent);
    }

    void startApplicationUninstallActivity(ApplicationInfo appInfo) {
        if ((appInfo.flags & ApplicationInfo.DOWNLOADED_FLAG) == 0) {
            // System applications cannot be installed. For now, show a toast explaining that.
            // We may give them the option of disabling apps this way.
            int messageId = R.string.uninstall_system_app_text;
            Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
        } else {
            String packageName = appInfo.componentName.getPackageName();
            String className = appInfo.componentName.getClassName();
            Intent intent = new Intent(
                    Intent.ACTION_DELETE, Uri.fromParts("package", packageName, className));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }
    }

    void startShortcutUninstallActivity(ShortcutInfo shortcutInfo) {
        PackageManager pm = getPackageManager();
        ResolveInfo resolveInfo = pm.resolveActivity(shortcutInfo.intent, 0);
        if ((resolveInfo.activityInfo.applicationInfo.flags &
                android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
            // System applications cannot be installed. For now, show a toast explaining that.
            // We may give them the option of disabling apps this way.
            int messageId = R.string.uninstall_system_app_text;
            Toast.makeText(this, messageId, Toast.LENGTH_SHORT).show();
        } else {
            String packageName = shortcutInfo.intent.getComponent().getPackageName();
            String className = shortcutInfo.intent.getComponent().getClassName();
            Intent intent = new Intent(
                    Intent.ACTION_DELETE, Uri.fromParts("package", packageName, className));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }
    }

    boolean startActivitySafely(Intent intent, Object tag) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Unable to launch. tag=" + tag + " intent=" + intent, e);
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity. "
                    + "tag="+ tag + " intent=" + intent, e);
        }
        return false;
    }

    void startActivityForResultSafely(Intent intent, int requestCode) {
        try {
            startActivityForResult(intent, requestCode);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        } catch (SecurityException e) {
            Toast.makeText(this, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Launcher does not have the permission to launch " + intent +
                    ". Make sure to create a MAIN intent-filter for the corresponding activity " +
                    "or use the exported attribute for this activity.", e);
        }
    }

    private void handleFolderClick(FolderIcon folderIcon) {
        final FolderInfo info = folderIcon.mInfo;
        Folder openFolder = mWorkspace.getFolderForTag(info);

        // If the folder info reports that the associated folder is open, then verify that
        // it is actually opened. There have been a few instances where this gets out of sync.
        if (info.opened && openFolder == null) {
            Log.d(TAG, "Folder info marked as open, but associated folder is not open. Screen: "
                    + info.screen + " (" + info.cellX + ", " + info.cellY + ")");
            info.opened = false;
        }

        if (!info.opened) {
            // Close any open folder
            closeFolder();
            // Open the requested folder
            openFolder(folderIcon);
        } else {
            // Find the open folder...
            int folderScreen;
            if (openFolder != null) {
                folderScreen = mWorkspace.getPageForView(openFolder);
                // .. and close it
                closeFolder(openFolder);
                if (folderScreen != mWorkspace.getCurrentPage()) {
                    // Close any folder open on the current screen
                    closeFolder();
                    // Pull the folder onto this screen
                    openFolder(folderIcon);
                }
            }
        }
    }

    private void growAndFadeOutFolderIcon(FolderIcon fi) {
        if (fi == null) return;
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 0);
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.5f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.5f);

        FolderInfo info = (FolderInfo) fi.getTag();
        if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT ||
                info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT_TWO) {
            CellLayout cl = (CellLayout) fi.getParent().getParent();
            CellLayout.LayoutParams lp = (CellLayout.LayoutParams) fi.getLayoutParams();
            cl.setFolderLeaveBehindCell(lp.cellX, lp.cellY);
        }

        ObjectAnimator oa = ObjectAnimator.ofPropertyValuesHolder(fi, alpha, scaleX, scaleY);
        oa.setDuration(getResources().getInteger(R.integer.config_folderAnimDuration));
        oa.start();
    }

    private void shrinkAndFadeInFolderIcon(FolderIcon fi) {
        if (fi == null) return;
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat("alpha", 1.0f);
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.0f);

        FolderInfo info = (FolderInfo) fi.getTag();
        CellLayout cl = null;
        if (info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT ||
                info.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT_TWO) {
            cl = (CellLayout) fi.getParent().getParent();
        }

        final CellLayout layout = cl;
        ObjectAnimator oa = ObjectAnimator.ofPropertyValuesHolder(fi, alpha, scaleX, scaleY);
        oa.setDuration(getResources().getInteger(R.integer.config_folderAnimDuration));
        oa.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (layout != null) {
                    layout.clearFolderLeaveBehind();
                }
            }
        });
        oa.start();
    }

    /**
     * Opens the user folder described by the specified tag. The opening of the folder
     * is animated relative to the specified View. If the View is null, no animation
     * is played.
     *
     * @param folderInfo The FolderInfo describing the folder to open.
     */
    public void openFolder(FolderIcon folderIcon) {
        Folder folder = folderIcon.mFolder;
        FolderInfo info = folder.mInfo;

        growAndFadeOutFolderIcon(folderIcon);
        info.opened = true;

        // Just verify that the folder hasn't already been added to the DragLayer.
        // There was a one-off crash where the folder had a parent already.
        if (folder.getParent() == null) {
            mDragLayer.addView(folder);
            mDragController.addDropTarget((DropTarget) folder);
        } else {
            Log.w(TAG, "Opening folder (" + folder + ") which already has a parent (" +
                    folder.getParent() + ").");
        }
        folder.animateOpen();
    }

    public void closeFolder() {
        Folder folder = mWorkspace.getOpenFolder();
        if (folder != null) {
            if (folder.isEditingName()) {
                folder.dismissEditingName();
            }
            closeFolder(folder);

            // Dismiss the folder cling
            dismissFolderCling(null);
        }
    }

    void closeFolder(Folder folder) {
        folder.getInfo().opened = false;

        ViewGroup parent = (ViewGroup) folder.getParent().getParent();
        if (parent != null) {
            FolderIcon fi = (FolderIcon) mWorkspace.getViewForTag(folder.mInfo);
            shrinkAndFadeInFolderIcon(fi);
        }
        folder.animateClosed();
    }

    public boolean onLongClick(View v) {
        if (v == mCustomButtonOne) {
            performGesture(mAppBarLongClick, 13);
            return true;
        } else if (v == mCustomButtonTwo) {
            performGesture(mAppBarLongClick, 13);
            return true;
        } else if (v == mCustomButtonThree) {
            performGesture(mAppBarLongClick, 13);
            return true;
        } else if (v == mCustomButtonFour) {
            performGesture(mAppBarLongClick, 13);
            return true;
        } else if (v == mCustomButtonFive) {
            performGesture(mAppBarLongClick, 13);
            return true;
        } else if (v == mCustomButtonSix) {
            performGesture(mAppBarLongClick, 13);
            return true;
        } else if (v == mCustomButtonSeven) {
            performGesture(mAppBarLongClick, 13);
            return true;
        } else if (v == mCustomButtonEight) {
            performGesture(mAppBarLongClick, 13);
            return true;
        }

        if (mState != State.WORKSPACE) {
            return false;
        }

        if (isWorkspaceLocked()) {
            return false;
        }

        if (!(v instanceof CellLayout)) {
            v = (View) v.getParent().getParent();
        }

        resetAddInfo();
        CellLayout.CellInfo longClickCellInfo = (CellLayout.CellInfo) v.getTag();
        // This happens when long clicking an item with the dpad/trackball
        if (longClickCellInfo == null) {
            return true;
        }

        // The hotseat touch handling does not go through Workspace, and we always allow long press
        // on hotseat items.
        final View itemUnderLongClick = longClickCellInfo.cell;
        boolean allowLongPress = isHotseatLayout(v) || isHotseatTwoLayout(v) ||
                mWorkspace.allowLongPress();
        if (allowLongPress && !mDragController.isDragging()) {
            if (itemUnderLongClick == null) {
                // User long pressed on empty space
                mWorkspace.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS,
                        HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
                startWallpaper();
            } else {
                if (!(itemUnderLongClick instanceof Folder)) {
                    // User long pressed on an item
                    mWorkspace.startDrag(longClickCellInfo);
                }
            }
        }
        return true;
    }

    boolean isHotseatLayout(View layout) {
        return mHotseat != null && layout != null &&
                (layout instanceof CellLayout) && (layout == mHotseat.getLayout());
    }

    boolean isHotseatTwoLayout(View layout) {
        return mHotseatTwo != null && layout != null &&
                (layout instanceof CellLayout) && (layout == mHotseatTwo.getLayout());
    }

    Hotseat getHotseat() {
        return mHotseat;
    }

    Hotseat getHotseatTwo() {
        return mHotseatTwo;
    }

    SearchDropTargetBar getSearchBar() {
        return mSearchDropTargetBar;
    }

    /**
     * Returns the CellLayout of the specified container at the specified screen.
     */
    CellLayout getCellLayout(long container, int screen) {
        if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
            if (mHotseat != null) {
                return mHotseat.getLayout();
            } else {
                return null;
            }
        } else if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT_TWO) {
            if (mHotseatTwo != null) {
                return mHotseatTwo.getLayout();
            } else {
                return null;
            }
        } else {
            return (CellLayout) mWorkspace.getChildAt(screen);
        }
    }

    Workspace getWorkspace() {
        return mWorkspace;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_CREATE_SHORTCUT:
                return new CreateShortcut().createDialog();
            case DIALOG_RENAME_FOLDER:
                return new RenameFolder().createDialog();
        }

        return super.onCreateDialog(id);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
            case DIALOG_CREATE_SHORTCUT:
                break;
            case DIALOG_RENAME_FOLDER:
                if (mFolderInfo != null) {
                    EditText input = (EditText) dialog.findViewById(R.id.folder_name);
                    final CharSequence text = mFolderInfo.title;
                    input.setText(text);
                    input.setSelection(0, text.length());
                }
                break;
        }
    }

    void showRenameDialog(FolderInfo info) {
        mFolderInfo = info;
        mWaitingForResult = true;
        showDialog(DIALOG_RENAME_FOLDER);
    }

    private void showAddDialog() {
        resetAddInfo();
        mPendingAddInfo.container = LauncherSettings.Favorites.CONTAINER_DESKTOP;
        mPendingAddInfo.screen = mWorkspace.getCurrentPage();
        mWaitingForResult = true;
        showDialog(DIALOG_CREATE_SHORTCUT);
    }

    private class RenameFolder {
        private EditText mInput;

        Dialog createDialog() {
            final View layout = View.inflate(Launcher.this, R.layout.rename_folder, null);
            mInput = (EditText) layout.findViewById(R.id.folder_name);

            AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this);
            builder.setIcon(0);
            builder.setTitle(getString(R.string.rename_folder_title));
            builder.setCancelable(true);
            builder.setOnCancelListener(new Dialog.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    cleanup();
                }
            });
            builder.setNegativeButton(getString(R.string.cancel_action),
                new Dialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        cleanup();
                    }
                }
            );
            builder.setPositiveButton(getString(R.string.rename_action),
                new Dialog.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        changeFolderName();
                    }
                }
            );
            builder.setView(layout);

            final AlertDialog dialog = builder.create();
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                public void onShow(DialogInterface dialog) {
                    mWaitingForResult = true;
                    mInput.requestFocus();
                    InputMethodManager inputManager = (InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
                    inputManager.showSoftInput(mInput, 0);
                }
            });

            return dialog;
        }

        private void changeFolderName() {
            final String name = mInput.getText().toString();
            if (!TextUtils.isEmpty(name)) {
                // Make sure we have the right folder info
                mFolderInfo = sFolders.get(mFolderInfo.id);
                mFolderInfo.title = name;
                LauncherModel.updateItemInDatabase(Launcher.this, mFolderInfo);

                if (mWorkspaceLoading) {
                    lockAllApps();
                    mModel.startLoader(Launcher.this, false);
                } else {
                    final FolderIcon folderIcon = (FolderIcon)
                            mWorkspace.getViewForTag(mFolderInfo);
                    if (folderIcon != null) {
                        // TODO: At some point we'll probably want some version of setting
                        // the text for a folder icon.
                        //folderIcon.setText(name);
                        getWorkspace().requestLayout();
                    } else {
                        lockAllApps();
                        mWorkspaceLoading = true;
                        mModel.startLoader(Launcher.this, false);
                    }
                }
            }
            cleanup();
        }

        private void cleanup() {
            dismissDialog(DIALOG_RENAME_FOLDER);
            mWaitingForResult = false;
            mFolderInfo = null;
        }
    }

    // Now a part of LauncherModel.Callbacks. Used to reorder loading steps.
    public boolean isAllAppsVisible() {
        return (mState == State.APPS_CUSTOMIZE);
    }

    /**
     * Helper method for the cameraZoomIn/cameraZoomOut animations
     * @param view The view being animated
     * @param state The state that we are moving in or out of (eg. APPS_CUSTOMIZE)
     * @param scaleFactor The scale factor used for the zoom
     */
    private void setPivotsForZoom(View view, float scaleFactor) {
        view.setPivotX(view.getWidth() / 2.0f);
        view.setPivotY(view.getHeight() / 2.0f);
    }

    void updateWallpaperVisibility(boolean visible) {
        int wpflags = visible ? WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER : 0;
        int curflags = getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER;
        if (wpflags != curflags) {
            getWindow().setFlags(wpflags, WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        }
    }

    /**
     * Things to test when changing the following seven functions.
     *   - Home from workspace
     *          - from center screen
     *          - from other screens
     *   - Home from all apps
     *          - from center screen
     *          - from other screens
     *   - Back from all apps
     *          - from center screen
     *          - from other screens
     *   - Launch app from workspace and quit
     *          - with back
     *          - with home
     *   - Launch app from all apps and quit
     *          - with back
     *          - with home
     *   - Go to a screen that's not the default, then all
     *     apps, and launch and app, and go back
     *          - with back
     *          -with home
     *   - On workspace, long press power and go back
     *          - with back
     *          - with home
     *   - On all apps, long press power and go back
     *          - with back
     *          - with home
     *   - On workspace, power off
     *   - On all apps, power off
     *   - Launch an app and turn off the screen while in that app
     *          - Go back with home key
     *          - Go back with back key  TODO: make this not go to workspace
     *          - From all apps
     *          - From workspace
     *   - Enter and exit car mode (becuase it causes an extra configuration changed)
     *          - From all apps
     *          - From the center workspace
     *          - From another workspace
     */

    /**
     * Zoom the camera out from the workspace to reveal 'toView'.
     * Assumes that the view to show is anchored at either the very top or very bottom
     * of the screen.
     */
    private void showAppsCustomizeHelper(boolean animated, final boolean springLoaded) {
        if (mStateAnimation != null) {
            mStateAnimation.cancel();
            mStateAnimation = null;
        }
        final Resources res = getResources();
        final Launcher instance = this;

        final int duration = res.getInteger(R.integer.config_appsCustomizeZoomInTime);
        final int fadeDuration = res.getInteger(R.integer.config_appsCustomizeFadeInTime);
        final float scale = (float) res.getInteger(R.integer.config_appsCustomizeZoomScaleFactor);
        final View toView = mAppsCustomizeTabHost;
        final int startDelay =
                res.getInteger(R.integer.config_workspaceAppsCustomizeAnimationStagger);

        setPivotsForZoom(toView, scale);

        Workspace.TransitionEffect transitionEffect = mWorkspace.getTransitionEffect();
        final boolean animateWorkspace = transitionEffect != Workspace.TransitionEffect.RotateUp &&
                transitionEffect != Workspace.TransitionEffect.RotateDown;

        // Shrink workspaces away if going to AppsCustomize from workspace
        if (animateWorkspace) {
            mWorkspace.changeState(Workspace.State.SMALL, animated);
        }

        if (animated) {
            final ValueAnimator scaleAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(duration);
            scaleAnim.setInterpolator(new Workspace.ZoomOutInterpolator());
            scaleAnim.addUpdateListener(new LauncherAnimatorUpdateListener() {
                public void onAnimationUpdate(float a, float b) {
                    toView.setScaleX(a * scale + b * 1f);
                    toView.setScaleY(a * scale + b * 1f);
                }
            });

            toView.setVisibility(View.VISIBLE);
            toView.setAlpha(0f);
            ValueAnimator alphaAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(fadeDuration);
            alphaAnim.setInterpolator(new DecelerateInterpolator(1.5f));
            alphaAnim.addUpdateListener(new LauncherAnimatorUpdateListener() {
                public void onAnimationUpdate(float a, float b) {
                    // don't need to invalidate because we do so above
                    toView.setAlpha(a * 0f + b * 1f);
                }
            });
            alphaAnim.setStartDelay(startDelay);
            alphaAnim.start();

            scaleAnim.addListener(new AnimatorListenerAdapter() {
                boolean animationCancelled = false;

                @Override
                public void onAnimationStart(Animator animation) {
                    if (!mShowWallpaper) updateWallpaperVisibility(true);
                    mWorkspace.setVisibility(View.GONE);
                    hideHotseat(true);
                    hideAllAppsBar(true);
                    hideSearchBar(true);
                    hideDockDivider(true);
                    // Prepare the position
                    toView.setTranslationX(0.0f);
                    toView.setTranslationY(0.0f);
                    toView.setVisibility(View.VISIBLE);
                    toView.bringToFront();
                }
                @Override
                public void onAnimationEnd(Animator animation) {
                    // If we don't set the final scale values here, if this animation is cancelled
                    // it will have the wrong scale value and subsequent cameraPan animations will
                    // not fix that
                    toView.setScaleX(1.0f);
                    toView.setScaleY(1.0f);
                    if (toView instanceof LauncherTransitionable) {
                        ((LauncherTransitionable) toView).onLauncherTransitionEnd(instance,
                                scaleAnim, false);
                    }

                    if (!springLoaded) {
                        // Hide the workspace scrollbar
                        mWorkspace.hideScrollingIndicator(true);
                    }
                    if (!animationCancelled && !mShowWallpaper) {
                        updateWallpaperVisibility(false);
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    animationCancelled = true;
                }
            });

            // toView should appear right at the end of the workspace shrink animation
            mStateAnimation = new AnimatorSet();
            mStateAnimation.play(scaleAnim).after(startDelay);

            boolean delayAnim = false;
            if (toView instanceof LauncherTransitionable) {
                LauncherTransitionable lt = (LauncherTransitionable) toView;
                delayAnim = lt.onLauncherTransitionStart(instance, mStateAnimation, false);
            }
            // if the anim is delayed, the LauncherTransitionable is responsible for starting it
            if (!delayAnim) {
                // TODO: q-- what if this anim is cancelled before being started? or started after
                // being cancelled?
                mStateAnimation.start();
            }
        } else {
            mWorkspace.setVisibility(View.GONE);
            hideDockDivider(animated);
            hideAllAppsBar(animated);
            hideHotseat(animated);
            hideSearchBar(animated);
            toView.setTranslationX(0.0f);
            toView.setTranslationY(0.0f);
            toView.setScaleX(1.0f);
            toView.setScaleY(1.0f);
            toView.setVisibility(View.VISIBLE);
            toView.bringToFront();
            if (toView instanceof LauncherTransitionable) {
                ((LauncherTransitionable) toView).onLauncherTransitionStart(instance, null, false);
                ((LauncherTransitionable) toView).onLauncherTransitionEnd(instance, null, false);

                if (!springLoaded) {
                    // Hide the workspace scrollbar
                    mWorkspace.hideScrollingIndicator(true);
                }
            }
            if (!mShowWallpaper) updateWallpaperVisibility(false);
        }
    }

    /**
     * Zoom the camera back into the workspace, hiding 'fromView'.
     * This is the opposite of showAppsCustomizeHelper.
     * @param animated If true, the transition will be animated.
     */
    private void hideAppsCustomizeHelper(boolean animated, final boolean springLoaded) {
        if (mStateAnimation != null) {
            mStateAnimation.cancel();
            mStateAnimation = null;
        }
        Resources res = getResources();
        final Launcher instance = this;

        final int duration = res.getInteger(R.integer.config_appsCustomizeZoomOutTime);
        final float scaleFactor = (float)
                res.getInteger(R.integer.config_appsCustomizeZoomScaleFactor);
        final View fromView = mAppsCustomizeTabHost;

        setPivotsForZoom(fromView, scaleFactor);

        if (!mShowWallpaper) updateWallpaperVisibility(true);

        if (animated) {
            final float oldScaleX = fromView.getScaleX();
            final float oldScaleY = fromView.getScaleY();

            ValueAnimator scaleAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(duration);
            scaleAnim.setInterpolator(new Workspace.ZoomInInterpolator());
            scaleAnim.addUpdateListener(new LauncherAnimatorUpdateListener() {
                public void onAnimationUpdate(float a, float b) {
                    fromView.setScaleX(a * oldScaleX + b * scaleFactor);
                    fromView.setScaleY(a * oldScaleY + b * scaleFactor);
                }
            });
            final ValueAnimator alphaAnim = ValueAnimator.ofFloat(0f, 1f);
            alphaAnim.setDuration(res.getInteger(R.integer.config_appsCustomizeFadeOutTime));
            alphaAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            alphaAnim.addUpdateListener(new LauncherAnimatorUpdateListener() {
                public void onAnimationUpdate(float a, float b) {
                    fromView.setAlpha(a * 1f + b * 0f);
                }
            });
            if (fromView instanceof LauncherTransitionable) {
                ((LauncherTransitionable) fromView).onLauncherTransitionStart(instance, alphaAnim,
                        true);
            }
            alphaAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!mShowWallpaper) updateWallpaperVisibility(true);
                    mWorkspace.setVisibility(View.VISIBLE);
                    showDockDivider(true);
                    showAllAppsBar(true);
                    showHotseat(true);
                    if (!springLoaded) showSearchBar(true);
                    fromView.setVisibility(View.GONE);
                    if (fromView instanceof LauncherTransitionable) {
                        ((LauncherTransitionable) fromView).onLauncherTransitionEnd(instance,
                                alphaAnim, true);
                    }
                }
            });

            mStateAnimation = new AnimatorSet();
            mStateAnimation.playTogether(scaleAnim, alphaAnim);
            mStateAnimation.start();
        } else {
            fromView.setVisibility(View.GONE);
            if (fromView instanceof LauncherTransitionable) {
                ((LauncherTransitionable) fromView).onLauncherTransitionStart(instance, null, true);
                ((LauncherTransitionable) fromView).onLauncherTransitionEnd(instance, null, true);
            }
            mWorkspace.hideScrollingIndicator(false);
            mWorkspace.setVisibility(View.VISIBLE);
            showDockDivider(true);
            showHotseat(true);
            if (!springLoaded) {
                showSearchBar(true);
                showAllAppsBar(true);
            }
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            mAppsCustomizeTabHost.onTrimMemory();
        }
    }

    void showWorkspace(boolean animated) {
        Resources res = getResources();
        int stagger = res.getInteger(R.integer.config_appsCustomizeWorkspaceAnimationStagger);

        Workspace.TransitionEffect transitionEffect = mWorkspace.getTransitionEffect();
        final boolean animateWorkspace = (transitionEffect != Workspace.TransitionEffect.RotateUp &&
                transitionEffect != Workspace.TransitionEffect.RotateDown) || mWorkspace.getState()
                != Workspace.State.SMALL;

        if (animateWorkspace) {
            mWorkspace.changeState(Workspace.State.NORMAL, animated, stagger);
        }

        if (mState != State.WORKSPACE) {
            mWorkspace.setVisibility(View.VISIBLE);
            hideAppsCustomizeHelper(animated, false);

        }

        mWorkspace.flashScrollingIndicator(animated);

        // Change the state *after* we've called all the transition code
        mState = State.WORKSPACE;

        // Resume the auto-advance of widgets
        mUserPresent = true;
        updateRunning();

        // send an accessibility event to announce the context change
        getWindow().getDecorView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    void showAllApps(boolean animated) {
        if (mState != State.WORKSPACE) return;

        showAppsCustomizeHelper(animated, false);
        mAppsCustomizeTabHost.requestFocus();

        // Change the state *after* we've called all the transition code
        mState = State.APPS_CUSTOMIZE;

        // Pause the auto-advance of widgets until we are out of AllApps
        mUserPresent = false;
        updateRunning();
        closeFolder();

        // Send an accessibility event to announce the context change
        getWindow().getDecorView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    void enterSpringLoadedDragMode() {
        if (mState == State.APPS_CUSTOMIZE) {
            mWorkspace.changeState(Workspace.State.SPRING_LOADED);
            hideAppsCustomizeHelper(true, true);
            hideDockDivider(true);
            mState = State.APPS_CUSTOMIZE_SPRING_LOADED;
        }
    }

    void exitSpringLoadedDragModeDelayed(final boolean successfulDrop, boolean extendedDelay) {
        if (mState != State.APPS_CUSTOMIZE_SPRING_LOADED) return;

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (successfulDrop) {
                    // Before we show workspace, hide all apps again because
                    // exitSpringLoadedDragMode made it visible. This is a bit hacky; we should
                    // clean up our state transition functions
                    mAppsCustomizeTabHost.setVisibility(View.GONE);
                    showWorkspace(true);
                } else {
                    exitSpringLoadedDragMode();
                }
            }
        }, (extendedDelay ?
                EXIT_SPRINGLOADED_MODE_LONG_TIMEOUT :
                EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT));
    }

    void exitSpringLoadedDragMode() {
        if (mState == State.APPS_CUSTOMIZE_SPRING_LOADED) {
            final boolean animated = true;
            final boolean springLoaded = true;
            showAppsCustomizeHelper(animated, springLoaded);
            mState = State.APPS_CUSTOMIZE;
        }
        // Otherwise, we are not in spring loaded mode, so don't do anything.
    }

    void hideDockDivider(boolean animated) {
        if (mQsbDivider != null && mDockDivider != null) {
            if (mShowSearchBar) {
                mQsbDivider.setVisibility(View.INVISIBLE);
            }
            if (mShowDockDivider) {
                mDockDivider.setVisibility(View.INVISIBLE);
            }
        } else if (mDockDivider != null && mShowDockDivider) {
            if (animated) {
                int duration = mSearchDropTargetBar.getTransitionOutDuration();
                mDockDivider.animate().alpha(0f).setDuration(duration);
                if (mDockDividerTwo != null && mShowDockDividerTwo) {
                    mDockDividerTwo.animate().alpha(0f).setDuration(duration);
                }
            } else {
                mDockDivider.setAlpha(0f);
                if (mDockDividerTwo != null && mShowDockDividerTwo) {
                    mDockDividerTwo.setAlpha(0f);
                }
            }
            mDockDivider.setVisibility(View.INVISIBLE);
            if (mDockDividerTwo != null && mShowDockDividerTwo) {
                mDockDividerTwo.setVisibility(View.INVISIBLE);
            }
        }
    }

    void showDockDivider(boolean animated) {
        if (mQsbDivider != null && mDockDivider != null) {
            if (mShowSearchBar) {
                mQsbDivider.setVisibility(View.VISIBLE);
            }
            if (mShowDockDivider) {
                mDockDivider.setVisibility(View.VISIBLE);
            }
            if (mDividerAnimator != null) {
                mDividerAnimator.cancel();
                if (mShowSearchBar) {
                    mQsbDivider.setAlpha(1f);
                }
                mDockDivider.setAlpha(1f);
                mDividerAnimator = null;
            }
            if (animated) {
                mDividerAnimator = new AnimatorSet();
                if (mShowSearchBar && mShowDockDivider) {
                    mDividerAnimator.playTogether(ObjectAnimator.ofFloat(mQsbDivider, "alpha", 1f),
                            ObjectAnimator.ofFloat(mDockDivider, "alpha", 1f));
                } else if (mShowSearchBar) {
                    mDividerAnimator.play(ObjectAnimator.ofFloat(mQsbDivider, "alpha", 1f));
                } else if (mShowDockDivider) {
                    mDividerAnimator.play(ObjectAnimator.ofFloat(mDockDivider, "alpha", 1f));
                }
                mDividerAnimator.setDuration(mSearchDropTargetBar.getTransitionInDuration());
                mDividerAnimator.start();
            }
        } else if (mDockDivider != null && mShowDockDivider && mShowHotseat) {
            mDockDivider.setVisibility(View.VISIBLE);
            if (mDockDividerTwo != null && mShowDockDividerTwo) {
                mDockDividerTwo.setVisibility(View.VISIBLE);
            }
            if (animated) {
                int duration = mSearchDropTargetBar.getTransitionInDuration();
                mDockDivider.animate().alpha(1f).setDuration(duration);
                if (mDockDividerTwo != null && mShowDockDividerTwo) {
                    mDockDividerTwo.animate().alpha(1f).setDuration(duration);
                }
            } else {
                mDockDivider.setAlpha(1f);
                if (mDockDividerTwo != null && mShowDockDividerTwo) {
                    mDockDividerTwo.setAlpha(1f);
                }
            }
        }
    }

    void lockAllApps() {
        // TODO
    }

    void unlockAllApps() {
        // TODO
    }

    public boolean isAllAppsCustomizeOpen() {
        return mState == State.APPS_CUSTOMIZE;
    }

    void showAllAppsBar(boolean animated) {
        if (mAllAppsBar != null && mState != State.APPS_CUSTOMIZE_SPRING_LOADED
                && mShowAllAppsBar) {
            mAllAppsBar.setVisibility(View.VISIBLE);
            if (animated) {
                int duration = mSearchDropTargetBar.getTransitionInDuration();
                mAllAppsBar.animate().alpha(1f).setDuration(duration);
            } else {
                mAllAppsBar.setAlpha(1f);
            }
        }
    }

    void hideAllAppsBar(boolean animated) {
        if (mAllAppsBar != null) {
            if (animated) {
                int duration = mSearchDropTargetBar.getTransitionOutDuration();
                mAllAppsBar.animate().alpha(0f).setDuration(duration);
            } else {
                mAllAppsBar.setAlpha(0f);
            }
            mAllAppsBar.setVisibility(View.INVISIBLE);
        }
    }

    void showSearchBar(boolean animated) {
        if (mShowSearchBar) mSearchDropTargetBar.showSearchBar(animated);
    }

    void hideSearchBar(boolean animated) {
        mSearchDropTargetBar.hideSearchBar(animated);
    }

    /**
     * Shows the hotseat area.
     */
    void showHotseat(boolean animated) {
        if (mShowHotseat) {
            if (mHotseat != null) {
                mHotseat.setVisibility(View.VISIBLE);
                if (animated) {
                    int duration = mSearchDropTargetBar.getTransitionInDuration();
                    mHotseat.animate().alpha(1f).setDuration(duration);
                } else {
                    mHotseat.setAlpha(1f);
                }
            }
            if (mHotseatTwo != null) {
                mHotseatTwo.setVisibility(View.VISIBLE);
                if (animated) {
                    int duration = mSearchDropTargetBar.getTransitionInDuration();
                    mHotseatTwo.animate().alpha(1f).setDuration(duration);
                } else {
                    mHotseatTwo.setAlpha(1f);
                }
            }
        }
    }

    /**
     * Hides the hotseat area.
     */
    void hideHotseat(boolean animated) {
        if (mHotseat != null) {
            if (animated) {
                int duration = mSearchDropTargetBar.getTransitionOutDuration();
                mHotseat.animate().alpha(0f).setDuration(duration);
            } else {
                mHotseat.setAlpha(0f);
            }
            mHotseat.setVisibility(View.INVISIBLE);
        }
        if (mHotseatTwo != null) {
            if (animated) {
                int duration = mSearchDropTargetBar.getTransitionOutDuration();
                mHotseatTwo.animate().alpha(0f).setDuration(duration);
            } else {
                mHotseatTwo.setAlpha(0f);
            }
            mHotseatTwo.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Shows the desktop items that don't scroll.
     */
    void showWorkspaceItems() {
        if (mHidePageControls) {
            showAllAppsBar(true);
            showSearchBar(true);
        }
        showDockDivider(true);
        showHotseat(true);

    }

    /**
     * Hides the desktop items that don't scroll.
     */
    void hideWorkspaceItems() {
        if (mHidePageControls) {
            hideAllAppsBar(true);
            hideSearchBar(true);
        }
        hideDockDivider(true);
        hideHotseat(true);

    }

    /**
     * Shows the hotseat area outline.
     */
    void showHotseatOutlines() {
        if (mShowHotseat) {
            if (mHotseat != null) {
                mHotseat.showOutlines();
            }
            if (mHotseatTwo != null) {
                mHotseatTwo.showOutlines();
            }
        }
    }

    /**
     * Hides the hotseat area outline.
     */
    void hideHotseatOutlines() {
        if (mShowHotseat) {
            if (mHotseat != null) {
                mHotseat.hideOutlines();
            }
            if (mHotseatTwo != null) {
                mHotseatTwo.hideOutlines();
            }
        }
    }

    /**
     * Add an item from all apps or customize onto the given workspace screen.
     * If layout is null, add to the current screen.
     */
    void addExternalItemToScreen(ItemInfo itemInfo, final CellLayout layout) {
        if (!mWorkspace.addExternalItemToScreen(itemInfo, layout)) {
            showOutOfSpaceMessage();
        }
    }

    public int getCurrentOrientation() {
        return getResources().getConfiguration().orientation;
    }

    /** Maps the current orientation to an index for referencing orientation correct global icons */
    private int getCurrentOrientationIndexForGlobalIcons() {
        // default - 0, landscape - 1
        switch (getCurrentOrientation()) {
        case Configuration.ORIENTATION_LANDSCAPE:
            return 1;
        default:
            return 0;
        }
    }

    private Drawable getExternalPackageToolbarIcon(ComponentName activityName) {
        try {
            PackageManager packageManager = getPackageManager();
            // Look for the toolbar icon specified in the activity meta-data
            Bundle metaData = packageManager.getActivityInfo(
                    activityName, PackageManager.GET_META_DATA).metaData;
            if (metaData != null) {
                int iconResId = metaData.getInt(TOOLBAR_ICON_METADATA_NAME);
                if (iconResId != 0) {
                    Resources res = packageManager.getResourcesForActivity(activityName);
                    return res.getDrawable(iconResId);
                }
            }
        } catch (NameNotFoundException e) {
            // This can happen if the activity defines an invalid drawable
            Log.w(TAG, "Failed to load toolbar icon; " + activityName.flattenToShortString() +
                    " not found", e);
        } catch (Resources.NotFoundException nfe) {
            // This can happen if the activity defines an invalid drawable
            Log.w(TAG, "Failed to load toolbar icon from " + activityName.flattenToShortString(),
                    nfe);
        }
        return null;
    }

    // if successful in getting icon, return it; otherwise, set button to use default drawable
    private Drawable.ConstantState updateTextButtonWithIconFromExternalActivity(
            int buttonId, ComponentName activityName, int fallbackDrawableId) {
        Drawable toolbarIcon = getExternalPackageToolbarIcon(activityName);
        Resources r = getResources();
        int w = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_width);
        int h = r.getDimensionPixelSize(R.dimen.toolbar_external_icon_height);

        TextView button = (TextView) findViewById(buttonId);
        // If we were unable to find the icon via the meta-data, use a generic one
        if (toolbarIcon == null) {
            toolbarIcon = r.getDrawable(fallbackDrawableId);
            toolbarIcon.setBounds(0, 0, w, h);
            if (button != null) {
                button.setCompoundDrawables(toolbarIcon, null, null, null);
            }
            return null;
        } else {
            toolbarIcon.setBounds(0, 0, w, h);
            if (button != null) {
                button.setCompoundDrawables(toolbarIcon, null, null, null);
            }
            return toolbarIcon.getConstantState();
        }
    }

    // if successful in getting icon, return it; otherwise, set button to use default drawable
    private Drawable.ConstantState updateButtonWithIconFromExternalActivity(
            int buttonId, ComponentName activityName, int fallbackDrawableId) {
        ImageView button = (ImageView) findViewById(buttonId);
        Drawable toolbarIcon = getExternalPackageToolbarIcon(activityName);

        if (button != null) {
            // If we were unable to find the icon via the meta-data, use a
            // generic one
            if (toolbarIcon == null) {
                button.setImageResource(fallbackDrawableId);
            } else {
                button.setImageDrawable(toolbarIcon);
            }
        }

        return toolbarIcon != null ? toolbarIcon.getConstantState() : null;

    }

    private void updateTextButtonWithDrawable(int buttonId, Drawable.ConstantState d) {
        TextView button = (TextView) findViewById(buttonId);
        button.setCompoundDrawables(d.newDrawable(getResources()), null, null, null);
    }

    private void updateButtonWithDrawable(int buttonId, Drawable.ConstantState d) {
        ImageView button = (ImageView) findViewById(buttonId);
        button.setImageDrawable(d.newDrawable(getResources()));
    }

    private void invalidatePressedFocusedStates(View container, View button) {
        if (container instanceof HolographicLinearLayout) {
            HolographicLinearLayout layout = (HolographicLinearLayout) container;
            layout.invalidatePressedFocusedStates();
        } else if (button instanceof HolographicImageView) {
            HolographicImageView view = (HolographicImageView) button;
            view.invalidatePressedFocusedStates();
        }
    }

    private boolean updateGlobalSearchIcon() {
        final View searchButtonContainer = findViewById(R.id.search_button_container);
        final ImageView searchButton = (ImageView) findViewById(R.id.search_button);
        final View searchDivider = findViewById(R.id.search_divider);
        final View voiceButtonContainer = findViewById(R.id.voice_button_container);
        final View voiceButton = findViewById(R.id.voice_button);

        final SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        ComponentName activityName = searchManager.getGlobalSearchActivity();
        if (activityName != null) {
            int coi = getCurrentOrientationIndexForGlobalIcons();
            sGlobalSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                    R.id.search_button, activityName, R.drawable.ic_home_search_normal_holo);
            if (searchDivider != null) {
                searchDivider.setVisibility(mShowButtonDividers ? View.VISIBLE : View.INVISIBLE);
            }
            if (searchButtonContainer != null) searchButtonContainer.setVisibility(View.VISIBLE);
            searchButton.setVisibility(View.VISIBLE);
            invalidatePressedFocusedStates(searchButtonContainer, searchButton);
            return true;
        } else {
            // We disable both search and voice search when there is no global search provider
            if (searchDivider != null) searchDivider.setVisibility(View.GONE);
            if (searchButtonContainer != null) searchButtonContainer.setVisibility(View.GONE);
            if (voiceButtonContainer != null) voiceButtonContainer.setVisibility(View.GONE);
            searchButton.setVisibility(View.GONE);
            voiceButton.setVisibility(View.GONE);
            return false;
        }
    }

    private void updateGlobalSearchIcon(Drawable.ConstantState d) {
        final View searchButtonContainer = findViewById(R.id.search_button_container);
        final View searchButton = (ImageView) findViewById(R.id.search_button);
        updateButtonWithDrawable(R.id.search_button, d);
        invalidatePressedFocusedStates(searchButtonContainer, searchButton);
    }

    private boolean updateVoiceSearchIcon(boolean searchVisible) {
        final View searchDivider = findViewById(R.id.search_divider);
        final View voiceButtonContainer = findViewById(R.id.voice_button_container);
        final View voiceButton = findViewById(R.id.voice_button);

        // We only show/update the voice search icon if the search icon is enabled as well
        Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
        ComponentName activityName = intent.resolveActivity(getPackageManager());
        if (searchVisible && activityName != null) {
            int coi = getCurrentOrientationIndexForGlobalIcons();
            sVoiceSearchIcon[coi] = updateButtonWithIconFromExternalActivity(
                    R.id.voice_button, activityName, R.drawable.ic_home_voice_search_holo);
            if (searchDivider != null) {
                searchDivider.setVisibility(mShowButtonDividers ? View.VISIBLE : View.INVISIBLE);
            }
            if (voiceButtonContainer != null) voiceButtonContainer.setVisibility(View.VISIBLE);
            voiceButton.setVisibility(View.VISIBLE);
            invalidatePressedFocusedStates(voiceButtonContainer, voiceButton);
            return true;
        } else {
            if (searchDivider != null) searchDivider.setVisibility(View.GONE);
            if (voiceButtonContainer != null) voiceButtonContainer.setVisibility(View.GONE);
            voiceButton.setVisibility(View.GONE);
            return false;
        }
    }

    private void updateVoiceSearchIcon(Drawable.ConstantState d) {
        final View voiceButtonContainer = findViewById(R.id.voice_button_container);
        final View voiceButton = findViewById(R.id.voice_button);
        updateButtonWithDrawable(R.id.voice_button, d);
        invalidatePressedFocusedStates(voiceButtonContainer, voiceButton);
    }

    /**
     * Sets the app market icon
     */
    private void updateAppMarketIcon() {
        final View marketButton = findViewById(R.id.market_button);
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MARKET);
        // Find the app market activity by resolving an intent.
        // (If multiple app markets are installed, it will return the ResolverActivity.)
        ComponentName activityName = intent.resolveActivity(getPackageManager());

        if (activityName != null) {
            mAppMarketIntent = intent;
        }

        if (activityName != null && (ViewConfiguration.get(this).hasPermanentMenuKey() ||
                getResources().getBoolean(R.bool.config_cyanogenmod))) {
            int coi = getCurrentOrientationIndexForGlobalIcons();
            sAppMarketIcon[coi] = updateTextButtonWithIconFromExternalActivity(
                    R.id.market_button, activityName, R.drawable.ic_launcher_market_holo);
            marketButton.setVisibility(View.VISIBLE);
        } else {
            // We should hide and disable the view so that we don't try and restore the visibility
            // of it when we swap between drag & normal states from IconDropTarget subclasses.
            marketButton.setVisibility(View.GONE);
            marketButton.setEnabled(false);
        }
    }

    private void updateAppMarketIcon(Drawable.ConstantState d) {
        updateTextButtonWithDrawable(R.id.market_button, d);
    }

    private void updateOverflowMenuButton() {
        View overflowMenuButton = findViewById(R.id.overflow_menu_button);
        if (ViewConfiguration.get(this).hasPermanentMenuKey() ||
                getResources().getBoolean(R.bool.config_cyanogenmod)) {
            overflowMenuButton.setVisibility(View.GONE);
            overflowMenuButton.setEnabled(false);
        } else {
            overflowMenuButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Displays the shortcut creation dialog and launches, if necessary, the
     * appropriate activity.
     */
    private class CreateShortcut implements DialogInterface.OnClickListener,
            DialogInterface.OnCancelListener, DialogInterface.OnDismissListener,
            DialogInterface.OnShowListener {

        private AddAdapter mAdapter;

        Dialog createDialog() {
            mAdapter = new AddAdapter(Launcher.this);

            final AlertDialog.Builder builder = new AlertDialog.Builder(Launcher.this,
                    AlertDialog.THEME_HOLO_DARK);
            builder.setAdapter(mAdapter, this);

            AlertDialog dialog = builder.create();
            dialog.setOnCancelListener(this);
            dialog.setOnDismissListener(this);
            dialog.setOnShowListener(this);

            return dialog;
        }

        public void onCancel(DialogInterface dialog) {
            mWaitingForResult = false;
            cleanup();
        }

        public void onDismiss(DialogInterface dialog) {
            mWaitingForResult = false;
            cleanup();
        }

        private void cleanup() {
            try {
                dismissDialog(DIALOG_CREATE_SHORTCUT);
            } catch (Exception e) {
                // An exception is thrown if the dialog is not visible, which is fine
            }
        }

        /**
         * Handle the action clicked in the "Add to home" dialog.
         */
        public void onClick(DialogInterface dialog, int which) {
            cleanup();

            AddAdapter.ListItem item = (AddAdapter.ListItem) mAdapter.getItem(which);
            switch (item.actionTag) {
                case AddAdapter.ITEM_APPLICATION: {
                    if (mAppsCustomizeTabHost != null) {
                        mAppsCustomizeTabHost.selectAppsTab();
                    }
                    showAllApps(true);
                    break;
                }
                case AddAdapter.ITEM_APPWIDGET: {
                    if (mAppsCustomizeTabHost != null) {
                        mAppsCustomizeTabHost.selectWidgetsTab();
                    }
                    showAllApps(true);
                    break;
                }
                case AddAdapter.ITEM_WALLPAPER: {
                    startWallpaper();
                    break;
                }
            }
        }

        public void onShow(DialogInterface dialog) {
            mWaitingForResult = true;
        }
    }

    /**
     * Receives notifications when system dialogs are to be closed.
     */
    private class CloseSystemDialogsIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            closeSystemDialogs();
        }
    }

    /**
     * Receives notifications whenever the appwidgets are reset.
     */
    private class AppWidgetResetObserver extends ContentObserver {
        public AppWidgetResetObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            onAppWidgetReset();
        }
    }

    /**
     * If the activity is currently paused, signal that we need to re-run the loader
     * in onResume.
     *
     * This needs to be called from incoming places where resources might have been loaded
     * while we are paused.  That is becaues the Configuration might be wrong
     * when we're not running, and if it comes back to what it was when we
     * were paused, we are not restarted.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     *
     * @return true if we are currently paused.  The caller might be able to
     * skip some work in that case since we will come back again.
     */
    public boolean setLoadOnResume() {
        if (mPaused) {
            Log.i(TAG, "setLoadOnResume");
            mOnResumeNeedsLoad = true;
            return true;
        } else {
            return false;
        }
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public int getCurrentWorkspaceScreen() {
        if (mWorkspace != null) {
            return mWorkspace.getCurrentPage();
        } else {
            return DEFAULT_SCREEN;
        }
    }


    /**
     * Refreshes the shortcuts shown on the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void startBinding() {
        final Workspace workspace = mWorkspace;

        mWorkspace.clearDropTargets();
        int count = workspace.getChildCount();
        for (int i = 0; i < count; i++) {
            // Use removeAllViewsInLayout() to avoid an extra requestLayout() and invalidate().
            final CellLayout layoutParent = (CellLayout) workspace.getChildAt(i);
            layoutParent.removeAllViewsInLayout();
        }
        mWidgetsToAdvance.clear();
        if (mHotseat != null) {
            mHotseat.resetLayout(true);
        }
        if (mHotseatTwo != null) {
            mHotseatTwo.resetLayout(false);
        }
    }

    /**
     * Bind the items start-end from the list.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindItems(ArrayList<ItemInfo> shortcuts, int start, int end) {
        setLoadOnResume();

        final Workspace workspace = mWorkspace;
        for (int i=start; i<end; i++) {
            final ItemInfo item = shortcuts.get(i);

            // Short circuit if we are loading dock items for a configuration which has no dock
            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
                    mHotseat == null) {
                continue;
            }

            if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT_TWO &&
                    mHotseatTwo == null) {
                continue;
            }

            switch (item.itemType) {
                case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
                case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
                    View shortcut = createShortcut((ShortcutInfo)item);
                    workspace.addInScreen(shortcut, item.container, item.screen, item.cellX,
                            item.cellY, 1, 1, false);
                    break;
                case LauncherSettings.Favorites.ITEM_TYPE_FOLDER:
                    FolderIcon newFolder = FolderIcon.fromXml(R.layout.folder_icon, this,
                            (ViewGroup) workspace.getChildAt(workspace.getCurrentPage()),
                            (FolderInfo) item, mIconCache);
                    if (!mHideIconLabels) {
                        newFolder.setTextVisible(false);
                    }
                    workspace.addInScreen(newFolder, item.container, item.screen, item.cellX,
                            item.cellY, 1, 1, false);
                    break;
            }
        }
        workspace.requestLayout();
    }

    /**
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindFolders(HashMap<Long, FolderInfo> folders) {
        setLoadOnResume();
        sFolders.clear();
        sFolders.putAll(folders);
    }

    /**
     * Add the views for a widget to the workspace.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppWidget(LauncherAppWidgetInfo item) {
        setLoadOnResume();

        final long start = DEBUG_WIDGETS ? SystemClock.uptimeMillis() : 0;
        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bindAppWidget: " + item);
        }
        final Workspace workspace = mWorkspace;

        final int appWidgetId = item.appWidgetId;
        final AppWidgetProviderInfo appWidgetInfo = mAppWidgetManager.getAppWidgetInfo(appWidgetId);
        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bindAppWidget: id=" + item.appWidgetId + " belongs to component " +
                    appWidgetInfo.provider);
        }

        item.hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);

        item.hostView.setAppWidget(appWidgetId, appWidgetInfo);
        item.hostView.setTag(item);

        workspace.addInScreen(item.hostView, item.container, item.screen, item.cellX,
                item.cellY, item.spanX, item.spanY, false);

        addWidgetToAutoAdvanceIfNeeded(item.hostView, appWidgetInfo);

        workspace.requestLayout();

        if (DEBUG_WIDGETS) {
            Log.d(TAG, "bound widget id="+item.appWidgetId+" in "
                    + (SystemClock.uptimeMillis()-start) + "ms");
        }
    }

    /**
     * Callback saying that there aren't any more items to bind.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void finishBindingItems() {
        setLoadOnResume();

        if (mSavedState != null) {
            if (!mWorkspace.hasFocus()) {
                mWorkspace.getChildAt(mWorkspace.getCurrentPage()).requestFocus();
            }
            mSavedState = null;
        }

        if (mSavedInstanceState != null) {
            super.onRestoreInstanceState(mSavedInstanceState);
            mSavedInstanceState = null;
        }

        mWorkspaceLoading = false;

        // If we received the result of any pending adds while the loader was running (e.g. the
        // widget configuration forced an orientation change), process them now.
        for (int i = 0; i < sPendingAddList.size(); i++) {
            completeAdd(sPendingAddList.get(i));
        }
        sPendingAddList.clear();

        // Update the market app icon as necessary (the other icons will be managed in response to
        // package changes in bindSearchablesChanged()
        updateAppMarketIcon();

        // Hide overflow menu on devices with a hardkey
        // updateOverflowMenuButton();

        mWorkspace.post(mBuildLayersRunnable);
    }

    @Override
    public void bindSearchablesChanged() {
        boolean searchVisible = updateGlobalSearchIcon();
        boolean voiceVisible = updateVoiceSearchIcon(searchVisible);
        mSearchDropTargetBar.onSearchPackagesChanged(searchVisible, voiceVisible);
    }

    /**
     * Add the icons for all apps.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAllApplications(final ArrayList<ApplicationInfo> apps) {
        // Remove the progress bar entirely; we could also make it GONE
        // but better to remove it since we know it's not going to be used
        View progressBar = mAppsCustomizeTabHost.
            findViewById(R.id.apps_customize_progress_bar);
        if (progressBar != null) {
            ((ViewGroup)progressBar.getParent()).removeView(progressBar);
        }
        // We just post the call to setApps so the user sees the progress bar
        // disappear-- otherwise, it just looks like the progress bar froze
        // which doesn't look great
        mAppsCustomizeTabHost.post(new Runnable() {
            public void run() {
                if (mAppsCustomizeContent != null) {
                    mAppsCustomizeContent.setApps(apps);
                }
            }
        });
    }

    /**
     * A package was installed.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsAdded(ArrayList<ApplicationInfo> apps) {
        setLoadOnResume();
        removeDialog(DIALOG_CREATE_SHORTCUT);

        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.addApps(apps);
        }
    }

    /**
     * A package was updated.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsUpdated(ArrayList<ApplicationInfo> apps) {
        setLoadOnResume();
        removeDialog(DIALOG_CREATE_SHORTCUT);
        if (mWorkspace != null) {
            mWorkspace.updateShortcuts(apps);
        }

        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.updateApps(apps);
        }
    }

    /**
     * A package was uninstalled.
     *
     * Implementation of the method from LauncherModel.Callbacks.
     */
    public void bindAppsRemoved(ArrayList<ApplicationInfo> apps, boolean permanent) {
        removeDialog(DIALOG_CREATE_SHORTCUT);
        if (permanent) {
            mWorkspace.removeItems(apps);
        }

        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.removeApps(apps);
        }

        // Notify the drag controller
        mDragController.onAppsRemoved(apps, this);
    }

    /**
     * A number of packages were updated.
     */
    public void bindPackagesUpdated() {
        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.onPackagesUpdated();
        }
    }

    private int mapConfigurationOriActivityInfoOri(int configOri) {
        final Display d = getWindowManager().getDefaultDisplay();
        int naturalOri = Configuration.ORIENTATION_LANDSCAPE;
        switch (d.getRotation()) {
        case Surface.ROTATION_0:
        case Surface.ROTATION_180:
            // We are currently in the same basic orientation as the natural orientation
            naturalOri = configOri;
            break;
        case Surface.ROTATION_90:
        case Surface.ROTATION_270:
            // We are currently in the other basic orientation to the natural orientation
            naturalOri = (configOri == Configuration.ORIENTATION_LANDSCAPE) ?
                    Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE;
            break;
        }

        int[] oriMap = {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
                ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        };
        // Since the map starts at portrait, we need to offset if this device's natural orientation
        // is landscape.
        int indexOffset = 0;
        if (naturalOri == Configuration.ORIENTATION_LANDSCAPE) {
            indexOffset = 1;
        }
        return oriMap[(d.getRotation() + indexOffset) % 4];
    }

    public void lockScreenOrientationOnLargeUI() {
        if (LauncherApplication.isScreenLarge()) {
            setRequestedOrientation(mapConfigurationOriActivityInfoOri(getCurrentOrientation()));
        }
    }

    public void unlockScreenOrientationOnLargeUI() {
        if (LauncherApplication.isScreenLarge()) {
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    syncOrientation();
                }
            }, mRestoreScreenOrientationDelay);
        }
    }

    /* Cling related */
    private boolean isClingsEnabled() {
        // disable clings when running in a test harness
        if(ActivityManager.isRunningInTestHarness()) return false;

        return true;
    }
    private Cling initCling(int clingId, int[] positionData, boolean animate, int delay) {
        Cling cling = (Cling) findViewById(clingId);
        if (cling != null) {
            cling.init(this, positionData);
            cling.setVisibility(View.VISIBLE);
            cling.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            if (animate) {
                cling.buildLayer();
                cling.setAlpha(0f);
                cling.animate()
                    .alpha(1f)
                    .setInterpolator(new AccelerateInterpolator())
                    .setDuration(SHOW_CLING_DURATION)
                    .setStartDelay(delay)
                    .start();
            } else {
                cling.setAlpha(1f);
            }
        }
        return cling;
    }
    private void dismissCling(final Cling cling, final String flag, int duration) {
        if (cling != null) {
            cling.dismiss();
            ObjectAnimator anim = ObjectAnimator.ofFloat(cling, "alpha", 0f);
            anim.setDuration(duration);
            anim.addListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator animation) {
                    cling.setVisibility(View.GONE);
                    cling.cleanup();
                    SharedPreferences prefs =
                            getSharedPreferences(PreferencesProvider.PREFERENCES_KEY,
                            Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(flag, true);
                    editor.commit();
                };
            });
            anim.start();
        }
    }
    private void removeCling(int id) {
        final View cling = findViewById(id);
        if (cling != null) {
            final ViewGroup parent = (ViewGroup) cling.getParent();
            parent.post(new Runnable() {
                @Override
                public void run() {
                    parent.removeView(cling);
                }
            });
        }
    }
    public void showFirstRunWorkspaceCling() {
        // Enable the clings only if they have not been dismissed before
        SharedPreferences prefs =
            getSharedPreferences(PreferencesProvider.PREFERENCES_KEY, Context.MODE_PRIVATE);
        if (isClingsEnabled() && !prefs.getBoolean(Cling.WORKSPACE_CLING_DISMISSED_KEY, false)) {
            initCling(R.id.workspace_cling, null, false, 0);
        } else {
            removeCling(R.id.workspace_cling);
        }
    }
    public void showFirstRunAllAppsCling(int[] position) {
        // Enable the clings only if they have not been dismissed before
        SharedPreferences prefs =
            getSharedPreferences(PreferencesProvider.PREFERENCES_KEY, Context.MODE_PRIVATE);
        if (isClingsEnabled() && !prefs.getBoolean(Cling.ALLAPPS_CLING_DISMISSED_KEY, false)) {
            initCling(R.id.all_apps_cling, position, true, 0);
        } else {
            removeCling(R.id.all_apps_cling);
        }
    }
    public void showFirstRunAllAppsSortCling() {
        // Enable the clings only if they have not been dismissed before
        SharedPreferences prefs =
            getSharedPreferences(PreferencesProvider.PREFERENCES_KEY, Context.MODE_PRIVATE);
        if (isClingsEnabled() && !prefs.getBoolean(Cling.ALLAPPS_SORT_CLING_DISMISSED_KEY, false)) {
            initCling(R.id.all_apps_sort_cling, null, true, 0);
        } else {
            removeCling(R.id.all_apps_sort_cling);
        }
    }
    public Cling showFirstRunFoldersCling() {
        // Enable the clings only if they have not been dismissed before
        SharedPreferences prefs =
            getSharedPreferences(PreferencesProvider.PREFERENCES_KEY, Context.MODE_PRIVATE);
        Cling cling = null;
        if (isClingsEnabled() && !prefs.getBoolean(Cling.FOLDER_CLING_DISMISSED_KEY, false)) {
            cling = initCling(R.id.folder_cling, null, true, 0);
        } else {
            removeCling(R.id.folder_cling);
        }
        return cling;
    }
    public boolean isFolderClingVisible() {
        Cling cling = (Cling) findViewById(R.id.folder_cling);
        if (cling != null) {
            return cling.getVisibility() == View.VISIBLE;
        }
        return false;
    }
    public void dismissWorkspaceCling(View v) {
        Cling cling = (Cling) findViewById(R.id.workspace_cling);
        dismissCling(cling, Cling.WORKSPACE_CLING_DISMISSED_KEY, DISMISS_CLING_DURATION);
    }
    public void dismissAllAppsCling(View v) {
        Cling cling = (Cling) findViewById(R.id.all_apps_cling);
        dismissCling(cling, Cling.ALLAPPS_CLING_DISMISSED_KEY, DISMISS_CLING_DURATION);
    }
    public void dismissAllAppsSortCling(View v) {
        Cling cling = (Cling) findViewById(R.id.all_apps_sort_cling);
        dismissCling(cling, Cling.ALLAPPS_SORT_CLING_DISMISSED_KEY, DISMISS_CLING_DURATION);
    }
    public void dismissFolderCling(View v) {
        Cling cling = (Cling) findViewById(R.id.folder_cling);
        dismissCling(cling, Cling.FOLDER_CLING_DISMISSED_KEY, DISMISS_CLING_DURATION);
    }

    public boolean preferencesChanged() {
        SharedPreferences prefs =
                getSharedPreferences(PreferencesProvider.PREFERENCES_KEY, Context.MODE_PRIVATE);
        boolean preferencesChanged =
                prefs.getBoolean(PreferencesProvider.PREFERENCES_CHANGED, false);
        if (preferencesChanged) {
            SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(PreferencesProvider.PREFERENCES_CHANGED, false);
                    editor.commit();
        }
        return preferencesChanged;
    }

    public void performGesture(int action, int index) {
        switch (action) {
            case 0:
                break;
            case 1:
                if (index > 2 && index != 13) {
                    showWorkspace(true);
                } else {
                    onClickAllAppsButton(null);
                }
                break;
            case 2:
                onClickHotseatButton(null);
                break;
            case 3:
                if (mStatusBarManager == null) {
                    mStatusBarManager = (StatusBarManager)
                            getApplicationContext().getSystemService(Context.STATUS_BAR_SERVICE);
                }
                if (!mStatusBarDisabled) {
                    mStatusBarManager.disable(0x10000000);
                    mStatusBarDisabled = true;
                } else {
                    mStatusBarManager.disable(0x00000000);
                    mStatusBarDisabled = false;
                }
                break;
            case 4:
                try {
                    Object service  = getSystemService("statusbar");
                    Class<?> statusbarManager = Class.forName("android.app.StatusBarManager");
                    Method expand = statusbarManager.getMethod("expand");
                    expand.invoke(service);
                } catch (Exception e) {
                }
                break;
            case 5:
                Intent preferences = new Intent().setClass(this, Preferences.class);
                preferences.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(preferences);
                break;
            case 6:
                SharedPreferences prefs =
                        getSharedPreferences(PreferencesProvider.PREFERENCES_KEY, Context.MODE_PRIVATE);
                String shortcutUri = new String();
                switch (index) {
                    case 0:
                        shortcutUri = prefs.getString("hdt_application", "");
                        break;
                    case 1:
                        shortcutUri = prefs.getString("hsu_application", "");
                        break;
                    case 2:
                        shortcutUri = prefs.getString("hsd_application", "");
                        break;
                    case 3:
                        shortcutUri = prefs.getString("dsu_application", "");
                        break;
                    case 4:
                        shortcutUri = prefs.getString("dsd_application", "");
                        break;
                    case 13:
                        shortcutUri = prefs.getString("app_bar_longclick_application", "");
                        break;
                }
                try {
                    Intent launchIntent = Intent.parseUri(shortcutUri, 0);
                    launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launchIntent);
                } catch (Exception e) {
                }
                break;
            case 7:
                if (mAllAppsBar.getVisibility() == View.VISIBLE) {
                    hideAllAppsBar(true);
                    hideSearchBar(true);
                    mShowAllAppsBar = false;
                    mShowSearchBar = false;
                } else {
                    mShowAllAppsBar = true;
                    mShowSearchBar = true;
                    showAllAppsBar(true);
                    showSearchBar(true);
                }
                break;
            case 8:
                Intent rocketLauncher = new Intent().setClass(this, RocketLauncher.class);
                rocketLauncher.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
                startActivity(rocketLauncher);
                break;
        }
    }

    /**
     * Prints out out state for debugging.
     */
    public void dumpState() {
        Log.d(TAG, "BEGIN launcher2 dump state for launcher " + this);
        Log.d(TAG, "mSavedState=" + mSavedState);
        Log.d(TAG, "mWorkspaceLoading=" + mWorkspaceLoading);
        Log.d(TAG, "mRestoring=" + mRestoring);
        Log.d(TAG, "mWaitingForResult=" + mWaitingForResult);
        Log.d(TAG, "mSavedInstanceState=" + mSavedInstanceState);
        Log.d(TAG, "sFolders.size=" + sFolders.size());
        mModel.dumpState();

        if (mAppsCustomizeContent != null) {
            mAppsCustomizeContent.dumpState();
        }
        Log.d(TAG, "END launcher2 dump state");
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        super.dump(prefix, fd, writer, args);
        writer.println(" ");
        writer.println("Debug logs: ");
        for (int i = 0; i < sDumpLogs.size(); i++) {
            writer.println("  " + sDumpLogs.get(i));
        }
    }
}

interface LauncherTransitionable {
    // return true if the callee will take care of start the animation by itself
    boolean onLauncherTransitionStart(Launcher l, Animator animation, boolean toWorkspace);
    void onLauncherTransitionEnd(Launcher l, Animator animation, boolean toWorkspace);
}
