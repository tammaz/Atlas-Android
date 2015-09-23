package com.layer.atlas;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.layer.atlas.simple.transformations.CircleTransform;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class AtlasAvatar extends View {
    private static final String TAG = AtlasAvatar.class.getSimpleName();

    private final static CircleTransform SINGLE_TRANSFORM = new CircleTransform(TAG + ".single");
    private final static CircleTransform MULTI_TRANSFORM = new CircleTransform(TAG + ".multi");

    private static final Paint PAINT_TRANSPARENT = new Paint();
    private static final Paint PAINT_BITMAP = new Paint();
    private static final Paint PAINT_INITIALS = new Paint();
    private static final Paint PAINT_BORDER = new Paint();
    private static final Paint PAINT_BACKGROUND = new Paint();

    // TODO: make these styleable
    private static final float BORDER_SIZE_DP = 1f;
    private static final float SINGLE_TEXT_SIZE_DP = 16f;
    private static final float MULTI_FRACTION = 26f / 40f;

    static {
        PAINT_TRANSPARENT.setARGB(0, 255, 255, 255);
        PAINT_TRANSPARENT.setAntiAlias(true);

        PAINT_BITMAP.setARGB(255, 255, 255, 255);
        PAINT_BITMAP.setAntiAlias(true);

        PAINT_INITIALS.setARGB(255, 0, 0, 0);
        PAINT_INITIALS.setAntiAlias(true);
        PAINT_INITIALS.setSubpixelText(true);

        PAINT_BORDER.setARGB(255, 255, 255, 255);
        PAINT_BORDER.setAntiAlias(true);

        PAINT_BACKGROUND.setARGB(255, 235, 235, 235);
        PAINT_BACKGROUND.setAntiAlias(true);
    }

    private ParticipantProvider mParticipantProvider;

    // Initials and Picasso image targets by user ID
    private final Map<String, ImageTarget> mImageTargets = new HashMap<String, ImageTarget>();
    private final Map<String, String> mInitials = new HashMap<String, String>();

    // Sizing set in setSize() and used in onDraw()
    private float mOuterRadius;
    private float mInnerRadius;
    private float mCenterX;
    private float mCenterY;
    private float mDeltaX;
    private float mDeltaY;

    private Rect mRect = new Rect();
    private RectF mInnerRect = new RectF();


    public AtlasAvatar(Context context) {
        super(context);
    }

    public AtlasAvatar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AtlasAvatar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AtlasAvatar init(ParticipantProvider participantProvider) {
        mParticipantProvider = participantProvider;
        return this;
    }

    public AtlasAvatar setParticipants(String... participantIds) {
        LinkedHashSet<String> hashSet = new LinkedHashSet<String>();
        for (String participantId : participantIds) {
            hashSet.add(participantId);
        }
        return setParticipants(hashSet);
    }

    public AtlasAvatar setParticipants(Set<String> participantIds) {
        List<String> addParticipants = new ArrayList<String>(participantIds.size());
        List<String> removeParticipants = new ArrayList<String>(mImageTargets.size());
        for (String participantId : participantIds) {
            if (!mImageTargets.containsKey(participantId)) addParticipants.add(participantId);
        }
        for (String participantId : mImageTargets.keySet()) {
            if (!participantIds.contains(participantId)) removeParticipants.add(participantId);
        }
        if (addParticipants.isEmpty() && removeParticipants.isEmpty()) return this;

        List<ImageTarget> toLoad = new ArrayList<ImageTarget>(participantIds.size());
        int recycled = 0;
        for (String participantId : addParticipants) {
            Participant participant = mParticipantProvider.getParticipant(participantId);
            if (participant == null) {
                mInitials.remove(participantId);
                mImageTargets.remove(participantId);
                continue;
            }
            mInitials.put(participantId, initials(participant.getName()));
            if (recycled < removeParticipants.size()) {
                // Recycle an ImageTarget
                ImageTarget recycledTarget = mImageTargets.remove(removeParticipants.get(recycled++));
                mImageTargets.put(participantId, recycledTarget);
                recycledTarget.setUrl(participant.getAvatarUrl());
                toLoad.add(recycledTarget);
            } else {
                // Add a new ImageTarget
                ImageTarget newTarget = new ImageTarget(this);
                mImageTargets.put(participantId, newTarget);
                newTarget.setUrl(participant.getAvatarUrl());
                toLoad.add(newTarget);
            }
        }

        while (recycled < removeParticipants.size()) {
            String participantId = removeParticipants.get(recycled++);
            mInitials.remove(participantId);
            mImageTargets.remove(participantId);
        }

        // Set sizing
        if (!setSize()) {
            throw new IllegalStateException("Could not set avatar size; No layout params?");
        }

        // Fetch images
        Picasso picasso = Picasso.with(getContext());
        int size = (int) (mInnerRadius * 2f);
        for (ImageTarget imageTarget : toLoad) {
            picasso.load(imageTarget.getUrl()).centerCrop().resize(size, size)
                    .transform(isMultiCluster() ? MULTI_TRANSFORM : SINGLE_TRANSFORM).into(imageTarget);
        }

        // Redraw
        invalidate();
        return this;
    }

    private boolean isMultiCluster() {
        return mInitials != null && mInitials.size() > 1;
    }
    
    private boolean setSize() {
        int avatarCount = mInitials.size();
        if (avatarCount == 0) return false;

        ViewGroup.LayoutParams params = getLayoutParams();
        int drawableWidth = params.width - (getPaddingLeft() + getPaddingRight());
        int drawableHeight = params.height - (getPaddingTop() + getPaddingBottom());
        float dimension = Math.min(drawableWidth, drawableHeight);
        float density = getContext().getResources().getDisplayMetrics().density;
        float fraction = (avatarCount == 1) ? 1 : MULTI_FRACTION;

        mOuterRadius = fraction * (dimension / 2f);
        mInnerRadius = mOuterRadius - (density * BORDER_SIZE_DP);
        mCenterX = getPaddingLeft() + mOuterRadius;
        mCenterY = getPaddingTop() + mOuterRadius;
        PAINT_INITIALS.setTextSize(fraction * density * SINGLE_TEXT_SIZE_DP);

        float outerMultiSize = fraction * dimension;
        mDeltaX = (drawableWidth - outerMultiSize) / (avatarCount - 1);
        mDeltaY = (drawableHeight - outerMultiSize) / (avatarCount - 1);

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Clear canvas
        int avatarCount = mInitials.size();

        canvas.drawRect(0f, 0f, canvas.getWidth(), canvas.getHeight(), PAINT_TRANSPARENT);
        if (avatarCount == 0) return;

        // Draw avatar cluster
        float cx = mCenterX;
        float cy = mCenterY;
        mInnerRect.set(cx - mInnerRadius, cy - mInnerRadius, cx + mInnerRadius, cy + mInnerRadius);
        for (Map.Entry<String, String> entry : mInitials.entrySet()) {
            // Border and background
            canvas.drawCircle(cx, cy, mOuterRadius, PAINT_BORDER);

            // Initials or bitmap
            ImageTarget imageTarget = mImageTargets.get(entry.getKey());
            Bitmap bitmap = (imageTarget == null) ? null : imageTarget.getBitmap();
            if (bitmap == null) {
                String initials = entry.getValue();
                PAINT_INITIALS.getTextBounds(initials, 0, initials.length(), mRect);
                canvas.drawCircle(cx, cy, mInnerRadius, PAINT_BACKGROUND);
                canvas.drawText(initials, cx - mRect.centerX(), cy - mRect.centerY() - 1f, PAINT_INITIALS);
            } else {
                canvas.drawBitmap(bitmap, mInnerRect.left, mInnerRect.top, PAINT_BITMAP);
            }

            // Translate for next avatar
            cx += mDeltaX;
            cy += mDeltaY;
            mInnerRect.offset(mDeltaX, mDeltaY);
        }
    }

    public static String initials(String s) {
        return ("" + s.charAt(0)).toUpperCase();
    }

    private static class ImageTarget implements Target {
        private final static AtomicLong sCounter = new AtomicLong(0);
        private final long mId;
        private final AtlasAvatar mCluster;
        private String mUrl;
        private Bitmap mBitmap;

        public ImageTarget(AtlasAvatar cluster) {
            mId = sCounter.incrementAndGet();
            mCluster = cluster;
        }

        public void setUrl(String url) {
            mUrl = url;
        }

        public String getUrl() {
            return mUrl;
        }

        @Override
        public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
            mCluster.invalidate();
            mBitmap = bitmap;
        }

        @Override
        public void onBitmapFailed(Drawable errorDrawable) {
            mCluster.invalidate();
            mBitmap = null;
        }

        @Override
        public void onPrepareLoad(Drawable placeHolderDrawable) {
            mBitmap = null;
        }

        public Bitmap getBitmap() {
            return mBitmap;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ImageTarget target = (ImageTarget) o;
            return mId == target.mId;
        }

        @Override
        public int hashCode() {
            return (int) (mId ^ (mId >>> 32));
        }
    }
}
