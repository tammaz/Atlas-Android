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
package com.layer.atlas;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.layer.sdk.LayerClient;
import com.layer.sdk.listeners.LayerTypingIndicatorListener;
import com.layer.sdk.messaging.Conversation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AtlasTypingIndicator provides feedback about typists within a Conversation.  When initialized
 * and registered with a LayerClient as a LayerTypingIndicatorListener, AtlasTypingIndicator
 * maintains a set of typists for the given Conversation, providing callbacks when UI updates are
 * needed.  AtlasTypingIndicator can provide a default UI updater if desired.
 */
public class AtlasTypingIndicator extends FrameLayout implements LayerTypingIndicatorListener.Weak {
    private static final String TAG = AtlasTypingIndicator.class.getSimpleName();

    private final ConcurrentHashMap<String, TypingIndicator> mTypists = new ConcurrentHashMap<String, TypingIndicator>();

    private volatile Conversation mConversation;
    private volatile TypingActivityListener mActivityListener;
    private volatile TypingIndicatorFactory mTypingIndicatorFactory;
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
        layerClient.registerTypingIndicator(this);
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

    public AtlasTypingIndicator setTypingIndicatorFactory(TypingIndicatorFactory factory) {
        mTypingIndicatorFactory = factory;
        removeAllViews();
        if (factory != null) addView(factory.onCreateView(getContext()));
        return this;
    }

    public AtlasTypingIndicator setTypingActivityListener(TypingActivityListener listener) {
        mActivityListener = listener;
        return this;
    }

    /**
     * Clears the current list of typists and calls refresh().
     *
     * @return This AtlasTypingIndicator for chaining.
     */
    public AtlasTypingIndicator clear() {
        mTypists.clear();
        refresh();
        return this;
    }

    /**
     * Calls Callback.onBindView() with the current list of typists.
     *
     * @return This AtlasTypingIndicator for chaining.
     */
    private AtlasTypingIndicator refresh() {
        if (mTypingIndicatorFactory != null) mTypingIndicatorFactory.onBindView(this, mTypists);
        return this;
    }

    /**
     * Refresh when the typing indicator gets attached (e.g. scrolling into view).
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refresh();
    }

    @Override
    public void onTypingIndicator(LayerClient layerClient, Conversation conversation, String userId, TypingIndicator typingIndicator) {
        // Only monitor typing in this indicator's conversation.
        if (mConversation != conversation) return;

        // Notify ActivityListener to active/inactive typists.
        boolean empty;
        if (typingIndicator == TypingIndicator.FINISHED) {
            mTypists.remove(userId);
        } else {
            mTypists.put(userId, typingIndicator);
        }
        empty = mTypists.isEmpty();
        if (empty && mActive) {
            mActive = false;
            if (mActivityListener != null) mActivityListener.onTypingInactive(this);
        } else if (!empty && !mActive) {
            mActive = true;
            if (mActivityListener != null) mActivityListener.onTypingActive(this);
        }

        // Refresh the indicator view.
        refresh();
    }

    /**
     * TypingIndicatorFactory allows an external class to set indicator text, visibility,
     * etc. based on the current typists.
     */
    public interface TypingIndicatorFactory {
        /**
         * Returns the View for this typing indicator.
         *
         * @param context Context to create the View.
         * @return View to display as a typing indicator.
         */
        View onCreateView(Context context);

        /**
         * Notifies the callback to typist updates.
         *
         * @param indicator     The AtlasTypingIndicator notifying
         * @param typingUserIds The set of currently-active typist user IDs
         */
        void onBindView(AtlasTypingIndicator indicator, Map<String, TypingIndicator> typingUserIds);
    }

    /**
     * TypingActivityListener alerts a listener to active and inactive typing.  This is useful for
     * adding and removing a typing indicator view from a layout, for example.
     */
    public interface TypingActivityListener {
        /**
         * Typists transitioned from inactive to active.
         *
         * @param typingIndicator AtlasTypingIndicator notifying this listener.
         */
        void onTypingActive(AtlasTypingIndicator typingIndicator);

        /**
         * Typists transitioned from active to inactive.
         *
         * @param typingIndicator AtlasTypingIndicator notifying this listener.
         */
        void onTypingInactive(AtlasTypingIndicator typingIndicator);
    }
}
