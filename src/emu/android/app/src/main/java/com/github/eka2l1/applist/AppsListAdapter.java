/*
 * Copyright (c) 2020 EKA2L1 Team
 *
 * This file is part of EKA2L1 project.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.github.eka2l1.applist;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.eka2l1.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


public class AppsListAdapter extends RecyclerView.Adapter<AppItemViewHolder> implements Filterable {

    private List<AppItem> list;
    private List<AppItem> filteredList;
    private final LayoutInflater layoutInflater;
    private final AppFilter appFilter;
    public static final int VIEW_MODE_LIST = 0;
    public static final int VIEW_MODE_LIST_2 = 1;
    public static final int VIEW_MODE_LIST_3 = 2;
    public static final int VIEW_MODE_GRID = 3;

    private int viewMode = VIEW_MODE_LIST;
    private String lastConstraint = "";

    public AppsListAdapter(Context context) {
        this.list = new ArrayList<>();
        this.filteredList = new ArrayList<>();
        this.layoutInflater = LayoutInflater.from(context);
        this.appFilter = new AppFilter();
    }

    @NonNull
    @Override
    public AppItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = layoutInflater.inflate(viewType, parent, false);
        return new AppItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppItemViewHolder holder, int position) {
        AppItem item = filteredList.get(position);
        holder.setItem(item, position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemCount() {
        return filteredList.size();
    }

    @Override
    public int getItemViewType(final int position) {
        switch (viewMode) {
            case VIEW_MODE_LIST_2:
            case VIEW_MODE_LIST_3:
                return R.layout.listitem_detail_icon_multicolumn;
            case VIEW_MODE_GRID:
                return R.layout.listitem_detail_icon_grid;
            case VIEW_MODE_LIST:
            default:
                return R.layout.listitem_detail_icon;
        }
    }

    public void setItems(List<AppItem> items) {
        list = items;
        refreshItems();
    }

    public void refreshItems() {
        Collections.sort(list, (o1, o2) -> {
            int compareResult = o1.getTitle().compareToIgnoreCase(o2.getTitle());
            if (compareResult != 0) {
                return compareResult;
            }
            return o1.getTitle().compareTo(o2.getTitle());
        });

        if (lastConstraint == null || lastConstraint.isEmpty()) {
            filteredList = list;
        } else {
            ArrayList<AppItem> resultList = new ArrayList<>();
            for (AppItem item : list) {
                if (item.getTitle().toLowerCase(Locale.ROOT).contains(lastConstraint)) {
                    resultList.add(item);
                }
            }
            filteredList = resultList;
        }

        notifyDataSetChanged();
    }

    public void setDisplay(boolean isGridDisplay) {
        setDisplayMode(isGridDisplay ? VIEW_MODE_GRID : VIEW_MODE_LIST);
    }

    public void setDisplayMode(int mode) {
        viewMode = mode;

        if (list != null) {
            notifyDataSetChanged();
        }
    }

    public AppItem getItem(int position) {
        return filteredList.get(position);
    }

    @Override
    public Filter getFilter() {
        return appFilter;
    }

    private class AppFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            FilterResults results = new FilterResults();
            lastConstraint = constraint == null ? "" : constraint.toString().toLowerCase(Locale.ROOT);
            if (lastConstraint.equals("")) {
                results.count = list.size();
                results.values = list;
            } else {
                ArrayList<AppItem> resultList = new ArrayList<>();
                for (AppItem item : list) {
                    if (item.getTitle().toLowerCase(Locale.ROOT).contains(lastConstraint)) {
                        resultList.add(item);
                    }
                }
                results.count = resultList.size();
                results.values = resultList;
            }
            return results;
        }


        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            if (results.values != null) {
                filteredList = (List<AppItem>) results.values;
            }
            notifyDataSetChanged();
        }
    }
}
