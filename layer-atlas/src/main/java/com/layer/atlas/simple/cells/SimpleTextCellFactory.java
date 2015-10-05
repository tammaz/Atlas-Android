package com.layer.atlas.simple.cells;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.layer.atlas.AtlasCellFactory;
import com.layer.atlas.R;
import com.layer.sdk.messaging.Message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleTextCellFactory implements AtlasCellFactory<SimpleTextCellFactory.TextCellHolder> {
    private Map<Uri, String> mContentCache = new ConcurrentHashMap<Uri, String>();

    @Override
    public boolean isBindable(Message message) {
        return message.getMessageParts().get(0).getMimeType().startsWith("text/");
    }

    @Override
    public TextCellHolder createCellHolder(ViewGroup cellView, boolean isMe, LayoutInflater layoutInflater) {
        Context context = cellView.getContext();

        View v = layoutInflater.inflate(R.layout.simple_cell_text, cellView, true);
        v.setBackgroundResource(isMe ? R.drawable.atlas_message_item_cell_me : R.drawable.atlas_message_item_cell_them);

        TextView t = (TextView) v.findViewById(R.id.cell_text);
        t.setTextColor(context.getResources().getColor(isMe ? R.color.atlas_text_white : R.color.atlas_text_black));
        return new TextCellHolder(v);
    }

    @Override
    public void bindCellHolder(TextCellHolder cellHolder, Message message, boolean isMe, int position, int maxWidth) {
        onCache(message);
        cellHolder.mTextView.setText(mContentCache.get(message.getId()));
    }

    @Override
    public void onCache(Message message) {
        if (mContentCache.containsKey(message.getId())) return;
        mContentCache.put(message.getId(), new String(message.getMessageParts().get(0).getData()));
    }

    static class TextCellHolder extends AtlasCellFactory.CellHolder {
        TextView mTextView;

        public TextCellHolder(View view) {
            mTextView = (TextView) view.findViewById(R.id.cell_text);
        }
    }
}
