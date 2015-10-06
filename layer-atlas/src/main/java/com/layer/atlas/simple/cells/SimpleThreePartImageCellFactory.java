package com.layer.atlas.simple.cells;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.layer.atlas.AtlasCellFactory;
import com.layer.atlas.R;
import com.layer.atlas.simple.transformations.RoundedTransform;
import com.layer.sdk.messaging.Message;
import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Transformation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleThreePartImageCellFactory implements AtlasCellFactory<SimpleThreePartImageCellFactory.ImageCellHolder> {
    private static final String TAG = SimpleThreePartImageCellFactory.class.getSimpleName();

    private static final int PLACEHOLDER_RES_ID = R.drawable.atlas_message_item_cell_placeholder;

    private final Picasso mPicasso;
    private final Transformation mTransform;
    private final Map<String, ThreePartImageUtils.ThreePartImageInfo> mInfoCache = new ConcurrentHashMap<String, ThreePartImageUtils.ThreePartImageInfo>();

    public SimpleThreePartImageCellFactory(Context context, Picasso picasso) {
        mPicasso = picasso;
        float radius = context.getResources().getDimension(com.layer.atlas.R.dimen.atlas_message_item_cell_radius);
        mTransform = new RoundedTransform(radius);
    }

    @Override
    public boolean isBindable(Message message) {
        return ThreePartImageUtils.isThreePartImage(message);
    }

    @Override
    public void onCache(Message message) {
        String id = message.getId().toString();
        if (mInfoCache.containsKey(id)) return;
        mInfoCache.put(id, ThreePartImageUtils.getInfo(message));
    }

    @Override
    public ImageCellHolder createCellHolder(ViewGroup cellView, boolean isMe, LayoutInflater layoutInflater) {
        return new ImageCellHolder(layoutInflater.inflate(R.layout.simple_cell_image, cellView, true));
    }

    @Override
    public void bindCellHolder(final ImageCellHolder cellHolder, final Message message, CellHolderSpecs specs) {
        // Parse into part
        onCache(message);
        ThreePartImageUtils.ThreePartImageInfo info = mInfoCache.get(message.getId().toString());

        // Get rotation and scaled dimensions
        final float rotation;
        int width;
        int height;
        switch (info.orientation) {
            case 0:
                // 0 degrees
                rotation = 0f;
                width = info.width;
                height = info.height;
                break;
            case 1:
                // 180 degrees
                rotation = 180f;
                width = info.width;
                height = info.height;
                break;
            case 2:
                // 90 degrees right
                rotation = -90f;
                width = info.height;
                height = info.width;
                break;
            default:
                // 90 degrees left
                rotation = 90f;
                width = info.height;
                height = info.width;
                break;
        }
        final int scaledWidth;
        final int scaledHeight;
        if (width <= specs.maxWidth) {
            scaledWidth = width;
            scaledHeight = height;
        } else {
            scaledWidth = specs.maxWidth;
            scaledHeight = (int) Math.round((double) specs.maxWidth * (double) height / (double) width);
        }

        cellHolder.mImageView.setImageBitmap(null);
        cellHolder.mImageView.setLayoutParams(new FrameLayout.LayoutParams(scaledWidth, scaledHeight));
        cellHolder.mImageView.setBackgroundResource(PLACEHOLDER_RES_ID);

        if (ThreePartImageUtils.isFullImageReady(message)) {
            // Full image is ready, load it directly.
            mPicasso.load(ThreePartImageUtils.getFullImageId(message))
                    .tag(ThreePartImageUtils.TAG).noPlaceholder().noFade()
                    .centerInside().resize(scaledWidth, scaledHeight).rotate(rotation)
                    .transform(mTransform).into(cellHolder.mImageView, new Callback() {
                @Override
                public void onSuccess() {
                    cellHolder.mImageView.setBackgroundDrawable(null);
                }

                @Override
                public void onError() {
                    cellHolder.mImageView.setBackgroundResource(PLACEHOLDER_RES_ID);
                }
            });
        } else {
            // Full image is not ready, so start by loading the preview...
            mPicasso.load(ThreePartImageUtils.getPreviewImageId(message))
                    .tag(ThreePartImageUtils.TAG).noPlaceholder().noFade()
                    .centerInside().resize(scaledWidth, scaledHeight).rotate(rotation)
                    .transform(mTransform).into(cellHolder.mImageView, new Callback() {
                @Override
                public void onSuccess() {
                    // ...Then load in the full image.
                    cellHolder.mImageView.setBackgroundDrawable(null);
                    mPicasso.load(ThreePartImageUtils.getFullImageId(message))
                            .tag(ThreePartImageUtils.TAG).noPlaceholder().noFade()
                            .centerInside().resize(scaledWidth, scaledHeight).rotate(rotation)
                            .transform(mTransform).into(cellHolder.mImageView, new Callback() {
                        @Override
                        public void onSuccess() {
                            cellHolder.mImageView.setBackgroundDrawable(null);
                        }

                        @Override
                        public void onError() {
                            cellHolder.mImageView.setBackgroundResource(PLACEHOLDER_RES_ID);
                        }
                    });
                }

                @Override
                public void onError() {
                    cellHolder.mImageView.setBackgroundResource(PLACEHOLDER_RES_ID);
                }
            });
        }
    }

    static class ImageCellHolder extends AtlasCellFactory.CellHolder {
        ImageView mImageView;

        public ImageCellHolder(View view) {
            mImageView = (ImageView) view.findViewById(R.id.cell_image);
        }
    }

}
