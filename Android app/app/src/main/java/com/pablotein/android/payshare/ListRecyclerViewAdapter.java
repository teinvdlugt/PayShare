package com.pablotein.android.payshare;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

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

        ListRecyclerItem(String name, String id, String byID, float price, byte amount) {
            this.name = name;
            this.byID = byID;
            this.id = id;
            this.price = price;
            this.amount = amount;
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
        String currency, name, parsedCurrency;
        boolean isPublic;

        ListSettings(String name, boolean isPublic, String currency) {
            this.currency = currency;
            this.name = name;
            this.isPublic = isPublic;
        }
    }

    class ListRecyclerViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView, priceTextView;

        ListRecyclerViewHolder(View view) {
            super(view);
            nameTextView = (TextView) view.findViewById(R.id.nameTextView);
            priceTextView = (TextView) view.findViewById(R.id.priceTextView);
        }
    }

    ListRecyclerViewAdapter(Context context) {
        this.context = context;
    }

    @Override
    public void onBindViewHolder(final ListRecyclerViewHolder holder, int position) {
        if (!noItems) {
            holder.nameTextView.setText(list.get(position).name);
            holder.priceTextView.setText(String.valueOf(list.get(position).price) + " " + settings.parsedCurrency);//TODO update currecy/price order depending on localization
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

    public void setSettings(ListSettings settings) {
        this.settings = settings;
        switch (settings.currency) {
            case "EUR":
                this.settings.parsedCurrency = "€";
                break;
            case "USD":
                this.settings.parsedCurrency = "$";
                break;
            //TODO add currencies
        }
    }
}
