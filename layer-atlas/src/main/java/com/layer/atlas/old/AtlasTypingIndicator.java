/*
 * Copyright (c) 2015 Layer. All rights reserved.
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
package com.layer.atlas.old;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.layer.atlas.Participant;
import com.layer.atlas.ParticipantProvider;
import com.layer.atlas.R;
import com.layer.sdk.LayerClient;
import com.layer.sdk.listeners.LayerTypingIndicatorListener;
import com.layer.sdk.messaging.Conversation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AtlasTypingIndicator provides feedback about typists within a Conversation.  When initialized
 * and registered with a LayerClient as a LayerTypingIndicatorListener, AtlasTypingIndicator
 * maintains a set of typists for the given Conversation, providing callbacks when UI updates are
 * needed.  AtlasTypingIndicator can provide a default UI updater if desired.
 */
public class AtlasTypingIndicator extends FrameLayout implements LayerTypingIndicatorListener.Weak {
    private static final String TAG = AtlasTypingIndicator.class.getSimpleName();

    private LayerClient mLayerClient;
    private volatile Conversation mConversation;
    private final Set<String> mTypists = new HashSet<String>();
    private volatile ActivityListener mActivityListener;
    private volatile TypingIndicatorFactory mTypingIndicatorFactory;
    private volatile boolean mShowing = false;

    private volatile boolean mActive = false;

    public AtlasTypingIndicator(Context context) {
        super(context);
    }

    public AtlasTypingIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AtlasTypingIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Initializes this AtlasTypingIndicator.
     *
     * @return This AtlasTypingIndicator for chaining.
     */
    public AtlasTypingIndicator init(LayerClient layerClient) {
        if (layerClient == null) {
            throw new IllegalArgumentException("LayerClient cannot be null");
        }
        mLayerClient = layerClient;
        mLayerClient.registerTypingIndicator(this);
        return this;
    }

    /**
     * Sets the Conversation to listen for typing on.  If `null`, no typing will be listened to.
     *
     * @param conversation Conversation to listen for typing on
     * @return This AtlasTypingIndicator for chaining.
     */
    public AtlasTypingIndicator setConversation(Conversation conversation) {
        mConversation = conversation;
        return this;
    }

    public AtlasTypingIndicator setTypingIndicatorFactory(TypingIndicatorFactory typingIndicatorFactory) {
        mTypingIndicatorFactory = typingIndicatorFactory;
        removeAllViews();
        if (typingIndicatorFactory != null) addView(typingIndicatorFactory.onCreateView(getContext()));
        return this;
    }

    public AtlasTypingIndicator setActivityListener(ActivityListener activityListener) {
        mActivityListener = activityListener;
        return this;
    }

    /**
     * Clears the current list of typists and calls refresh().
     *
     * @return This AtlasTypingIndicator for chaining.
     */
    public AtlasTypingIndicator clear() {
        synchronized (mTypists) {
            mTypists.clear();
        }
        refresh();
        return this;
    }

    /**
     * Calls Callback.onBindView() with the current list of typists.
     *
     * @return This AtlasTypingIndicator for chaining.
     */
    private AtlasTypingIndicator refresh() {
        synchronized (mTypists) {
            if (mTypingIndicatorFactory != null) mTypingIndicatorFactory.onBindView(this, mTypists);
        }
        return this;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    public void onTypingIndicator(LayerClient layerClient, Conversation conversation, String userId, TypingIndicator typingIndicator) {
        Log.v(TAG, "onTypingIndicator: " + typingIndicator);
        if (mConversation != conversation) return;
        boolean empty;
        synchronized (mTypists) {
            if (typingIndicator == TypingIndicator.FINISHED) {
                mTypists.remove(userId);
            } else {
                mTypists.add(userId);
            }
            empty = mTypists.isEmpty();
        }

        if (empty && mActive) {
            mActive = false;
            if (mActivityListener != null) mActivityListener.onInactive(this);
        } else if (!empty && !mActive) {
            mActive = true;
            if (mActivityListener != null) mActivityListener.onActive(this);
        }

        refresh();
    }

    /**
     * AtlasTypingIndicator.Callback allows an external class to set indicator text, visibility,
     * etc. based on the current typists.
     */
    public interface TypingIndicatorFactory {
        View onCreateView(Context context);

        /**
         * Notifies the callback to typist updates.
         *
         * @param indicator     The AtlasTypingIndicator notifying
         * @param typingUserIds The set of currently-active typist user IDs
         */
        void onBindView(AtlasTypingIndicator indicator, Set<String> typingUserIds);
    }

    public interface ActivityListener {
        void onActive(AtlasTypingIndicator typingIndicator);

        void onInactive(AtlasTypingIndicator typingIndicator);
    }

    /**
     * Default Callback handler implementation.
     */
    public static class DefaultTypingIndicatorFactory implements TypingIndicatorFactory {
        private TextView mTextView;
        private final ParticipantProvider mParticipantProvider;

        public DefaultTypingIndicatorFactory(Context context, ParticipantProvider participantProvider) {
            if (participantProvider == null)
                throw new IllegalArgumentException("ParticipantProvider cannot be null");
            mParticipantProvider = participantProvider;
            mTextView = new TextView(context);
        }

        @Override
        public View onCreateView(Context context) {
            return mTextView;
        }

        @Override
        public void onBindView(AtlasTypingIndicator indicator, Set<String> typingUserIds) {
            List<Participant> typists = new ArrayList<Participant>(typingUserIds.size());
            for (String userId : typingUserIds) {
                Participant participant = mParticipantProvider.getParticipant(userId);
                if (participant != null) typists.add(participant);
            }

            if (typists.isEmpty()) return;

            String[] strings = indicator.getResources().getStringArray(R.array.atlas_typing_indicator);
            String string = strings[Math.min(strings.length - 1, typingUserIds.size())];
            String[] names = new String[typists.size()];
            int i = 0;
            for (Participant typist : typists) {
                names[i++] = typist.getName();
            }
            mTextView.setText(String.format(string, names));
        }
    }

    public static class SimpleBubbleTypingIndicatorFactory implements TypingIndicatorFactory {
        private static final int DOT_SIZE = 8;
        private static final int DOT_SPACE = 4;
        private static final int DOT_RES_ID = R.drawable.atlas_shape_circle_black;
        private static final float DOT_ON_ALPHA = 0.31f;
        private static final long ANIMATION_DURATION = 500;
        private static final long ANIMATION_OFFSET = ANIMATION_DURATION / 3;

        private View sDot1;
        private View sDot2;
        private View sDot3;

        @Override
        public View onCreateView(Context context) {
            LinearLayout l = new LinearLayout(context);
            float minHeight = context.getResources().getDimension(R.dimen.atlas_message_item_cell_min_height);
            l.setMinimumHeight(Math.round(minHeight));
            l.setGravity(Gravity.CENTER);
            l.setOrientation(LinearLayout.HORIZONTAL);
            l.setLayoutParams(new AtlasTypingIndicator.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            l.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.atlas_message_item_cell_them));

            ImageView v;
            LinearLayout.LayoutParams p;

            v = new ImageView(context);
            p = new LinearLayout.LayoutParams(DOT_SIZE, DOT_SIZE);
            p.setMargins(0, 0, DOT_SPACE, 0);
            v.setLayoutParams(p);
            v.setBackgroundDrawable(context.getResources().getDrawable(DOT_RES_ID));
            sDot1 = v;

            v = new ImageView(context);
            p = new LinearLayout.LayoutParams(DOT_SIZE, DOT_SIZE);
            p.setMargins(0, 0, DOT_SPACE, 0);
            v.setLayoutParams(p);
            v.setBackgroundDrawable(context.getResources().getDrawable(DOT_RES_ID));
            sDot2 = v;

            v = new ImageView(context);
            p = new LinearLayout.LayoutParams(DOT_SIZE, DOT_SIZE);
            v.setLayoutParams(p);
            v.setBackgroundDrawable(context.getResources().getDrawable(DOT_RES_ID));
            sDot3 = v;

            l.addView(sDot1);
            l.addView(sDot2);
            l.addView(sDot3);

            return l;
        }

        @Override
        public void onBindView(AtlasTypingIndicator indicator, Set<String> typingUserIds) {
            sDot1.setAlpha(DOT_ON_ALPHA);
            sDot2.setAlpha(DOT_ON_ALPHA);
            sDot3.setAlpha(DOT_ON_ALPHA);
            startAnimation(sDot1, 0);
            startAnimation(sDot2, ANIMATION_OFFSET);
            startAnimation(sDot3, ANIMATION_OFFSET + ANIMATION_OFFSET);
        }

//        @Override
//        public void onHide() {
//            sDot1.clearAnimation();
//            sDot2.clearAnimation();
//            sDot3.clearAnimation();
//            sDot1.setAlpha(0.0f);
//            sDot2.setAlpha(0.0f);
//            sDot3.setAlpha(0.0f);
//        }

        private void startAnimation(final View v, long offset) {
            final AlphaAnimation a1 = new AlphaAnimation(1.0f, 0.0f);
            a1.setDuration(ANIMATION_DURATION);
            a1.setStartOffset(offset);

            final AlphaAnimation a2 = new AlphaAnimation(0.0f, 1.0f);
            a2.setDuration(ANIMATION_DURATION);
            a2.setStartOffset(0);

            a1.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    a2.setStartOffset(0);
                    a2.reset();
                    v.startAnimation(a2);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });

            a2.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    a1.setStartOffset(0);
                    a1.reset();
                    v.startAnimation(a1);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });

            v.startAnimation(a1);
        }
    }

}
