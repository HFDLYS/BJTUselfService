package com.hfdlys.bjtuselfservice.fragment.evaluation.building;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hfdlys.bjtuselfservice.R;

import java.util.List;

public class BuildingAdapter extends RecyclerView.Adapter<BuildingAdapter.ViewHolder> {
    private List<String> buildingList;
    private OnBuildingClickListener listener;

    public BuildingAdapter(List<String> buildingList, OnBuildingClickListener listener) {
        this.buildingList = buildingList;
        this.listener = listener;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView buildingName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            buildingName = itemView.findViewById(R.id.building_name);
        }
    }

    @NonNull
    @Override
    public BuildingAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.building_item_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BuildingAdapter.ViewHolder holder, int position) {
        String building = buildingList.get(position);
        holder.buildingName.setText(building);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onBuildingClicked(building);
            }
        });
    }

    @Override
    public int getItemCount() {
        return buildingList.size();
    }

    public interface OnBuildingClickListener {
        void onBuildingClicked(String buildingName);
    }
}
