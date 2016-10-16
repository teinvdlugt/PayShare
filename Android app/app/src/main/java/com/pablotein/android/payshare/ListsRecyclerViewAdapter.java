package com.pablotein.android.payshare;

import android.content.Context;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

class ListsRecyclerViewAdapter extends RecyclerView.Adapter<ListsRecyclerViewAdapter.ListRecyclerViewHolder> {
    private ArrayList<ListRecyclerItem> list = new ArrayList<>();
    private static final int TYPE_NORMAL = 1, TYPE_NONE = 2;
    private boolean noItems = true;
    private Context context;

    /*public ListRecyclerViewAdapter(ArrayList<ListRecyclerItem> list) {
        this.list = list;
    }*/

    void setLists(ArrayList<ListsRecyclerViewAdapter.ListRecyclerItem> lists) {
        for (int vez = 0; vez < lists.size(); vez++) {
            int position = containsId(lists.get(vez).id);
            if (position == -1) {
                list.add(lists.get(vez));
                notifyItemInserted(list.size() - 1);
            } else if (!list.get(position).equals(lists.get(vez))) {
                list.set(position, lists.get(vez));
                notifyItemChanged(position);
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

    private int containsId(String id) {
        for (int vez = 0; vez < list.size(); vez++) {
            if (list.get(vez).id.equals(id)) return vez;
        }
        return -1;
    }

    static class ListRecyclerItem {
        String name, id;

        ListRecyclerItem(String name, String id) {
            this.name = name;
            this.id = id;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ListRecyclerItem) {
                ListsRecyclerViewAdapter.ListRecyclerItem item = (ListRecyclerItem) obj;
                return (item.name.equals(this.name) && item.id.equals(this.id));
            } else {
                return super.equals(obj);
            }
        }
    }

    class ListRecyclerViewHolder extends RecyclerView.ViewHolder {
        TextView nameTextView;

        ListRecyclerViewHolder(View view) {
            super(view);
            nameTextView = (TextView) view.findViewById(R.id.nameTextView);
        }
    }

    ListsRecyclerViewAdapter(Context context) {
        this.context = context;
    }

    @Override
    public void onBindViewHolder(final ListRecyclerViewHolder holder, int position) {
        if (!noItems) {
            holder.nameTextView.setText(list.get(position).name);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(context, ListActivity.class);
                    intent.putExtra(ConnectionService.EXTRA_LIST_ID, list.get(holder.getAdapterPosition()).id);
                    intent.putExtra(ConnectionService.EXTRA_NAME, list.get(holder.getAdapterPosition()).name);
                    context.startActivity(intent);
                }
            });
        }
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
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.lists_recycler_item, parent, false);
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
        return new ListRecyclerViewHolder(LayoutInflater.from(parent.getContext()).inflate(viewType == TYPE_NORMAL ? R.layout.lists_recycler_item : R.layout.list_recycler_none_message, parent, false));
    }

    @Override
    public int getItemViewType(int position) {
        return noItems ? TYPE_NONE : TYPE_NORMAL;
    }
}
