package org.mozilla.vrbrowser.ui.prompts;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSession.PromptDelegate.Choice;
import org.mozilla.vrbrowser.R;
import org.mozilla.vrbrowser.SessionStore;
import org.mozilla.vrbrowser.WidgetPlacement;
import org.mozilla.vrbrowser.audio.AudioEngine;
import org.mozilla.vrbrowser.ui.UIWidget;

import java.util.ArrayList;
import java.util.Arrays;

public class ChoicePromptWidget extends UIWidget implements GeckoSession.NavigationDelegate {

    public interface ChoicePromptDelegate {
        void onDismissed(String[] text);
    }

    private static String TAB_CHAR = "\u0009\u0009\u0009";

    private AudioEngine mAudio;
    private ListView mList;
    private Button mCloseButton;
    private Button mOkButton;
    private TextView mPromptTitle;
    private TextView mPromptMessage;
    private ChoiceWrapper[] mListItems;
    private ChoicePromptDelegate mPromptDelegate;
    private ChoiceAdapter mAdapter;
    private Drawable mRadioDrawable;

    public ChoicePromptWidget(Context aContext) {
        super(aContext);
        initialize(aContext);
    }

    public ChoicePromptWidget(Context aContext, AttributeSet aAttrs) {
        super(aContext, aAttrs);
        initialize(aContext);
    }

    public ChoicePromptWidget(Context aContext, AttributeSet aAttrs, int aDefStyle) {
        super(aContext, aAttrs, aDefStyle);
        initialize(aContext);
    }

    private void initialize(Context aContext) {
        inflate(aContext, R.layout.choice_prompt, this);

        mAudio = AudioEngine.fromContext(aContext);

        SessionStore.get().addNavigationListener(this);

        mList = findViewById(R.id.choiceslist);
        mList.setSoundEffectsEnabled(false);
        mList.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                mAdapter.notifyDataSetChanged();

                ChoiceWrapper selectedItem = mListItems[position];
                if (mList.getChoiceMode() == ListView.CHOICE_MODE_SINGLE) {
                    if (mPromptDelegate != null) {
                        mPromptDelegate.onDismissed(new String[]{selectedItem.getChoice().id});
                    }
                }
            }
        });

        mPromptTitle = findViewById(R.id.promptTitle);
        mPromptMessage = findViewById(R.id.promptMessage);

        mCloseButton = findViewById(R.id.closeButton);
        mCloseButton.setSoundEffectsEnabled(false);
        mCloseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                switch (mList.getChoiceMode()) {
                    case ListView.CHOICE_MODE_SINGLE:
                    case ListView.CHOICE_MODE_MULTIPLE: {
                        if (mPromptDelegate != null) {
                            mPromptDelegate.onDismissed(getDefaultChoices(mListItems));
                        }
                    }
                    break;
                }
            }
        });

        mOkButton = findViewById(R.id.okButton);
        mOkButton.setSoundEffectsEnabled(false);
        mOkButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mAudio != null) {
                    mAudio.playSound(AudioEngine.Sound.CLICK);
                }

                switch (mList.getChoiceMode()) {
                    case ListView.CHOICE_MODE_SINGLE:
                    case ListView.CHOICE_MODE_MULTIPLE: {
                        if (mPromptDelegate != null) {
                            int len = mList.getCount();
                            SparseBooleanArray selected = mList.getCheckedItemPositions();
                            ArrayList<String> selectedChoices = new ArrayList<>();
                            for (int i = 0; i < len; i++) {
                                if (selected.get(i)) {
                                    selectedChoices.add(mListItems[i].getChoice().id);
                                }
                            }
                            if (selectedChoices.size() > 0) {
                                mPromptDelegate.onDismissed(selectedChoices.toArray(new String[selectedChoices.size()]));

                            } else {
                                mPromptDelegate.onDismissed(getDefaultChoices(mListItems));
                            }
                        }
                    }
                    break;
                }
            }
        });

        mListItems = new ChoiceWrapper[]{};
    }

    @Override
    public void releaseWidget() {
        super.releaseWidget();

        SessionStore.get().removeNavigationListener(this);
    }

    @Override
    protected void onBackButton() {
        hide();

        if (mPromptDelegate != null) {
            mPromptDelegate.onDismissed(getDefaultChoices(mListItems));
        }
    }

    public void setChoices(Choice[] choices) {
        mListItems = getWrappedChoices(choices);
        mAdapter = new ChoiceAdapter(getContext(), R.layout.choice_prompt_item, mListItems);
        mList.setAdapter(mAdapter);
    }

    @NonNull
    private static ChoiceWrapper[] getWrappedChoices(Choice[] aChoices) {
        return getWrappedChoices(aChoices, 0);
    }

    @NonNull
    private static ChoiceWrapper[] getWrappedChoices(Choice[] aChoices, int aLevel) {
        ArrayList<ChoiceWrapper> flattenedChoicesList = new ArrayList<>();
        for (int i = 0; i < aChoices.length; i++) {
            flattenedChoicesList.add(new ChoiceWrapper(aChoices[i], aLevel));
            if (aChoices[i].items != null && aChoices[i].items.length > 0) {
                ChoiceWrapper[] childChoices = getWrappedChoices(aChoices[i].items, aLevel+1);
                flattenedChoicesList.addAll(Arrays.asList(childChoices));
            }
        }

        return flattenedChoicesList.toArray(new ChoiceWrapper[flattenedChoicesList.size()]);
    }

    @NonNull
    private static String[] getDefaultChoices(ChoiceWrapper[] aChoices) {
        ArrayList<String> defaultChoices = new ArrayList<>();
        for (int i = 0; i < aChoices.length; i++) {
            if (aChoices[i].getChoice().selected) {
                defaultChoices.add(aChoices[i].getChoice().id);
            }
        }

        return defaultChoices.toArray(new String[defaultChoices.size()]);
    }

    static class ChoiceWrapper {

        private int mLevel;
        private Choice mChoice;
        private StringBuilder mIndentString;
        private boolean isParent;

        public ChoiceWrapper(Choice choice, int level) {
            mChoice = choice;
            mLevel = level;
            mIndentString = new StringBuilder();
            for (int i = 0; i < level; i++) {
                mIndentString.append(TAB_CHAR);
            }
            isParent = mChoice.items != null && mChoice.items.length > 0;
        }

        public boolean isParent() {
            return isParent;
        }

        public int getLevel() {
            return mLevel;
        }

        public Choice getChoice() {
            return mChoice;
        }

        public String getIndent() {
            return mIndentString.toString();
        }
    }

    public void setDelegate(ChoicePromptDelegate delegate) {
        mPromptDelegate = delegate;
    }

    public void setTitle(String title) {
        if (title == null || title.isEmpty()) {
            mPromptTitle.setVisibility(View.GONE);

        } else {
            mPromptTitle.setText(title);
        }
    }

    public void setMessage(String message) {
        if (message == null || message.isEmpty()) {
            mPromptMessage.setVisibility(View.GONE);

        } else {
            mPromptMessage.setText(message);
        }
    }

    public void setMenuType(int type) {
        switch (type) {
            case Choice.CHOICE_TYPE_SINGLE:
            case Choice.CHOICE_TYPE_MENU: {
                mList.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
                mCloseButton.setVisibility(View.VISIBLE);
                mOkButton.setVisibility(View.GONE);
            }
            break;
            case Choice.CHOICE_TYPE_MULTIPLE: {
                mList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
                mCloseButton.setVisibility(View.VISIBLE);
                mOkButton.setVisibility(View.VISIBLE);
            }
            break;
        }
    }

    @Override
    protected void initializeWidgetPlacement(WidgetPlacement aPlacement) {
        aPlacement.visible = false;
        aPlacement.width =  WidgetPlacement.dpDimension(getContext(), R.dimen.choice_prompt_width);
        aPlacement.height = WidgetPlacement.dpDimension(getContext(), R.dimen.choice_prompt_height);
        aPlacement.parentAnchorX = 0.5f;
        aPlacement.parentAnchorY = 0.5f;
        aPlacement.anchorX = 0.5f;
        aPlacement.anchorY = 0.5f;
        aPlacement.translationZ = WidgetPlacement.unitFromMeters(getContext(), R.dimen.choice_prompt_world_z);
    }

    public class ChoiceAdapter extends ArrayAdapter<ChoiceWrapper> {

        private class ChoiceViewHolder {
            LinearLayout layout;
            RadioButton check;
            TextView label;
        }

        private LayoutInflater mInflater;

        public ChoiceAdapter(Context context, int resource, ChoiceWrapper[] choices) {
            super(context, resource, choices);

            mInflater = LayoutInflater.from(getContext());
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return !getItem(position).getChoice().disabled && !getItem(position).isParent();
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View listItem = convertView;

            ChoiceViewHolder choiceViewHolder;
            if(listItem == null) {
                listItem = mInflater.inflate(R.layout.choice_prompt_item, parent, false);

                choiceViewHolder = new ChoiceViewHolder();
                choiceViewHolder.layout = listItem.findViewById(R.id.choiceItemLayoutId);
                choiceViewHolder.check = listItem.findViewById(R.id.radioOption);
                choiceViewHolder.check.setFocusable(false);
                choiceViewHolder.check.setClickable(false);
                choiceViewHolder.label = listItem.findViewById(R.id.optionLabel);

                listItem.setTag(R.string.list_item_view_tag, choiceViewHolder);

            } else {
                choiceViewHolder = (ChoiceViewHolder) listItem.getTag(R.string.list_item_view_tag);
            }

            ChoiceWrapper currentChoice = getItem(position);

            choiceViewHolder.check.setVisibility(View.VISIBLE);

            choiceViewHolder.check.setEnabled(true);
            choiceViewHolder.label.setEnabled(true);
            choiceViewHolder.label.setTypeface(choiceViewHolder.check.getTypeface(), Typeface.NORMAL);
            if (currentChoice.isParent()) {
                choiceViewHolder.label.setTypeface(choiceViewHolder.check.getTypeface(), Typeface.BOLD);
                choiceViewHolder.check.setVisibility(View.GONE);
                choiceViewHolder.label.setEnabled(false);
            }
            choiceViewHolder.label.setText(currentChoice.getIndent() + currentChoice.getChoice().label);

            listItem.setEnabled(!currentChoice.getChoice().disabled);

            // Change color if selected
            if (mList.isItemChecked(position)) {
                choiceViewHolder.check.setChecked(true);
                listItem.setTag(R.string.list_item_checked_tag, true);

            } else {
                choiceViewHolder.check.setChecked(false);
                listItem.setTag(R.string.list_item_checked_tag, false);
            }

            choiceViewHolder.label.setTextColor(getContext().getColor(R.color.fog));
            if (currentChoice.getChoice().disabled) {
                choiceViewHolder.label.setTextColor(getContext().getColor(R.color.asphalt));
                choiceViewHolder.check.setEnabled(false);
                choiceViewHolder.label.setEnabled(false);
            }

            listItem.setOnHoverListener(mHoverListener);

            return listItem;
        }

        private OnHoverListener mHoverListener = new OnHoverListener() {
            @Override
            public boolean onHover(View view, MotionEvent motionEvent) {
                int ev = motionEvent.getActionMasked();
                switch (ev) {
                    case MotionEvent.ACTION_HOVER_ENTER:
                        view.setHovered(true);
                        view.setBackgroundResource(R.drawable.prompt_item_selected);
                        return true;
                    case MotionEvent.ACTION_HOVER_EXIT:
                        view.setHovered(false);
                        if (view.getTag(R.string.list_item_checked_tag) != null) {
                            if ((Boolean)view.getTag(R.string.list_item_checked_tag)) {
                                view.setBackgroundColor(getContext().getColor(R.color.void_color));

                            } else {
                                view.setBackgroundColor(getContext().getColor(R.color.void_color));
                            }
                        }
                        return true;
                }

                return false;
            }
        };

    }

    // NavigationDelegate

    @Override
    public void onLocationChange(GeckoSession session, String url) {
        if (mPromptDelegate != null) {
            mPromptDelegate.onDismissed(getDefaultChoices(mListItems));
        }
    }

    @Override
    public void onCanGoBack(GeckoSession session, boolean canGoBack) {

    }

    @Override
    public void onCanGoForward(GeckoSession session, boolean canGoForward) {

    }

    @Nullable
    @Override
    public GeckoResult<Boolean> onLoadRequest(@NonNull GeckoSession session, @NonNull String uri, int target, int flags) {
        if (mPromptDelegate != null) {
            mPromptDelegate.onDismissed(getDefaultChoices(mListItems));
        }
        return null;
    }

    @Nullable
    @Override
    public GeckoResult<GeckoSession> onNewSession(@NonNull GeckoSession session, @NonNull String uri) {
        if (mPromptDelegate != null) {
            mPromptDelegate.onDismissed(getDefaultChoices(mListItems));
        }

        return null;
    }

    @Override
    public GeckoResult<String> onLoadError(GeckoSession session, String uri, int category, int error) {
        return null;
    }

}
