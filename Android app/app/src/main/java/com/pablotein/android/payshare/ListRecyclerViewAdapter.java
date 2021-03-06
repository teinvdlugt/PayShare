package com.pablotein.android.payshare;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Date;

class ListRecyclerViewAdapter extends RecyclerView.Adapter<ListRecyclerViewAdapter.ListRecyclerViewHolder> {
    private ArrayList<ListRecyclerItem> list = new ArrayList<>();
    private static final int TYPE_NORMAL = 1, TYPE_NONE = 2;
    private boolean noItems = true;
    private Context context;
    private ListSettings settings;

    void setItems(ArrayList<ListRecyclerViewAdapter.ListRecyclerItem> items) {
        for (int vez = 0; vez < items.size(); vez++) {
            int position = containsId(items.get(vez).id);
            if (position == -1) {
                list.add(items.get(vez));
                notifyItemInserted(list.size() - 1);
            } else if (!list.get(position).equals(items.get(vez))) {
                list.set(position, items.get(vez));
                notifyItemChanged(position);
            }
        }
        for (int vez = 0; vez < list.size(); vez++) {
            boolean contains = false;
            for (int vez1 = 0; vez1 < items.size(); vez1++) {
                if (list.get(vez).id.equals(items.get(vez1).id)) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                list.remove(vez);
                notifyItemRemoved(vez);
                vez--;
            }
        }
        if (list.size() > 0) {
            if (noItems) {
                noItems = false;
                notifyItemRemoved(0);
            }
        } else {
            if (!noItems) {
                noItems = true;
                notifyItemInserted(0);
            }
        }
    }

    private int containsId(String id) {
        for (int vez = 0; vez < list.size(); vez++) {
            if (list.get(vez).id.equals(id)) return vez;
        }
        return -1;
    }

    static class ListRecyclerItem {
        String name, byID, id;
        float price;
        byte amount;
        Date createdDate;

        ListRecyclerItem(String name, String id, String byID, float price, byte amount, Date createdDate) {
            this.name = name;
            this.byID = byID;
            this.id = id;
            this.price = price;
            this.amount = amount;
            this.createdDate = createdDate;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ListRecyclerItem) {
                ListRecyclerItem item = (ListRecyclerItem) obj;
                return (item.name.equals(this.name) && item.byID.equals(this.byID) && item.id.equals(this.id) && item.price == this.price && item.amount == this.amount);
            } else {
                return super.equals(obj);
            }
        }
    }

    static class ListSettings {
        String currency, name;
        NumberFormat priceFormat;
        boolean isPublic;

        ListSettings(String name, boolean isPublic, String currency) {
            this.currency = currency;
            this.name = name;
            this.isPublic = isPublic;
            priceFormat = NumberFormat.getCurrencyInstance();
            priceFormat.setCurrency(Currency.getInstance(currency));
        }
    }

    class ListRecyclerViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView, priceTextView, addedInfoTextView;

        ListRecyclerViewHolder(View view) {
            super(view);
            nameTextView = (TextView) view.findViewById(R.id.nameTextView);
            priceTextView = (TextView) view.findViewById(R.id.priceTextView);
            addedInfoTextView = (TextView) view.findViewById(R.id.addedInfoTextView);
        }
    }

    ListRecyclerViewAdapter(Context context) {
        this.context = context;
    }

    @Override
    public void onBindViewHolder(final ListRecyclerViewHolder holder, int position) {
        if (!noItems) {
            holder.nameTextView.setText(list.get(position).name);
            holder.priceTextView.setText(settings.priceFormat.format(list.get(position).price));
            holder.addedInfoTextView.setText(context.getString(R.string.added_on_by,DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(list.get(position).createdDate),"you"));
        }
    }

    @Override
    public int getItemCount() {
        return noItems ? 1 : list.size();
    }

    @Override
    public ListRecyclerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ListRecyclerViewHolder(LayoutInflater.from(parent.getContext()).inflate(viewType == TYPE_NORMAL ? R.layout.list_recycler_item : R.layout.list_recycler_none_message, parent, false));
    }

    @Override
    public int getItemViewType(int position) {
        return noItems ? TYPE_NONE : TYPE_NORMAL;
    }

    void setSettings(ListSettings settings) {
        boolean update = this.settings != null && !settings.currency.equals(this.settings.currency);
        this.settings = settings;
        if (update) {
            notifyItemRangeChanged(0, list.size());
        }
    }
}
