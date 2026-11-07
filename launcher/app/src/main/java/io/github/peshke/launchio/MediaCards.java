package io.github.peshke.launchio;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** The cards of one row under the dock (Continue watching). Home does the loading, opening and navigation. */
final class MediaCards extends RecyclerView.Adapter<MediaCards.Holder> {

    interface Listener {
        void onCardFocused(int row, int position, View card);

        /** Up/Down, and Left/Right at the row's ends: true when Home handled it. */
        boolean onCardKey(int row, int position, int keyCode);

        void onCardClick(Featured.Item item);

        /** Hold OK on a card. */
        void onCardLongPress(Featured.Item item, View card);

        void loadArt(String url, ImageView into);
    }

    private final int row;
    private final Listener listener;
    private final List<Featured.Item> items = new ArrayList<>();
    /** Tall poster cards instead of wide ones, and the poster link for this shape (null for the standard art). */
    private final boolean tall;
    private final String link;

    MediaCards(int row, Listener listener, boolean tall, String link) {
        this.row = row;
        this.listener = listener;
        this.tall = tall;
        this.link = link;
    }

    void setItems(List<Featured.Item> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @Override public int getItemCount() { return items.size(); }

    Featured.Item item(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_media_card, parent, false);
        if (tall) {
            // A 2:3 poster card, 150 x 225 dp, instead of the 240 x 135 dp wide one.
            float d = parent.getResources().getDisplayMetrics().density;
            v.getLayoutParams().width = Math.round(150 * d);
            ViewGroup.LayoutParams card = v.findViewById(R.id.mediaCard).getLayoutParams();
            card.width = Math.round(150 * d);
            card.height = Math.round(225 * d);
        }
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Featured.Item it = items.get(position);
        h.title.setText(it.title);
        h.line.setText(it.facts);
        h.line.setVisibility(it.facts == null || it.facts.isEmpty() ? View.GONE : View.VISIBLE);
        h.art.setImageDrawable(null);
        listener.loadArt(Featured.cardArt(it, tall, link), h.art);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) h.progress.getLayoutParams();
        lp.width = Math.round(h.card.getLayoutParams().width * it.progress);
        h.progress.setLayoutParams(lp);
        h.progress.setVisibility(it.progress > 0 ? View.VISIBLE : View.GONE);
        h.card.setOnClickListener(v -> listener.onCardClick(it));
        h.card.setOnLongClickListener(v -> {
            listener.onCardLongPress(it, v);
            return true;
        });
        h.card.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(150).start();
            if (has) listener.onCardFocused(row, h.getBindingAdapterPosition(), v);
        });
        h.card.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            int at = h.getBindingAdapterPosition();
            boolean edge = (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && at == 0)
                    || (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && at == items.size() - 1);
            boolean vertical = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
            return (edge || vertical) && listener.onCardKey(row, at, keyCode);
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card, progress;
        final ImageView art;
        final TextView title, line;

        Holder(View v) {
            super(v);
            card = v.findViewById(R.id.mediaCard);
            card.setClipToOutline(true);   // the picture takes the card's rounded corners
            art = v.findViewById(R.id.mediaArt);
            progress = v.findViewById(R.id.mediaProgress);
            title = v.findViewById(R.id.mediaTitle);
            line = v.findViewById(R.id.mediaLine);
        }
    }
}
