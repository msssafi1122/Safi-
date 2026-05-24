package com.example;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.ViewHolder> {

    private final Context context;
    private final List<DatabaseHelper.NoteItem> notesList;
    private final OnNoteClickListener clickListener;

    public interface OnNoteClickListener {
        void onNoteClick(DatabaseHelper.NoteItem note);
    }

    public NoteAdapter(Context context, List<DatabaseHelper.NoteItem> notesList, OnNoteClickListener clickListener) {
        this.context = context;
        this.notesList = notesList;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_quick_note, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DatabaseHelper.NoteItem note = notesList.get(position);
        holder.tvTitle.setText(note.title == null || note.title.isEmpty() ? "Untitled" : note.title);
        holder.tvSnippet.setText(note.content == null || note.content.isEmpty() ? "No content" : note.content);

        if (note.isPinned) {
            holder.ivPin.setVisibility(View.VISIBLE);
        } else {
            holder.ivPin.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onNoteClick(note);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notesList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;
        TextView tvSnippet;
        ImageView ivPin;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNoteItemTitle);
            tvSnippet = itemView.findViewById(R.id.tvNoteItemSnippet);
            ivPin = itemView.findViewById(R.id.ivNoteItemPin);
        }
    }
}
