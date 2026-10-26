package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;

import java.util.ArrayList;
import java.util.List;

public class ChatScrimPopupContainerLayout extends LinearLayout {

    private ReactionsContainerLayout reactionsLayout;
    private ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout;
    // нижних панелей может быть несколько (запрет пересылки, ошибка цены, эмодзи-паки…)
    private final List<View> bottomViews = new ArrayList<>();
    private int maxHeight;
    private float popupLayoutLeftOffset;
    private float progressToSwipeBack;
    private float bottomViewYOffset;
    private float expandSize;
    private float bottomViewReactionsOffset;
    private float lastReactionsTransitionProgress;
    private float currentPopupAlpha = 1f;

    public ChatScrimPopupContainerLayout(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateBottomViewPosition();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (maxHeight != 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (popupWindowLayout == null) {
            return;
        }
        boolean remeasure = false;
        if (reactionsLayout != null) {
            reactionsLayout.getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
            ((LayoutParams) reactionsLayout.getLayoutParams()).rightMargin = 0;
        }
        int maxWidth = reactionsLayout != null ? reactionsLayout.getMeasuredWidth() : 0;
        if (popupWindowLayout.getSwipeBack() != null && popupWindowLayout.getSwipeBack().getMeasuredWidth() > maxWidth) {
            maxWidth = popupWindowLayout.getSwipeBack().getMeasuredWidth();
        }
        if (popupWindowLayout.getMeasuredWidth() > maxWidth) {
            maxWidth = popupWindowLayout.getMeasuredWidth();
        }
        if (reactionsLayout != null && reactionsLayout.showCustomEmojiReaction()) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY);
        }
        View menuContainer = popupWindowLayout.getSwipeBack() != null ? popupWindowLayout.getSwipeBack().getChildAt(0) : popupWindowLayout.getChildAt(0);
        if (reactionsLayout != null) {
            reactionsLayout.measureHint();
            int reactionsLayoutTotalWidth = reactionsLayout.getTotalWidth();
            int maxReactionsLayoutWidth = menuContainer.getMeasuredWidth() + dp(16) + dp(16) + dp(36);
            int hintTextWidth = reactionsLayout.getHintTextWidth();
            if (hintTextWidth > maxReactionsLayoutWidth) {
                maxReactionsLayoutWidth = hintTextWidth;
            } else if (maxReactionsLayoutWidth > maxWidth) {
                maxReactionsLayoutWidth = maxWidth;
            }
            reactionsLayout.bigCircleOffset = dp(36);
            int newWidth;
            if (reactionsLayout.showCustomEmojiReaction()) {
                newWidth = reactionsLayoutTotalWidth;
                reactionsLayout.bigCircleOffset = Math.max(reactionsLayoutTotalWidth - menuContainer.getMeasuredWidth() - dp(36), dp(36));
            } else if (reactionsLayoutTotalWidth > maxReactionsLayoutWidth) {
                int maxFullCount = ((maxReactionsLayoutWidth - dp(16)) / dp(36)) + 1;
                newWidth = maxFullCount * dp(36) + dp(8);
                if (hintTextWidth + dp(24) > newWidth) {
                    newWidth = hintTextWidth + dp(24);
                }
                if (newWidth > reactionsLayoutTotalWidth || maxFullCount == reactionsLayout.getItemsCount()) {
                    newWidth = reactionsLayoutTotalWidth;
                }
            } else {
                newWidth = LayoutHelper.WRAP_CONTENT;
            }
            if (reactionsLayout.getLayoutParams().width != newWidth) {
                reactionsLayout.getLayoutParams().width = newWidth;
                remeasure = true;
            }
            if (reactionsLayout.getMeasuredWidth() != maxWidth || !reactionsLayout.showCustomEmojiReaction()) {
                int widthDiff = 0;
                if (popupWindowLayout.getSwipeBack() != null) {
                    widthDiff = popupWindowLayout.getSwipeBack().getMeasuredWidth() - popupWindowLayout.getSwipeBack().getChildAt(0).getMeasuredWidth();
                }
                if (reactionsLayout.getLayoutParams().width != LayoutHelper.WRAP_CONTENT && reactionsLayout.getLayoutParams().width + widthDiff > maxWidth) {
                    widthDiff = maxWidth - reactionsLayout.getLayoutParams().width + dp(8);
                }
                if (widthDiff < 0) {
                    widthDiff = 0;
                }
                if (((LayoutParams) reactionsLayout.getLayoutParams()).rightMargin != widthDiff) {
                    ((LayoutParams) reactionsLayout.getLayoutParams()).rightMargin = widthDiff;
                    remeasure = true;
                }
                popupLayoutLeftOffset = 0;
            } else {
                popupLayoutLeftOffset = (maxWidth - menuContainer.getMeasuredWidth()) * 0.25f;
                reactionsLayout.bigCircleOffset -= (int) popupLayoutLeftOffset;
                if (reactionsLayout.bigCircleOffset < dp(36)) {
                    popupLayoutLeftOffset = 0;
                    reactionsLayout.bigCircleOffset = dp(36);
                }
            }
        }

        // нижние панели выравниваем по самому попапу: берём его отступы, а при RTL
        // они уже зеркальные — раньше отступ справа был жёстко 36dp, и панели уезжали
        int menuWidth = menuContainer.getMeasuredWidth();
        int popupWidth = popupWindowLayout.getMeasuredWidth();
        int swipeBackDiff = popupWindowLayout.getSwipeBack() != null ? Math.max(0, popupWindowLayout.getSwipeBack().getMeasuredWidth() - menuWidth) : 0;
        LayoutParams popupParams = (LayoutParams) popupWindowLayout.getLayoutParams();
        for (View view : bottomViews) {
            if (view == null) continue;
            LayoutParams params = (LayoutParams) view.getLayoutParams();
            int width;
            if ((reactionsLayout != null && reactionsLayout.showCustomEmojiReaction()) || view.getTag(R.id.fit_width_tag) != null) {
                width = menuWidth + dp(16);
                if (popupWidth > 0 && width > popupWidth) {
                    width = popupWidth;
                }
            } else {
                width = LayoutHelper.MATCH_PARENT;
            }
            int left = popupParams.leftMargin;
            int right = popupParams.rightMargin + swipeBackDiff;
            if (params.width != width || params.leftMargin != left || params.rightMargin != right) {
                params.width = width;
                params.leftMargin = left;
                params.rightMargin = right;
                remeasure = true;
            }
            if (progressToSwipeBack > 0) {
                view.setAlpha(1f - progressToSwipeBack);
            }
        }
        updatePopupTranslation();
        if (remeasure) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private float reactionsVisibility() {
        return reactionsLayout == null || reactionsLayout.getItemsCount() <= 0 || reactionsLayout.getVisibility() != View.VISIBLE ? 1f : lastReactionsTransitionProgress;
    }

    private void updatePopupTranslation() {
        float x = (1f - progressToSwipeBack) * popupLayoutLeftOffset;
        popupWindowLayout.setTranslationX(x);
        float visibility = reactionsVisibility();
        for (View view : bottomViews) {
            if (view != null) {
                view.setTranslationX(x);
                view.setAlpha((1f - progressToSwipeBack) * visibility * currentPopupAlpha);
            }
        }
    }

    public void applyViewBottom(FrameLayout bottomView) {
        if (bottomView != null) {
            bottomViews.add(bottomView);
            updateBottomOffset();
        }
    }

    public void setReactionsLayout(ReactionsContainerLayout reactionsLayout) {
        this.reactionsLayout = reactionsLayout;
        if (reactionsLayout != null) {
            reactionsLayout.setChatScrimView(this);
        }
    }

    private void updateBottomOffset() {
        if (popupWindowLayout == null) return;
        bottomViewYOffset = popupWindowLayout.getVisibleHeight() - popupWindowLayout.getMeasuredHeight();
        updateBottomViewPosition();
    }

    public void setPopupWindowLayout(ActionBarPopupWindow.ActionBarPopupWindowLayout popupWindowLayout) {
        this.popupWindowLayout = popupWindowLayout;
        popupWindowLayout.setOnSizeChangedListener(this::updateBottomOffset);
        if (popupWindowLayout.getSwipeBack() != null) {
            popupWindowLayout.getSwipeBack().addOnSwipeBackProgressListener((layout, toProgress, progress) -> {
                float visibility = reactionsVisibility();
                for (View view : bottomViews) {
                    if (view != null) {
                        view.setAlpha((1f - progress) * visibility * currentPopupAlpha);
                    }
                }
                progressToSwipeBack = progress;
                updatePopupTranslation();
            });
        }
    }

    private void updateBottomViewPosition() {
        float visibility = reactionsVisibility();
        for (View view : bottomViews) {
            if (view == null) continue;
            if (visibility < 1f && view.getMeasuredHeight() > 0) {
                bottomViewReactionsOffset = -view.getMeasuredHeight() * (1f - visibility);
            } else {
                bottomViewReactionsOffset = 0;
            }
            float alpha = visibility;
            if (progressToSwipeBack > 0) {
                alpha *= 1f - progressToSwipeBack;
            }
            view.setAlpha(alpha * currentPopupAlpha);
            view.setTranslationY(bottomViewYOffset + expandSize + bottomViewReactionsOffset);
        }
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public void setExpandSize(float expandSize) {
        popupWindowLayout.setTranslationY(expandSize);
        this.expandSize = expandSize;
        updateBottomViewPosition();
    }

    public void setPopupAlpha(float alpha) {
        currentPopupAlpha = alpha;
        popupWindowLayout.setAlpha(alpha);
        for (View view : bottomViews) {
            if (view != null) {
                view.setAlpha(alpha);
            }
        }
    }

    public void setReactionsTransitionProgress(float v) {
        lastReactionsTransitionProgress = v;
        popupWindowLayout.setReactionsTransitionProgress(v);
        if (reactionsLayout == null || reactionsLayout.getItemsCount() <= 0) {
            v = 1f;
        }
        for (View view : bottomViews) {
            if (view == null) continue;
            if (progressToSwipeBack == 0) {
                view.setAlpha(v);
            }
            float scale = 0.5f + v * 0.5f;
            view.setPivotX(view.getMeasuredWidth());
            view.setPivotY(0);
            view.setScaleX(scale);
            view.setScaleY(scale);
        }
        updateBottomViewPosition();
    }
}
