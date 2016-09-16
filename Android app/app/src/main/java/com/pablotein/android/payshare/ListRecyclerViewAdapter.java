package com.pablotein.android.payshare;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

public class ListRecyclerViewAdapter extends RecyclerView.Adapter<ListRecyclerViewAdapter.ListRecyclerViewHolder> {
    private ArrayList<ListRecyclerItem> list = new ArrayList<>();

    /*public ListRecyclerViewAdapter(ArrayList<ListRecyclerItem> list) {
        this.list = list;
    }*/
    public void addElement(ListRecyclerItem item) {
        list.add(item);
        notifyItemInserted(list.size() - 1);
    }

    public static class ListRecyclerItem {
        String name;

        public ListRecyclerItem(String name) {
            this.name = name;
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
        holder.nameTextView.setText(list.get(position).name);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    @Override
    public ListRecyclerViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ListRecyclerViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.list_recycler_item, parent, false));
    }
}
