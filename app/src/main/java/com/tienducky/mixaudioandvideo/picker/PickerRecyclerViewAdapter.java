package com.tienducky.mixaudioandvideo.picker;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.tienducky.mixaudioandvideo.databinding.PickerRecyclerViewItemLayoutBinding;
import com.tienducky.mixaudioandvideo.models.MediaItem;

import java.util.ArrayList;

public class PickerRecyclerViewAdapter extends RecyclerView.Adapter<PickerRecyclerViewAdapter.ViewHolder> {

    private ArrayList<MediaItem> mMediaItems;
    private PickerItemListener mItemListener;

    public PickerRecyclerViewAdapter(ArrayList<MediaItem> mediaItems, PickerItemListener listener){
        this.mMediaItems = mediaItems;
        this.mItemListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        PickerRecyclerViewItemLayoutBinding mViewBinding = PickerRecyclerViewItemLayoutBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false);

        return new ViewHolder(mViewBinding, mItemListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.setData(mMediaItems.get(position));
    }

    @Override
    public int getItemCount() {
        return mMediaItems.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder{
        private PickerRecyclerViewItemLayoutBinding mBinding;
        private PickerItemListener mListener;

        public ViewHolder(PickerRecyclerViewItemLayoutBinding binding, PickerItemListener listener) {
            super(binding.getRoot());
            mBinding = binding;
            mListener = listener;
        }

        public void setData(MediaItem mediaItem) {
            Glide.with(mBinding.getRoot())
                    .load(mediaItem.getFilePath())
                    .into(mBinding.fileThumbnail);
            mBinding.fileName.setText(mediaItem.getFileName());
            mBinding.getRoot().setOnClickListener(v -> mListener.onMediaItemSelected(mediaItem));
        }
    }

}
