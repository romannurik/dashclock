/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.dashclock.configuration;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.google.android.apps.dashclock.DashClockService;
import com.google.android.apps.dashclock.ExtensionManager;
import com.google.android.apps.dashclock.ExtensionSettingActivityProxy;
import com.google.android.apps.dashclock.Utils;
import com.google.android.apps.dashclock.api.host.ExtensionListing;
import com.google.android.apps.dashclock.ui.SwipeDismissListViewTouchListener;
import com.google.android.apps.dashclock.ui.UndoBarController;
import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;

import net.nurik.roman.dashclock.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fragment for allowing the user to configure active extensions, shown within a {@link
 * ConfigurationActivity}.
 */
public class ConfigureExtensionsFragment extends Fragment implements
        ExtensionManager.OnChangeListener,
        UndoBarController.UndoListener {

    private static final String SAVE_KEY_SELECTED_EXTENSIONS = "selected_extensions";

    private ExtensionManager mExtensionManager;

    private List<ComponentName> mSelectedExtensions = new ArrayList<>();
    private ExtensionListAdapter mSelectedExtensionsAdapter;

    private Map<ComponentName, ExtensionListing> mExtensionListings = new HashMap<>();
    private Map<ComponentName, BitmapDrawable> mExtensionIcons = new HashMap<>();
    private List<ComponentName> mAvailableExtensions = new ArrayList<>();
    private PopupMenu mAddExtensionPopupMenu;

    private BroadcastReceiver mPackageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            repopulateAvailableExtensions();
        }
    };

    private DragSortListView mListView;
    private SwipeDismissListViewTouchListener mSwipeDismissTouchListener;
    private UndoBarController mUndoBarController;
    private View mAddFabView;

    public ConfigureExtensionsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set up helper components
        mExtensionManager = ExtensionManager.getInstance(getActivity());
        mExtensionManager.addOnChangeListener(this);

        if (savedInstanceState == null) {
            // TODO: should load in the correct order
            mSelectedExtensions = mExtensionManager.getInternalActiveExtensionNames();
        } else {
            List<String> selected = savedInstanceState
                    .getStringArrayList(SAVE_KEY_SELECTED_EXTENSIONS);
            for (String s : selected) {
                mSelectedExtensions.add(ComponentName.unflattenFromString(s));
            }
        }

        mSelectedExtensionsAdapter = new ExtensionListAdapter();

        repopulateAvailableExtensions();

        IntentFilter packageChangeIntentFilter = new IntentFilter();
        packageChangeIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageChangeIntentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageChangeIntentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packageChangeIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageChangeIntentFilter.addDataScheme("package");
        getActivity().registerReceiver(mPackageChangedReceiver, packageChangeIntentFilter);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        ViewGroup rootView = (ViewGroup) inflater.inflate(
                R.layout.fragment_configure_extensions, container, false);

        mListView = (DragSortListView) rootView.findViewById(android.R.id.list);
        mListView.setAdapter(mSelectedExtensionsAdapter);
        mListView.setEmptyView(rootView.findViewById(android.R.id.empty));

        final ConfigurationDragSortController dragSortController =
                new ConfigurationDragSortController();
        mListView.setFloatViewManager(dragSortController);
        mListView.setDropListener(new DragSortListView.DropListener() {
            @Override
            public void drop(int from, int to) {
                ComponentName name = mSelectedExtensions.remove(from);
                mSelectedExtensions.add(to, name);
                mSelectedExtensionsAdapter.notifyDataSetChanged();
                mExtensionManager.setInternalActiveExtensions(mSelectedExtensions);
            }
        });
        mSwipeDismissTouchListener = new SwipeDismissListViewTouchListener(
                mListView,
                new SwipeDismissListViewTouchListener.DismissCallbacks() {
                    public boolean canDismiss(int position) {
                        return true;
                    }

                    public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                        showRemoveUndoBar(reverseSortedPositions);
                        for (int position : reverseSortedPositions) {
                            mSelectedExtensions.remove(position);
                        }
                        repopulateAvailableExtensions();
                        mSelectedExtensionsAdapter.notifyDataSetChanged();
                        mExtensionManager.setInternalActiveExtensions(mSelectedExtensions);
                    }
                });
        mListView.setOnScrollListener(mSwipeDismissTouchListener.makeScrollListener());
        mListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    mUndoBarController.hideUndoBar(false);
                }

                return dragSortController.onTouch(view, motionEvent)
                        || (!dragSortController.isDragging()
                        && mSwipeDismissTouchListener.onTouch(view, motionEvent));

            }
        });

        mListView.setItemsCanFocus(true);

        mAddFabView = rootView.findViewById(R.id.add_extension_fab);
        mAddFabView.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        showAddExtensionMenu(view);
                    }
                });
        showHideAddFabView(false);

        mUndoBarController = new UndoBarController(rootView.findViewById(R.id.undobar), this);
        mUndoBarController.onRestoreInstanceState(savedInstanceState);

        return rootView;
    }

    @Override
    public void onPause() {
        super.onPause();
        mExtensionManager.setInternalActiveExtensions(mSelectedExtensions);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unregisterReceiver(mPackageChangedReceiver);
        mExtensionManager.removeOnChangeListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        ArrayList<String> selectedExtensions = new ArrayList<>();
        for (ComponentName cn : mSelectedExtensions) {
            selectedExtensions.add(cn.flattenToString());
        }
        outState.putStringArrayList(SAVE_KEY_SELECTED_EXTENSIONS, selectedExtensions);
        if (mUndoBarController != null) {
            mUndoBarController.onSaveInstanceState(outState);
        }
    }

    private void repopulateAvailableExtensions() {
        Set<ComponentName> selectedExtensions = new HashSet<>();
        selectedExtensions.addAll(mSelectedExtensions);
        boolean selectedExtensionsDirty = false;

        mExtensionListings.clear();
        mAvailableExtensions.clear();

        Resources res = getResources();

        for (ExtensionListing listing : mExtensionManager.getAvailableExtensions()) {
            ComponentName extension = listing.componentName();
            mExtensionListings.put(listing.componentName(), listing);
            ExtensionListing previousListing = mExtensionListings.put(extension, listing);

            if (listing.icon() != 0) {
                Bitmap icon = Utils.loadExtensionIcon(getActivity(), extension,
                        listing.icon(), null, res.getColor(R.color.extension_item_color));
                mExtensionIcons.put(extension, new BitmapDrawable(res, icon));
            }

            if (selectedExtensions.contains(listing.componentName())) {
                if (previousListing == null) {
                    // Extension was newly detected
                    selectedExtensionsDirty = true;
                }
                if (!listing.compatible()) {
                    // If the extension is selected and its protocol isn't supported,
                    // then make it available but remove it from the selection.
                    mSelectedExtensions.remove(listing.componentName());
                    selectedExtensionsDirty = true;
                } else {
                    // If it's selected and supported, don't add it to the list of available
                    // extensions.
                    continue;
                }
            }

            mAvailableExtensions.add(listing.componentName());
        }

        Collections.sort(mAvailableExtensions, new Comparator<ComponentName>() {
            @Override
            public int compare(ComponentName cn1, ComponentName cn2) {
                ExtensionListing listing1 = mExtensionListings.get(cn1);
                ExtensionListing listing2 = mExtensionListings.get(cn2);
                return listing1.title().compareToIgnoreCase(listing2.title());
            }
        });

        showHideAddFabView(true);

        if (selectedExtensionsDirty && mSelectedExtensionsAdapter != null) {
            mSelectedExtensionsAdapter.notifyDataSetChanged();
            mExtensionManager.setInternalActiveExtensions(mSelectedExtensions);
        }

        if (mAddExtensionPopupMenu != null) {
            mAddExtensionPopupMenu.dismiss();
            mAddExtensionPopupMenu = null;
        }
    }

    private void showHideAddFabView(boolean animate) {
        if (mAddFabView == null) {
            return;
        }

        final boolean show = mAvailableExtensions.size() > 0;
        Boolean lastValue = (Boolean) mAddFabView.getTag();
        if (lastValue == null || lastValue != show) {
            if (show) {
                mAddFabView.setVisibility(View.VISIBLE);
            }
            mAddFabView.animate()
                    .setDuration(animate ? 200 : 0)
                    .scaleX(show ? 1 : 0)
                    .scaleY(show ? 1 : 0)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (!show) {
                                mAddFabView.setVisibility(View.GONE);
                            }
                        }
                    });
        }
    }

    private void showAddExtensionMenu(View anchorView) {
        if (mAddExtensionPopupMenu != null) {
            mAddExtensionPopupMenu.dismiss();
        }

        mAddExtensionPopupMenu = new PopupMenu(getActivity(), anchorView);

        for (int i = 0; i < mAvailableExtensions.size(); i++) {
            ComponentName cn = mAvailableExtensions.get(i);
            ExtensionListing listing = mExtensionListings.get(cn);
            String label = (listing == null) ? null : listing.title();
            if (TextUtils.isEmpty(label)) {
                label = cn.flattenToShortString();
            }

            if (listing != null && !listing.compatible()) {
                label = getString(R.string.incompatible_extension_menu_template, label);
            }

            mAddExtensionPopupMenu.getMenu().add(Menu.NONE, i, Menu.NONE, label);
        }
        mAddExtensionPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                mAddExtensionPopupMenu.dismiss();
                mAddExtensionPopupMenu = null;

                ComponentName cn = mAvailableExtensions.get(menuItem.getItemId());
                ExtensionListing extensionListing = mExtensionListings.get(cn);
                if (extensionListing == null || !extensionListing.compatible()) {
                    String title = cn.getShortClassName();
                    if (extensionListing != null) {
                        title = extensionListing.title();
                    }
                    CantAddExtensionDialog
                            .createInstance(cn.getPackageName(), title)
                            .show(getFragmentManager(), "cantaddextension");
                    return true;
                }

                // Item id == position for this popup menu.
                mSelectedExtensions.add(cn);
                repopulateAvailableExtensions();
                mSelectedExtensionsAdapter.notifyDataSetChanged();
                mListView.smoothScrollToPosition(mSelectedExtensionsAdapter.getCount() - 1);
                return true;
            }
        });
        mAddExtensionPopupMenu.show();
    }

    private class ConfigurationDragSortController extends DragSortController {
        private int mPos;
        private boolean mDragging;

        public ConfigurationDragSortController() {
            super(ConfigureExtensionsFragment.this.mListView, R.id.drag_handle,
                    DragSortController.ON_DOWN, 0);
            setRemoveEnabled(false);
            mDragging = false;
        }

        public boolean isDragging() {
            return mDragging;
        }

        @Override
        public boolean startDrag(int position, int deltaX, int deltaY) {
            mDragging = super.startDrag(position, deltaX, deltaY);
            return mDragging;
        }

        @Override
        public boolean onTouch(View v, MotionEvent ev) {
            boolean ret = super.onTouch(v, ev);
            switch (ev.getAction()) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mDragging = false;
                    break;
            }
            return ret;
        }

        @Override
        public View onCreateFloatView(int position) {
            Vibrator v = (Vibrator) ConfigureExtensionsFragment.this.mListView
                    .getContext().getSystemService(Context.VIBRATOR_SERVICE);
            v.vibrate(10);
            mPos = position;
            return mSelectedExtensionsAdapter.getView(position, null, mListView);
        }

        @Override
        public void onDestroyFloatView(View floatView) {
            //do nothing; block super from crashing
        }
    }

    @Override
    public void onExtensionsChanged(ComponentName sourceExtension) {
        repopulateAvailableExtensions();
    }

    private void showRemoveUndoBar(int[] reverseSortedPositions) {
        String undoString;
        if (reverseSortedPositions.length == 1) {
            ExtensionListing listing = mExtensionListings.get(
                    mSelectedExtensions.get(reverseSortedPositions[0]));
            undoString = getString(R.string.extension_removed_template,
                    (listing != null) ? listing.title() : "??");
        } else {
            undoString = getString(R.string.extensions_removed_template,
                    reverseSortedPositions.length);
        }

        ComponentName[] extensions = new ComponentName[reverseSortedPositions.length];
        for (int i = 0; i < reverseSortedPositions.length; i++) {
            extensions[i] = mSelectedExtensions.get(reverseSortedPositions[i]);
        }

        Bundle undoBundle = new Bundle();
        undoBundle.putIntArray("positions", reverseSortedPositions);
        undoBundle.putParcelableArray("extensions", extensions);
        mUndoBarController.showUndoBar(
                false,
                undoString,
                undoBundle);
    }

    @Override
    public void onUndo(Bundle token) {
        if (token == null) {
            return;
        }

        // Perform the undo
        int[] reverseSortedPositions = token.getIntArray("positions");
        ComponentName[] extensions = (ComponentName[]) token.getParcelableArray("extensions");

        for (int i = 0; i < reverseSortedPositions.length; i++) {
            mSelectedExtensions.add(reverseSortedPositions[i], extensions[i]);
        }

        repopulateAvailableExtensions();
        mSelectedExtensionsAdapter.notifyDataSetChanged();
    }

    public class ExtensionListAdapter extends BaseAdapter {
        private static final int VIEW_TYPE_ITEM = 0;

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getCount() {
            return mSelectedExtensions.size();
        }

        @Override
        public int getItemViewType(int position) {
            return VIEW_TYPE_ITEM;
        }

        @Override
        public Object getItem(int position) {
            return mSelectedExtensions.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mSelectedExtensions.get(position).hashCode();
        }

        @Override
        public boolean isEnabled(int position) {
            return false;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            switch (getItemViewType(position)) {
                case VIEW_TYPE_ITEM: {
                    ComponentName cn = (ComponentName) getItem(position);
                    if (convertView == null) {
                        convertView = getActivity().getLayoutInflater()
                                .inflate(R.layout.list_item_extension, parent, false);
                    }

                    TextView titleView = (TextView) convertView.findViewById(android.R.id.text1);
                    TextView descriptionView = (TextView) convertView
                            .findViewById(android.R.id.text2);
                    ImageView iconView = (ImageView) convertView.findViewById(android.R.id.icon1);
                    View overflowButton = convertView.findViewById(R.id.overflow_button);

                    final ExtensionListing listing = mExtensionListings.get(cn);
                    if (listing == null || TextUtils.isEmpty(listing.title())) {
                        iconView.setImageBitmap(null);
                        titleView.setText(cn.flattenToShortString());
                        descriptionView.setVisibility(View.GONE);
                        overflowButton.setVisibility(View.GONE);
                    } else {
                        iconView.setImageDrawable(mExtensionIcons.get(listing.componentName()));
                        titleView.setText(listing.title());
                        descriptionView.setVisibility(
                                TextUtils.isEmpty(listing.description()) ? View.GONE : View.VISIBLE);
                        descriptionView.setText(listing.description());
                        overflowButton.setVisibility(View.VISIBLE);
                        overflowButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                mUndoBarController.hideUndoBar(false);
                                PopupMenu menu = new PopupMenu(view.getContext(), view);
                                menu.inflate(R.menu.configure_item_overflow);
                                if (listing.settingsActivity() == null) {
                                    menu.getMenu().findItem(R.id.action_settings).setVisible(false);
                                }
                                menu.setOnMenuItemClickListener(
                                        new OverflowItemClickListener(position));
                                menu.show();
                            }
                        });
                    }
                    return convertView;
                }
            }

            return null;
        }

        private class OverflowItemClickListener implements PopupMenu.OnMenuItemClickListener {
            private int mPosition;

            public OverflowItemClickListener(int position) {
                mPosition = position;
            }

            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (mPosition > getCount()) {
                    return false;
                }

                switch (item.getItemId()) {
                    case R.id.action_remove:
                        mSwipeDismissTouchListener.dismiss(mPosition);
                        return true;

                    case R.id.action_settings:
                        ComponentName cn = (ComponentName) getItem(mPosition);
                        ExtensionListing listing = mExtensionListings.get(cn);
                        if (listing == null) {
                            return false;
                        }

                        showExtensionSettings(getActivity(), listing);
                        return true;
                }
                return false;
            }
        }
    }

    public void showExtensionSettings(Context context, ExtensionListing listing){
        if (listing.settingsActivity() == null) {
            // Nothing to show
            return;
        }

        // Start the proxy activity
        Intent i = new Intent(context, ExtensionSettingActivityProxy.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.putExtra(DashClockService.EXTRA_COMPONENT_NAME, listing.componentName().flattenToString());
        i.putExtra(ExtensionSettingActivityProxy.EXTRA_SETTINGS_ACTIVITY,
                listing.settingsActivity().flattenToString());
        startActivity(i);
    }

    public static class CantAddExtensionDialog extends DialogFragment {
        private static final String ARG_EXTENSION_TITLE = "title";
        private static final String ARG_EXTENSION_PACKAGE_NAME = "package_name";

        public static CantAddExtensionDialog createInstance(String extensionPackageName,
                String extensionTitle) {
            CantAddExtensionDialog fragment = new CantAddExtensionDialog();
            Bundle args = new Bundle();
            args.putString(ARG_EXTENSION_TITLE, extensionTitle);
            args.putString(ARG_EXTENSION_PACKAGE_NAME, extensionPackageName);
            fragment.setArguments(args);
            return fragment;
        }

        public CantAddExtensionDialog() {
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final String extensionTitle = getArguments().getString(ARG_EXTENSION_TITLE);
            final String extensionPackageName = getArguments().getString(ARG_EXTENSION_PACKAGE_NAME);
            return new AlertDialog.Builder(getActivity(), R.style.Theme_Dialog)
                    .setTitle(R.string.incompatible_extension_title)
                    .setMessage(Html.fromHtml(getString(
                            R.string.incompatible_extension_message_search_play_template,
                            extensionTitle)))
                    .setPositiveButton(R.string.search_play,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    Intent playIntent = new Intent(Intent.ACTION_VIEW);
                                    playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    playIntent.setData(Uri.parse(
                                            "https://play.google.com/store/apps/details?id="
                                                    + extensionPackageName));
                                    try {
                                        startActivity(playIntent);
                                    } catch (ActivityNotFoundException ignored) {
                                    }

                                    dialog.dismiss();
                                }
                            }
                    )
                    .setNegativeButton(R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    dialog.dismiss();
                                }
                            }
                    )
                    .create();
        }
    }
}
