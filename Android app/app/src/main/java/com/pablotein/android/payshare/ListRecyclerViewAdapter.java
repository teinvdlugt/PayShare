package com.pablotein.android.payshare;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

public class ListRecyclerViewAdapter extends RecyclerView.Adapter<ListRecyclerViewAdapter.ListRecyclerViewHolder> {
    private ArrayList<ListRecyclerItem> list = new ArrayList<>();
    private static final int TYPE_NORMAL = 1, TYPE_NONE = 2;
    private boolean noItems = true;

    /*public ListRecyclerViewAdapter(ArrayList<ListRecyclerItem> list) {
        this.list = list;
    }*/

    public void setLists(ArrayList<ListRecyclerViewAdapter.ListRecyclerItem> lists) {
        for (int vez = 0; vez < lists.size(); vez++) {
            if (!containsId(lists.get(vez).id)) {
                list.add(lists.get(vez));
                notifyItemInserted(list.size() - 1);
            }
        }
        for (int vez = 0; vez < list.size(); vez++) {
            boolean contains = false;
            for (int vez1 = 0; vez1 < lists.size(); vez1++) {
                if (list.get(vez).id.equals(lists.get(vez1).id)) {
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

    /*public void addElement(ListRecyclerItem item) {
        list.add(item);
        notifyItemInserted(list.size() - 1);
        if (noItems) {
            noItems = false;
            notifyItemRemoved(0);
        }
    }*/

    public boolean containsId(String id) {
        for (int vez = 0; vez < list.size(); vez++) {
            if (list.get(vez).id.equals(id)) return true;
        }
        return false;
    }

    public static class ListRecyclerItem {
        String name, id;

        public ListRecyclerItem(String name, String id) {
            this.name = name;
            this.id = id;
        }
    }

    public class ListRecyclerViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;

        public ListRecyclerViewHolder(View view) {
            super(view);
            nameTextView = (TextView) view.findViewById(R.id.nameTextView);
        }
    }

    @Override
    public void onBindViewHolder(ListRecyclerViewHolder holder, int position) {
        if (holder.nameTextView != null) holder.nameTextView.setText(list.get(position).name);
    }

    @Override
    public int getItemCount() {
        return noItems ? 1 : list.size();
    }

    @Override
    public ListRecyclerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        /*View view;
        switch (viewType) {
            case TYPE_NORMAL:
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_recycler_item, parent, false);
                break;
            case TYPE_NONE:
            default:
                RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = params.bottomMargin = params.leftMargin = params.rightMargin = ;
                view = new TextView(parent.getContext());
                view.setLayoutParams(params);
                ((TextView) view).setText("No items found");
                ((TextView) view).setGravity(Gravity.CENTER);
                break;
        }
        return new ListRecyclerViewHolder(view);*/
        return new ListRecyclerViewHolder(LayoutInflater.from(parent.getContext()).inflate(viewType == TYPE_NORMAL ? R.layout.list_recycler_item : R.layout.list_recycler_none_message, parent, false));
    }

    @Override
    public int getItemViewType(int position) {
        return noItems ? TYPE_NONE : TYPE_NORMAL;
    }
}
