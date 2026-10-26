package org.telegram.messenger.devgram.player.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.devgram.player.PlayerColors;
import java.util.ArrayList;
import java.util.Locale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.GenericProvider;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
@SuppressLint({"ViewConstructor"})
public class PlayerQueueView extends FrameLayout {
    private final Adapter adapter;
    private final ExpressiveStateDrawable addBackground;
    private final ImageView addButton;
    private final ExpressiveStateDrawable clearBackground;
    private final ImageView clearButton;
    private PlayerColors colors;
    private final int currentAccount;
    private Delegate delegate;
    private final TextView emptyView;
    private final GradientDrawable fieldBackground;
    private final LinearLayout header;
    private ItemTouchHelper itemTouchHelper;
    private final LinearLayoutManager layoutManager;
    private final RecyclerListView listView;
    private ArrayList<MessageObject> playlist;
    private final ExpressiveStateDrawable reverseBackground;
    private final ImageView reverseButton;
    private final EditTextBoldCursor searchEditText;
    private final FrameLayout searchField;
    private final ImageView searchIcon;
    private String searchQuery;
    private ArrayList<MessageObject> searchResult;
    private Runnable searchRunnable;
    private boolean searchWas;
    private int selectorColor;
    private int sideInset;

    public interface Delegate {
        MessagesController.SavedMusicList getSavedMusicList();

        boolean isMyList();

        boolean isNoForwards();

        void onAddToList();

        void onReverseOrder();

        void onSearchFocus(boolean z);

        void onShowOptions(PlayerQueueCell playerQueueCell, MessageObject messageObject);
    }

    @SuppressLint({"ClickableViewAccessibility"})
    public PlayerQueueView(Context context, int i) {
        super(context);
        this.fieldBackground = new GradientDrawable();
        this.sideInset = AndroidUtilities.dp(20.0f);
        this.playlist = new ArrayList<>();
        this.searchResult = new ArrayList<>();
        this.currentAccount = i;
        LinearLayout linearLayout = new LinearLayout(context);
        this.header = linearLayout;
        linearLayout.setOrientation(0);
        addView(linearLayout, LayoutHelper.createFrame(-1, 40, 48));
        FrameLayout frameLayout = new FrameLayout(context);
        this.searchField = frameLayout;
        linearLayout.addView(frameLayout, LayoutHelper.createLinear(0, 40, 1.0f));
        ImageView imageView = new ImageView(context);
        this.searchIcon = imageView;
        ImageView.ScaleType scaleType = ImageView.ScaleType.CENTER;
        imageView.setScaleType(scaleType);
        imageView.setImageResource(R.drawable.outline_header_search);
        frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, 19));
        EditTextBoldCursor editTextBoldCursor = new EditTextBoldCursor(context);
        this.searchEditText = editTextBoldCursor;
        editTextBoldCursor.setTextSize(1, 15.0f);
        editTextBoldCursor.setTypeface(AndroidUtilities.regular());
        editTextBoldCursor.setBackground(null);
        editTextBoldCursor.setSingleLine(true);
        editTextBoldCursor.setImeOptions(268435459);
        editTextBoldCursor.setHint(LocaleController.getString(R.string.PlayerQueueSearch));
        editTextBoldCursor.setPadding(0, 0, 0, 0);
        editTextBoldCursor.setCursorSize(AndroidUtilities.dp(18.0f));
        editTextBoldCursor.setCursorWidth(1.5f);
        editTextBoldCursor.setGravity(19);
        frameLayout.addView(editTextBoldCursor, LayoutHelper.createFrame(-1, 40.0f, 19, 40.0f, 0.0f, 40.0f, 0.0f));
        editTextBoldCursor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i2, int i3, int i4) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i2, int i3, int i4) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String string = editable.toString();
                PlayerQueueView.this.clearButton.setVisibility(string.isEmpty() ? 8 : 0);
                PlayerQueueView playerQueueView = PlayerQueueView.this;
                if (string.trim().isEmpty()) {
                    string = null;
                }
                playerQueueView.search(string);
            }
        });
        editTextBoldCursor.setOnFocusChangeListener((view, z) -> lambda_new_0(view, z));
        ImageView imageView2 = new ImageView(context);
        this.clearButton = imageView2;
        imageView2.setScaleType(scaleType);
        imageView2.setImageResource(R.drawable.ic_close_white);
        ExpressiveStateDrawable expressiveStateDrawable = new ExpressiveStateDrawable(10.0f);
        this.clearBackground = expressiveStateDrawable;
        imageView2.setBackground(expressiveStateDrawable);
        imageView2.setContentDescription(LocaleController.getString(R.string.ClearButton));
        imageView2.setVisibility(8);
        imageView2.setOnClickListener(view -> lambda_new_1(view));
        frameLayout.addView(imageView2, LayoutHelper.createFrame(32, 32.0f, 21, 0.0f, 0.0f, 4.0f, 0.0f));
        ImageView imageView3 = new ImageView(context);
        this.addButton = imageView3;
        imageView3.setScaleType(scaleType);
        imageView3.setImageResource(R.drawable.msg_add);
        ExpressiveStateDrawable selectedRadius = new ExpressiveStateDrawable(8.0f).setSelectedRadius(12.0f);
        this.addBackground = selectedRadius;
        imageView3.setBackground(selectedRadius);
        imageView3.setContentDescription(LocaleController.getString(R.string.PlayerQueueAdd));
        imageView3.setOnClickListener(view -> lambda_new_2(view));
        imageView3.setVisibility(8);
        linearLayout.addView(imageView3, LayoutHelper.createLinear(40, 40, 16, 8, 0, 0, 0));
        ImageView imageView4 = new ImageView(context);
        this.reverseButton = imageView4;
        imageView4.setScaleType(scaleType);
        imageView4.setImageResource(R.drawable.player_new_order);
        ExpressiveStateDrawable selectedRadius2 = new ExpressiveStateDrawable(8.0f).setSelectedRadius(12.0f);
        this.reverseBackground = selectedRadius2;
        imageView4.setBackground(selectedRadius2);
        imageView4.setContentDescription(LocaleController.getString(R.string.ReverseOrder));
        imageView4.setOnClickListener(view -> lambda_new_3(view));
        linearLayout.addView(imageView4, LayoutHelper.createLinear(40, 40, 16, 8, 0, 0, 0));
        RecyclerListView recyclerListView = new RecyclerListView(context);
        this.listView = recyclerListView;
        recyclerListView.setVerticalScrollBarEnabled(false);
        recyclerListView.setVerticalFadingEdgeEnabled(true);
        recyclerListView.setFadingEdgeLength(AndroidUtilities.dp(16.0f));
        recyclerListView.setItemSelectorColorProvider(obj -> lambda_new_4((Integer) obj));
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, 1, false);
        this.layoutManager = linearLayoutManager;
        recyclerListView.setLayoutManager(linearLayoutManager);
        Adapter adapter = new Adapter(context);
        this.adapter = adapter;
        recyclerListView.setAdapter(adapter);
        recyclerListView.setOnItemClickListener((view, i2) -> PlayerQueueView.$r8$lambda$8epJuLSq3Ryz5mxAjilhofUP_Tc(view, i2));
        recyclerListView.setOnItemLongClickListener((view, i2) -> lambda_new_6(view, i2));
        recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int i2) {
                if (i2 == 1) {
                    AndroidUtilities.hideKeyboard(PlayerQueueView.this.searchEditText);
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int i2, int i3) {
                if (PlayerQueueView.this.searchWas) {
                    return;
                }
                int iFindFirstVisibleItemPosition = PlayerQueueView.this.layoutManager.findFirstVisibleItemPosition();
                int iFindLastVisibleItemPosition = PlayerQueueView.this.layoutManager.findLastVisibleItemPosition();
                int itemCount = PlayerQueueView.this.adapter.getItemCount();
                if (iFindFirstVisibleItemPosition == -1) {
                    return;
                }
                if (SharedConfig.playOrderReversed) {
                    if (iFindFirstVisibleItemPosition < 10) {
                        MediaController.getInstance().loadMoreMusic();
                    }
                } else if (iFindLastVisibleItemPosition > itemCount - 10) {
                    MediaController.getInstance().loadMoreMusic();
                }
            }
        });
        addView(recyclerListView, LayoutHelper.createFrame(-1, -1.0f, 48, 0.0f, 48.0f, 0.0f, 0.0f));
        TextView textView = new TextView(context);
        this.emptyView = textView;
        textView.setTextSize(1, 14.0f);
        textView.setTypeface(AndroidUtilities.regular());
        textView.setGravity(17);
        textView.setVisibility(8);
        addView(textView, LayoutHelper.createFrame(-1, -2.0f, 48, 0.0f, 48.0f, 0.0f, 0.0f));
        applySideInset();
    }
    public /* synthetic */ void lambda_new_0(View view, boolean z) {
        Delegate delegate = this.delegate;
        if (delegate != null) {
            delegate.onSearchFocus(z);
        }
    }
    public /* synthetic */ void lambda_new_1(View view) {
        this.searchEditText.setText("");
    }
    public /* synthetic */ void lambda_new_2(View view) {
        Delegate delegate = this.delegate;
        if (delegate != null) {
            delegate.onAddToList();
        }
    }
    public /* synthetic */ void lambda_new_3(View view) {
        Delegate delegate = this.delegate;
        if (delegate != null) {
            delegate.onReverseOrder();
        }
    }
    public /* synthetic */ Integer lambda_new_4(Integer num) {
        return Integer.valueOf(this.selectorColor);
    }

    public static /* synthetic */ void $r8$lambda$8epJuLSq3Ryz5mxAjilhofUP_Tc(View view, int i) {
        if (view instanceof PlayerQueueCell) {
            ((PlayerQueueCell) view).didPressedButton();
        }
    }
    public /* synthetic */ boolean lambda_new_6(View view, int i) {
        Delegate delegate;
        PlayerQueueCell playerQueueCell;
        MessageObject messageObject;
        if (!(view instanceof PlayerQueueCell) || (delegate = this.delegate) == null || delegate.isMyList() || (messageObject = (playerQueueCell = (PlayerQueueCell) view).getMessageObject()) == null || messageObject.getId() <= 0 || messageObject.isVoice()) {
            return false;
        }
        this.delegate.onShowOptions(playerQueueCell, messageObject);
        return true;
    }

    public void setDelegate(Delegate delegate) {
        this.delegate = delegate;
        setupReorder();
    }

    private void applySideInset() {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) this.header.getLayoutParams();
        int i = this.sideInset;
        layoutParams.leftMargin = i;
        layoutParams.rightMargin = i;
        this.header.setLayoutParams(layoutParams);
        this.emptyView.setPadding(this.sideInset, AndroidUtilities.dp(14.0f), this.sideInset, AndroidUtilities.dp(14.0f));
        int childCount = this.listView.getChildCount();
        for (int i2 = 0; i2 < childCount; i2++) {
            View childAt = this.listView.getChildAt(i2);
            if (childAt instanceof PlayerQueueCell) {
                ((PlayerQueueCell) childAt).setSideInset(this.sideInset);
            }
        }
    }

    private void setupReorder() {
        ItemTouchHelper itemTouchHelper = this.itemTouchHelper;
        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(null);
            this.itemTouchHelper = null;
        }
        Delegate delegate = this.delegate;
        if (delegate == null || !delegate.isMyList()) {
            this.addButton.setVisibility(8);
            return;
        }
        this.addButton.setVisibility(0);
        ItemTouchHelper itemTouchHelper2 = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int i) {
            }

            @Override
            public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                if (PlayerQueueView.this.searchWas) {
                    return 0;
                }
                return ItemTouchHelper.Callback.makeMovementFlags(3, 0);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder viewHolder2) {
                MessagesController.SavedMusicList savedMusicList = PlayerQueueView.this.delegate != null ? PlayerQueueView.this.delegate.getSavedMusicList() : null;
                if (savedMusicList == null) {
                    return false;
                }
                int adapterPosition = viewHolder.getAdapterPosition();
                int adapterPosition2 = viewHolder2.getAdapterPosition();
                if (adapterPosition < 0 || adapterPosition2 < 0) {
                    return false;
                }
                savedMusicList.move(adapterPosition, adapterPosition2);
                ArrayList<MessageObject> playlist = MediaController.getInstance().getPlaylist();
                playlist.clear();
                playlist.addAll(savedMusicList.list);
                PlayerQueueView.this.adapter.notifyItemMoved(adapterPosition, adapterPosition2);
                return true;
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int i) {
                if (viewHolder != null) {
                    PlayerQueueView.this.listView.hideSelector(false);
                }
                if (i != 0) {
                    PlayerQueueView.this.listView.cancelClickRunnables(false);
                    if (viewHolder != null) {
                        View view = viewHolder.itemView;
                        if (view instanceof PlayerQueueCell) {
                            ((PlayerQueueCell) view).setLifted(true);
                        }
                    }
                }
                super.onSelectedChanged(viewHolder, i);
            }

            @Override
            public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                View view = viewHolder.itemView;
                if (view instanceof PlayerQueueCell) {
                    ((PlayerQueueCell) view).setLifted(false);
                }
            }
        });
        this.itemTouchHelper = itemTouchHelper2;
        itemTouchHelper2.attachToRecyclerView(this.listView);
    }

    public void setColors(PlayerColors playerColors) {
        this.colors = playerColors;
        this.selectorColor = ColorUtils.setAlphaComponent(playerColors.text, 20);
        this.fieldBackground.setCornerRadius(AndroidUtilities.dp(20.0f));
        this.fieldBackground.setColor(playerColors.surfaceContainer);
        Drawable background = this.searchField.getBackground();
        GradientDrawable gradientDrawable = this.fieldBackground;
        if (background != gradientDrawable) {
            this.searchField.setBackground(gradientDrawable);
        }
        ImageView imageView = this.searchIcon;
        int i = playerColors.textSecondary;
        PorterDuff.Mode mode = PorterDuff.Mode.SRC_IN;
        imageView.setColorFilter(new PorterDuffColorFilter(i, mode));
        this.clearButton.setColorFilter(new PorterDuffColorFilter(playerColors.textSecondary, mode));
        this.clearBackground.setColors(playerColors.textSecondary, 0);
        this.searchEditText.setTextColor(playerColors.text);
        this.searchEditText.setHintTextColor(playerColors.textSecondary);
        this.searchEditText.setCursorColor(playerColors.text);
        updateReverseColors();
        this.addBackground.setColors(playerColors.icon, playerColors.tonal);
        this.addButton.setColorFilter(new PorterDuffColorFilter(playerColors.icon, mode));
        this.emptyView.setTextColor(playerColors.textSecondary);
        this.listView.setGlowColor(playerColors.textSecondary);
        int childCount = this.listView.getChildCount();
        for (int i2 = 0; i2 < childCount; i2++) {
            View childAt = this.listView.getChildAt(i2);
            if (childAt instanceof PlayerQueueCell) {
                ((PlayerQueueCell) childAt).setColors(playerColors);
            }
        }
    }

    private void updateReverseColors() {
        if (this.colors == null) {
            return;
        }
        boolean zIsSelected = this.reverseBackground.isSelected();
        PlayerColors playerColors = this.colors;
        int i = zIsSelected ? playerColors.onTonal : playerColors.icon;
        this.reverseBackground.setColors(i, this.colors.tonal);
        this.reverseButton.setColorFilter(new PorterDuffColorFilter(i, PorterDuff.Mode.SRC_IN));
    }

    public void setReversed(boolean z, boolean z2) {
        this.reverseBackground.setSelected(z, z2);
        this.reverseButton.setSelected(z);
        updateReverseColors();
    }
    public boolean isDirectOrder() {
        Delegate delegate = this.delegate;
        return (delegate != null && delegate.getSavedMusicList() != null) != SharedConfig.playOrderReversed;
    }

    @SuppressLint({"NotifyDataSetChanged"})
    public void update() {
        this.playlist = MediaController.getInstance().getPlaylist();
        this.adapter.notifyDataSetChanged();
        updateEmpty();
    }

    public void updateCells() {
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        int childCount = this.listView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childAt = this.listView.getChildAt(i);
            if (childAt instanceof PlayerQueueCell) {
                PlayerQueueCell playerQueueCell = (PlayerQueueCell) childAt;
                MessageObject messageObject = playerQueueCell.getMessageObject();
                if (messageObject != null && (messageObject.isVoice() || messageObject.isMusic())) {
                    playerQueueCell.updateButtonState(false, true);
                }
                playerQueueCell.setCurrent(isCurrent(messageObject, playingMessageObject), true);
            }
        }
    }
    public static boolean isCurrent(MessageObject messageObject, MessageObject messageObject2) {
        if (messageObject == null || messageObject2 == null) {
            return false;
        }
        if (messageObject != messageObject2) {
            return messageObject.getId() == messageObject2.getId() && messageObject.getDialogId() == messageObject2.getDialogId();
        }
        return true;
    }

    public void scrollToCurrent(boolean z) {
        int iPositionOf;
        MessageObject playingMessageObject = MediaController.getInstance().getPlayingMessageObject();
        if (playingMessageObject != null && (iPositionOf = this.adapter.positionOf(playingMessageObject)) >= 0) {
            if (z) {
                this.listView.smoothScrollToPosition(iPositionOf);
            } else {
                this.layoutManager.scrollToPositionWithOffset(iPositionOf, AndroidUtilities.dp(56.0f) * 2);
            }
        }
    }

    public void clearSearch() {
        if (this.searchEditText.length() > 0) {
            this.searchEditText.setText("");
        }
        this.searchEditText.clearFocus();
        AndroidUtilities.hideKeyboard(this.searchEditText);
    }

    public boolean isSearchFocused() {
        return this.searchEditText.isFocused();
    }
    public void updateEmpty() {
        boolean z = this.searchWas && this.searchResult.isEmpty();
        this.emptyView.setVisibility(z ? 0 : 8);
        if (z) {
            this.emptyView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.NoAudioFoundPlayerInfo, this.searchQuery)));
        }
    }
    @SuppressLint({"NotifyDataSetChanged"})
    public void search(String str) {
        if (this.searchRunnable != null) {
            Utilities.searchQueue.cancelRunnable(this.searchRunnable);
            this.searchRunnable = null;
        }
        if (str == null) {
            this.searchWas = false;
            this.searchQuery = null;
            this.searchResult = new ArrayList<>();
            this.adapter.notifyDataSetChanged();
            updateEmpty();
            return;
        }
        AnonymousClass4 anonymousClass4 = new AnonymousClass4(str, new ArrayList(this.playlist));
        this.searchRunnable = anonymousClass4;
        Utilities.searchQueue.postRunnable(anonymousClass4, 300L);
    }
    public class AnonymousClass4 implements Runnable {
        final /* synthetic */ ArrayList val$copy;
        final /* synthetic */ String val$query;

        public AnonymousClass4(String str, ArrayList arrayList) {
            this.val$query = str;
            this.val$copy = arrayList;
        }

        @Override
        public void run() {
            String lowerCase = this.val$query.trim().toLowerCase(Locale.getDefault());
            String translitString = LocaleController.getInstance().getTranslitString(lowerCase);
            if (lowerCase.equals(translitString) || translitString == null || translitString.isEmpty()) {
                translitString = null;
            }
            int i = 0;
            String[] strArr = translitString != null ? new String[]{lowerCase, translitString} : new String[]{lowerCase};
            final ArrayList arrayList = new ArrayList();
            ArrayList arrayList2 = this.val$copy;
            int size = arrayList2.size();
            while (i < size) {
                Object obj = arrayList2.get(i);
                i++;
                MessageObject messageObject = (MessageObject) obj;
                if (PlayerQueueView.matches(messageObject, strArr)) {
                    arrayList.add(messageObject);
                }
            }
            final String str = this.val$query;
            AndroidUtilities.runOnUIThread(() -> lambda_run_0(this, str, arrayList));
        }
        public /* synthetic */ void lambda_run_0(Runnable runnable, String str, ArrayList arrayList) {
            if (PlayerQueueView.this.searchRunnable != runnable) {
                return;
            }
            PlayerQueueView.this.searchRunnable = null;
            PlayerQueueView.this.searchWas = true;
            PlayerQueueView.this.searchQuery = str;
            PlayerQueueView.this.searchResult = arrayList;
            PlayerQueueView.this.adapter.notifyDataSetChanged();
            PlayerQueueView.this.layoutManager.scrollToPosition(0);
            PlayerQueueView.this.updateEmpty();
        }
    }
    public static boolean matches(MessageObject messageObject, String[] strArr) {
        String str;
        for (String str2 : strArr) {
            String documentName = messageObject.getDocumentName();
            if (!TextUtils.isEmpty(documentName) && documentName.toLowerCase(Locale.getDefault()).contains(str2)) {
                return true;
            }
            TLRPC.Document document = messageObject.getDocument();
            if (document != null) {
                for (int i = 0; i < document.attributes.size(); i++) {
                    TLRPC.DocumentAttribute documentAttribute = document.attributes.get(i);
                    if (documentAttribute instanceof TLRPC.TL_documentAttributeAudio) {
                        String str3 = documentAttribute.performer;
                        if ((str3 == null || !str3.toLowerCase().contains(str2)) && ((str = documentAttribute.title) == null || !str.toLowerCase().contains(str2))) {
                            break;
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public class Adapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder viewHolder) {
            return true;
        }

        public Adapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            boolean z = PlayerQueueView.this.searchWas;
            PlayerQueueView playerQueueView = PlayerQueueView.this;
            return (z ? playerQueueView.searchResult : playerQueueView.playlist).size();
        }

        public int positionOf(MessageObject messageObject) {
            boolean z = PlayerQueueView.this.searchWas;
            PlayerQueueView playerQueueView = PlayerQueueView.this;
            if (z) {
                return playerQueueView.searchResult.indexOf(messageObject);
            }
            int iIndexOf = playerQueueView.playlist.indexOf(messageObject);
            if (iIndexOf < 0) {
                return -1;
            }
            return PlayerQueueView.this.isDirectOrder() ? iIndexOf : (PlayerQueueView.this.playlist.size() - iIndexOf) - 1;
        }

        public MessageObject getItem(int i) {
            if (PlayerQueueView.this.searchWas) {
                if (i < 0 || i >= PlayerQueueView.this.searchResult.size()) {
                    return null;
                }
                return (MessageObject) PlayerQueueView.this.searchResult.get(i);
            }
            if (i < 0 || i >= PlayerQueueView.this.playlist.size()) {
                return null;
            }
            boolean zIsDirectOrder = PlayerQueueView.this.isDirectOrder();
            PlayerQueueView playerQueueView = PlayerQueueView.this;
            return zIsDirectOrder ? (MessageObject) playerQueueView.playlist.get(i) : (MessageObject) playerQueueView.playlist.get((PlayerQueueView.this.playlist.size() - i) - 1);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            PlayerQueueCell playerQueueCell = new PlayerQueueCell(this.context, PlayerQueueView.this.currentAccount);
            playerQueueCell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerListView.Holder(playerQueueCell);
        }

        @Override
        @SuppressLint({"ClickableViewAccessibility"})
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            final PlayerQueueCell playerQueueCell = (PlayerQueueCell) viewHolder.itemView;
            final MessageObject item = getItem(i);
            if (item == null) {
                return;
            }
            item.setQuery(PlayerQueueView.this.searchQuery);
            boolean z = PlayerQueueView.this.delegate != null && PlayerQueueView.this.delegate.isMyList();
            boolean z2 = PlayerQueueView.this.delegate != null && PlayerQueueView.this.delegate.isNoForwards();
            boolean z3 = z && !PlayerQueueView.this.searchWas;
            View.OnTouchListener onTouchListener = z3 ? (view, motionEvent) -> lambda_onBindViewHolder_0(playerQueueCell, view, motionEvent) : null;
            boolean z4 = (z || z2 || item.isVoice() || item.getId() <= 0) ? false : true;
            playerQueueCell.setSideInset(PlayerQueueView.this.sideInset);
            if (PlayerQueueView.this.colors != null) {
                playerQueueCell.setColors(PlayerQueueView.this.colors);
            }
            playerQueueCell.setGlobalSearch(MediaController.getInstance().currentPlaylistIsGlobalSearch());
            playerQueueCell.setMessageObject(item, z3, z4 ? view -> lambda_onBindViewHolder_1(playerQueueCell, item, view) : null, onTouchListener);
            playerQueueCell.setCurrent(PlayerQueueView.isCurrent(item, MediaController.getInstance().getPlayingMessageObject()), false);
        }
        public /* synthetic */ boolean lambda_onBindViewHolder_0(PlayerQueueCell playerQueueCell, View view, MotionEvent motionEvent) {
            if (motionEvent.getAction() != 0 || PlayerQueueView.this.itemTouchHelper == null) {
                return false;
            }
            PlayerQueueView.this.itemTouchHelper.startDrag(PlayerQueueView.this.listView.getChildViewHolder(playerQueueCell));
            return false;
        }
        public /* synthetic */ void lambda_onBindViewHolder_1(PlayerQueueCell playerQueueCell, MessageObject messageObject, View view) {
            if (PlayerQueueView.this.delegate != null) {
                PlayerQueueView.this.delegate.onShowOptions(playerQueueCell, messageObject);
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder viewHolder) {
            View view = viewHolder.itemView;
            if (view instanceof PlayerQueueCell) {
                PlayerQueueCell playerQueueCell = (PlayerQueueCell) view;
                if (playerQueueCell.getMessageObject() != null) {
                    if (PlayerQueueView.this.colors != null) {
                        playerQueueCell.setColors(PlayerQueueView.this.colors);
                    }
                    playerQueueCell.updateButtonState(false, false);
                    playerQueueCell.setCurrent(PlayerQueueView.isCurrent(playerQueueCell.getMessageObject(), MediaController.getInstance().getPlayingMessageObject()), false);
                }
            }
        }
    }
}
