package com.layer.atlas.simple.cells;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.layer.atlas.AtlasCellFactory;
import com.layer.atlas.R;
import com.layer.sdk.messaging.Message;

public class SimpleTextCellFactory implements AtlasCellFactory<SimpleTextCellFactory.TextCellHolder> {
    @Override
    public boolean isBindable(Message message) {
        return message.getMessageParts().get(0).getMimeType().startsWith("text/");
    }

    @Override
    public TextCellHolder createCellHolder(ViewGroup cellView, boolean isMe, LayoutInflater layoutInflater) {
        Context context = cellView.getContext();

        View v = layoutInflater.inflate(R.layout.simple_cell_text, cellView, true);
        //int padding = dpPixels(context, 8f);
        //v.setPadding(padding, padding, padding, padding);
        v.setBackgroundResource(isMe ? R.drawable.atlas_message_item_cell_me : R.drawable.atlas_message_item_cell_them);

        TextView t = (TextView) v.findViewById(R.id.cell_text);
        t.setTextColor(context.getResources().getColor(isMe ? R.color.atlas_text_white : R.color.atlas_text_black));
        return new TextCellHolder(v);
    }

    @Override
    public void bindCellHolder(TextCellHolder cellHolder, Message message, boolean isMe, int position, int maxWidth) {
        String text = new String(message.getMessageParts().get(0).getData());
        cellHolder.mTextView.setText(text);
    }

    private static int dpPixels(Context context, float dps) {
        return (int) (dps * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    static class TextCellHolder extends AtlasCellFactory.CellHolder {
        TextView mTextView;

        public TextCellHolder(View view) {
            mTextView = (TextView) view.findViewById(R.id.cell_text);
        }
    }
}
